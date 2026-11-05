package com.example.muneoserver.domain.ai.trial;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.dto.RiskAnalyzeRequest;
import com.example.muneoserver.domain.ai.service.AiService;
import com.example.muneoserver.domain.ai.repository.AiApiRepository;
import com.example.muneoserver.global.security.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/trials")
@RequiredArgsConstructor
public class TrialController {
    private final AiService aiService;
    private final AiApiRepository aiRepository;
    private final TrialEstimateTokens estimateTokens;
    private final TrialStore store;
    private final TrialRateLimiter rateLimiter;
    private static final String COOKIE = "muneo_trial";

    @GetMapping("/{kind}")
    public ResponseEntity<Object> preview(@PathVariable String kind,
            @CookieValue(value = COOKIE, required = false) String cookie) {
        if (!validKind(kind)) return error(404, "체험 경로를 찾을 수 없습니다.");
        String token = token(cookie);
        Map<String, Object> data = store.read(kind, token);
        return ResponseEntity.status(data == null ? 204 : 200)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookie(token))
                .body(data == null ? null : data.get("preview"));
    }

    @PostMapping(value = "/analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> analyze(
            @CookieValue(value = COOKIE, required = false) String cookie,
            @RequestParam("space_type") @NotBlank String spaceType,
            @RequestParam("pyeong") @NotNull @Min(1) Integer pyeong,
            @RequestParam("room_count") @NotNull @Min(0) Integer roomCount,
            @RequestParam("floor") @NotNull Integer floor,
            @RequestParam("elevator") @NotNull Boolean elevator,
            @RequestParam("region") @NotBlank String region,
            @RequestParam("building_age") @NotBlank String buildingAge,
            @RequestParam("company_name") @NotBlank String companyName,
            @RequestParam("files") MultipartFile[] files) {
        if (files.length == 0 || files.length > 5) return error(400, "견적서는 1~5개까지 올릴 수 있어요.");
        long totalSize = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name == null || !name.toLowerCase(java.util.Locale.ROOT).matches(".+\\.(pdf|jpe?g|png|csv|xlsx?)"))
                return error(400, "PDF, JPG, PNG, CSV, Excel 파일을 올려 주세요.");
            if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) return error(400, "각 파일은 10MB 이하로 올려 주세요.");
            totalSize += file.getSize();
        }
        if (totalSize > 20 * 1024 * 1024) return error(400, "파일 전체 크기는 20MB 이하여야 해요.");
        String token = token(cookie);
        String lease = store.begin("analysis", token);
        if (lease == null) return error(409, "앞선 진단이 진행 중이에요. 잠시 후 결과를 확인해 주세요.");
        try {
            if (!rateLimiter.reserve(token)) return error(429, "체험 요청이 많아요. 잠시 후 다시 시도해 주세요.");
            RiskAnalyzeRequest request = new RiskAnalyzeRequest(spaceType, pyeong, roomCount, floor, elevator,
                    region, buildingAge, companyName, files);
            ResponseEntity<Object> response = aiService.analyzeRisk(request);
            if (!response.getStatusCode().is2xxSuccessful()) return error(502, "분석을 완료하지 못했어요. 입력을 확인하고 다시 시도해 주세요.");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("space_type", spaceType); input.put("pyeong", pyeong); input.put("room_count", roomCount);
            input.put("floor", floor); input.put("elevator", elevator); input.put("region", region);
            input.put("building_age", buildingAge); input.put("company_name", companyName);
            return saved("analysis", token, lease, input, response.getBody());
        } finally {
            store.finish("analysis", token, lease);
        }
    }

    @PostMapping("/estimate")
    public ResponseEntity<Object> estimate(@CookieValue(value = COOKIE, required = false) String cookie,
            @Valid @RequestBody EstimateGenerateRequest input) {
        if (input.pyeong() < 1 || input.roomCount() < 0 || input.floor() < 0 || input.processTypes().size() > 20)
            return error(400, "면적, 방 개수, 층수와 선택한 공정을 확인해 주세요.");
        String token = token(cookie);
        String lease = store.begin("estimate", token);
        if (lease == null) return error(409, "앞선 가견적 생성이 진행 중이에요. 잠시 후 결과를 확인해 주세요.");
        try {
            if (!rateLimiter.reserve(token)) return error(429, "체험 요청이 많아요. 잠시 후 다시 시도해 주세요.");
            ResponseEntity<Object> response = aiRepository.generateEstimate(TrialEstimateTokens.guestId(token), input);
            if (!response.getStatusCode().is2xxSuccessful()) return error(502, "분석을 완료하지 못했어요. 입력을 확인하고 다시 시도해 주세요.");
            estimateTokens.extend(token, response.getBody());
            return saved("estimate", token, lease, input, response.getBody());
        } finally {
            store.finish("estimate", token, lease);
        }
    }

    @GetMapping("/{kind}/result")
    public ResponseEntity<Object> result(@PathVariable String kind,
            @CookieValue(value = COOKIE, required = false) String cookie,
            @AuthenticationPrincipal AuthUser user) {
        if (user == null) return error(401, "전체 결과는 로그인 후 확인할 수 있어요.");
        if (!validKind(kind)) return error(404, "체험 경로를 찾을 수 없습니다.");
        TrialStore.Claim claimed = store.claim(kind, cookie, user.id());
        if (claimed.forbidden()) return error(403, "이 결과를 확인할 수 있는 계정이 아닙니다.");
        Map<String, Object> data = claimed.data();
        if (data == null) return error(410, "임시 결과 보관 시간이 지났어요. 다시 체험해 주세요.");
        if ("estimate".equals(kind)) estimateTokens.claim(cookie, user.id(), data.get("result"));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("input", data.get("input"), "result", data.get("result")));
    }

    private ResponseEntity<Object> saved(String kind, String token, String lease, Object input, Object result) {
        if (result == null) return error(502, "분석 결과를 받지 못했어요. 다시 시도해 주세요.");
        Map<String, Object> preview = store.save(kind, token, lease, input, result);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookie(token)).body(preview);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> storageUnavailable() {
        return error(503, "체험 결과 보관소에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler({IllegalStateException.class, RestClientException.class})
    public ResponseEntity<Object> analysisUnavailable() {
        return error(502, "분석을 완료하지 못했어요. 입력은 유지되니 다시 시도해 주세요.");
    }

    private boolean validKind(String kind) { return "analysis".equals(kind) || "estimate".equals(kind); }
    private String token(String cookie) { return TrialStore.validToken(cookie) ? cookie : UUID.randomUUID().toString(); }
    private String cookie(String token) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(true).sameSite("Lax").path("/")
                .maxAge(TrialStore.TTL).build().toString();
    }
    private ResponseEntity<Object> error(int status, String message) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == 429) response.header(HttpHeaders.RETRY_AFTER, "1800");
        return response.body(Map.of("message", message));
    }
}
