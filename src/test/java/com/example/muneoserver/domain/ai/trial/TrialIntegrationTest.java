package com.example.muneoserver.domain.ai.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 전용 로컬 Redis를 명시한 실행에서만 동작한다. 운영 Redis에는 연결하지 않는다.
@EnabledIfEnvironmentVariable(named = "TRIAL_TEST_REDIS_PORT", matches = "[0-9]+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=20MB", "spring.servlet.multipart.max-request-size=21MB"
})
class TrialIntegrationTest {
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> System.getenv("TRIAL_TEST_REDIS_PORT"));
    }
    @LocalServerPort int port;
    @Autowired StringRedisTemplate redis;
    @Autowired TrialStore store;
    @Autowired TrialRateLimiter limiter;
    @MockitoBean TrialEstimateTokens estimateTokens;
    @Autowired RestTemplate aiRestTemplate;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private MockRestServiceServer ai;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeEach
    void prepare() {
        ai = MockRestServiceServer.bindTo(aiRestTemplate).build();
        redis.delete("trial:{budget}:global");
    }

    @Test
    void guestAnalysisLoginOwnershipAndExpiryWorkThroughRealHttpAndRedis() throws Exception {
        ai.expect(requestTo("http://127.0.0.1:8000/risk-detector/analyze"))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess(TrialFixtures.ANALYSIS, MediaType.APPLICATION_JSON));
        String cookie = session("analysis");
        HttpResponse<String> preview = upload(cookie, "estimate.pdf", "%PDF-1.4 test");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(preview.body()).contains("폐기물 처리비").doesNotContain("전체 결과 전용", "private_note", "report", "입력 업체");
        assertThat(json.readTree(preview.body()).get("totalItems").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/trials/analysis", cookie).body()).isEqualTo(preview.body());
        assertThat(get("/api/v1/trials/analysis/result", cookie).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/risk-detector/analyze", "", "application/json", cookie).statusCode()).isEqualTo(401);

        String owner = cookie + "; " + login();
        HttpResponse<String> full = get("/api/v1/trials/analysis/result", owner);
        assertThat(full.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(full.body());
        assertThat(body.get("result")).isEqualTo(json.readTree(TrialFixtures.ANALYSIS));
        assertThat(body.get("input").get("company_name").asText()).isEqualTo("입력 업체");
        assertThat(get("/api/v1/trials/analysis/result", owner).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/trials/analysis/result", cookie + "; " + login()).statusCode()).isEqualTo(403);
        String key = TrialStore.key("analysis", cookie.substring("muneo_trial=".length()));
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1700L, 1800L);
        redis.expire(key, Duration.ZERO);
        assertThat(get("/api/v1/trials/analysis/result", owner).statusCode()).isEqualTo(410);
        ai.verify(); // 로그인 후 전체 조회는 AI를 다시 호출하지 않는다.
    }

    @Test
    void guestEstimateUsesSameAuthenticatedFullResult() throws Exception {
        String cookie = session("estimate");
        String token = cookie.substring("muneo_trial=".length());
        ai.expect(requestTo("http://127.0.0.1:8000/estimates/generate"))
                .andExpect(header("x-user-id", TrialEstimateTokens.guestId(token)))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess(TrialFixtures.ESTIMATE, MediaType.APPLICATION_JSON));
        HttpResponse<String> preview = post("/api/v1/trials/estimate", TrialFixtures.ESTIMATE_INPUT, "application/json", cookie);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("도배").doesNotContain("마루", "검색_쿼리", "총_견적_범위", "estimate_token");
        verify(estimateTokens).extend(eq(token), any());
        HttpResponse<String> full = get("/api/v1/trials/estimate/result", cookie + "; " + login());
        assertThat(full.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(full.body());
        assertThat(body.get("result")).isEqualTo(json.readTree(TrialFixtures.ESTIMATE));
        assertThat(body.get("input").get("평수").asInt()).isEqualTo(25);
        assertThat(body.get("input").has("pyeong")).isFalse();
        verify(estimateTokens).claim(eq(token), any(), any());
        ai.verify();
    }

    @Test
    void limitsAreAtomicAndRotatingCookieCannotBypassGlobalBudget() throws Exception {
        String token = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) calls.add(() -> limiter.reserve(token));
            int allowed = 0;
            for (var future : pool.invokeAll(calls)) if (future.get()) allowed++;
            assertThat(allowed).isEqualTo(3);
        }
        assertThat(redis.getExpire("trial:{budget}:session:" + token)).isBetween(1700L, 1800L);
        assertThat(post("/api/v1/trials/estimate", TrialFixtures.ESTIMATE_INPUT, "application/json", "muneo_trial=" + token).statusCode()).isEqualTo(429);
        redis.opsForValue().set("trial:{budget}:global", "60", Duration.ofHours(1));
        assertThat(limiter.reserve(UUID.randomUUID().toString())).isFalse();
        assertThat(get("/api/v1/trials/analysis", "").statusCode()).isEqualTo(204);
        ai.verify();
    }

    @Test
    void expiredWorkerCannotReplaceNewResultOrReleaseItsLock() throws Exception {
        String token = UUID.randomUUID().toString();
        String first = store.begin("analysis", token);
        assertThat(store.begin("analysis", token)).isNull();
        redis.expire(TrialStore.key("analysis", token) + ":lock", Duration.ZERO);
        String second = store.begin("analysis", token);
        store.finish("analysis", token, first);
        assertThat(store.begin("analysis", token)).isNull();
        assertThatThrownBy(() -> store.save("analysis", token, first, Map.of(), TrialFixtures.object(TrialFixtures.ANALYSIS)))
                .isInstanceOf(IllegalStateException.class);
        store.save("analysis", token, second, Map.of(), TrialFixtures.object(TrialFixtures.ANALYSIS));
        store.finish("analysis", token, second);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var claims = pool.invokeAll(List.<Callable<TrialStore.Claim>>of(
                    () -> store.claim("analysis", token, 101L), () -> store.claim("analysis", token, 102L)));
            assertThat(List.of(claims.get(0).get().forbidden(), claims.get(1).get().forbidden())).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void invalidInputAndMalformedAiResponseNeverBecomeSuccessfulPreview() throws Exception {
        String cookie = session("analysis");
        assertThat(upload(cookie, "estimate.txt", "text").statusCode()).isEqualTo(400);
        assertThat(upload(cookie, "estimate.pdf", "").statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/trials/estimate", "{}", "application/json", cookie).statusCode()).isEqualTo(400);
        ai.expect(requestTo("http://127.0.0.1:8000/risk-detector/analyze"))
                .andRespond(withSuccess("{\"detail\":\"invalid\"}", MediaType.APPLICATION_JSON));
        assertThat(upload(cookie, "estimate.pdf", "%PDF test").statusCode()).isEqualTo(502);
        assertThat(get("/api/v1/trials/analysis", cookie).statusCode()).isEqualTo(204);
        String token = cookie.substring("muneo_trial=".length());
        assertThat(store.begin("analysis", token)).isNotNull();
        ai.verify();
    }

    @Test
    void oversizedUploadReturnsActionable413WithoutCallingAi() throws Exception {
        HttpResponse<String> response = upload(session("analysis"), "estimate.pdf", "x".repeat(11 * 1024 * 1024));
        assertThat(response.statusCode()).isEqualTo(413);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("status").asInt()).isEqualTo(413);
        assertThat(body.get("code").asText()).isEqualTo("FILE_UPLOAD_SIZE_EXCEEDED");
        assertThat(body.get("message").asText()).contains("10MB");
        ai.verify();
    }

    private String session(String kind) throws Exception {
        HttpResponse<String> response = get("/api/v1/trials/" + kind, "");
        assertThat(response.statusCode()).isEqualTo(204);
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=1800");
        return cookie.split(";", 2)[0];
    }

    private String login() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        users.save(User.create(email, passwords.encode("TrialTest123!"), "테스트", "01012345678", LocalDate.of(1995, 1, 1)));
        HttpResponse<String> result = post("/api/v1/users/login", json.writeValueAsString(Map.of("email", email, "password", "TrialTest123!")), "application/json", "");
        assertThat(result.statusCode()).isEqualTo(200);
        return result.headers().allValues("Set-Cookie").stream().map(value -> value.split(";", 2)[0]).collect(Collectors.joining("; "));
    }

    private HttpResponse<String> upload(String cookie, String fileName, String contents) throws Exception {
        String boundary = "TrialBoundary";
        Map<String, String> fields = Map.of("space_type", "아파트", "pyeong", "25", "room_count", "3", "floor", "5",
                "elevator", "true", "region", "서울", "building_age", "10~20년", "company_name", "입력 업체");
        StringBuilder body = new StringBuilder();
        fields.forEach((name, value) -> body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n").append(value).append("\r\n"));
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"files\"; filename=\"").append(fileName)
                .append("\"\r\nContent-Type: application/pdf\r\n\r\n").append(contents).append("\r\n--").append(boundary).append("--\r\n");
        return post("/api/v1/trials/analysis", body.toString(), "multipart/form-data; boundary=" + boundary, cookie);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception { return send("GET", path, "", "application/json", cookie); }
    private HttpResponse<String> post(String path, String body, String type, String cookie) throws Exception { return send("POST", path, body, type, cookie); }
    private HttpResponse<String> send(String method, String path, String body, String type, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", type);
        if (!cookie.isEmpty()) request.header("Cookie", cookie);
        return http.send(request.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
