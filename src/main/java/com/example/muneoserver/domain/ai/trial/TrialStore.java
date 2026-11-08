package com.example.muneoserver.domain.ai.trial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class TrialStore {
    public static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    private final Duration lockTtl;
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
        redis.call('DEL', KEYS[1])
        redis.call('HSET', KEYS[1], 'data', ARGV[2])
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
        local data = redis.call('HGET', KEYS[1], 'data')
        if not data then return nil end
        local owner = redis.call('HGET', KEYS[1], 'owner')
        if owner and owner ~= ARGV[1] then return '__FORBIDDEN__' end
        redis.call('HSET', KEYS[1], 'owner', ARGV[1])
        return data
        """, String.class);

    public TrialStore(StringRedisTemplate redis,
            @Value("${app.ai.read-timeout-ms:300000}") int readTimeoutMs) {
        this.redis = redis;
        this.lockTtl = Duration.ofMillis(Math.max(readTimeoutMs, 300000L)).plusMinutes(1);
    }

    public String begin(String kind, String token) {
        String lease = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(kind, token) + ":lock", lease, lockTtl))
                ? lease : null;
    }

    public void finish(String kind, String token, String lease) {
        redis.execute(RELEASE, List.of(key(kind, token) + ":lock"), lease);
    }

    public Map<String, Object> save(String kind, String token, String lease, Object input, Object result) {
        Map<String, Object> preview = project(kind, result);
        preview.put("expiresAt", Instant.now().plus(TTL).toString());
        Map<String, Object> data = Map.of("input", input, "result", result, "preview", preview);
        final String json;
        try {
            json = mapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("체험 결과를 보관하지 못했습니다.", e);
        }
        Long saved = redis.execute(SAVE, List.of(key(kind, token), key(kind, token) + ":lock"),
                lease, json, String.valueOf(TTL.toMillis()));
        if (!Long.valueOf(1).equals(saved)) throw new IllegalStateException("분석 요청의 유효 시간이 지났습니다.");
        return preview;
    }

    public Map<String, Object> read(String kind, String token) {
        if (!validToken(token)) return null;
        Object data = redis.opsForHash().get(key(kind, token), "data");
        return data == null ? null : decode(data.toString());
    }

    public record Claim(Map<String, Object> data, boolean forbidden) {}

    // 소유자 확인·귀속과 결과 읽기를 한 번에 실행한다. TTL을 연장하지 않는다.
    public Claim claim(String kind, String token, Long userId) {
        if (!validToken(token)) return new Claim(null, false);
        String data = redis.execute(CLAIM, List.of(key(kind, token)), String.valueOf(userId));
        if ("__FORBIDDEN__".equals(data)) return new Claim(null, true);
        return new Claim(data == null ? null : decode(data), false);
    }

    public static boolean validToken(String token) {
        return token != null && token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    }

    static String key(String kind, String token) {
        return "trial:{" + kind + ":" + token + "}";
    }

    private Map<String, Object> decode(String data) {
        try {
            return mapper.readValue(data, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("체험 결과를 불러오지 못했습니다.", e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    // 비회원 응답은 허용된 요약 필드와 한 항목만 새로 구성한다. 전체 결과를 가려서 보내지 않는다.
    static Map<String, Object> project(String kind, Object result) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("kind", kind);
        Map<String, Object> data = object(result);
        if ("analysis".equals(kind) ? !(object(data.get("report")).get("process_sections") instanceof List<?>)
                : !(data.get("선택_공종") instanceof List<?>)) {
            throw new IllegalStateException("올바른 분석 결과를 받지 못했습니다.");
        }
        if ("analysis".equals(kind)) {
            Map<String, Object> report = object(data.get("report"));
            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("누락", 0);
            counts.put("중복", 0);
            counts.put("불분명", 0);
            for (Object sectionValue : list(report.get("process_sections"))) {
                Map<String, Object> section = object(sectionValue);
                for (Object itemValue : list(section.get("items"))) {
                    Map<String, Object> item = object(itemValue);
                    String status = String.valueOf(item.getOrDefault("status", "불분명"));
                    if (counts.containsKey(status)) counts.compute(status, (k, count) -> count + 1);
                    Map<String, Object> visible = new LinkedHashMap<>();
                    visible.put("process", section.getOrDefault("display_name", section.getOrDefault("process", "")));
                    for (String field : List.of("status", "title", "description", "guide")) {
                        visible.put(field, item.get(field) instanceof String text ? text : "");
                    }
                    items.add(visible);
                }
            }
            preview.put("totalItems", items.size());
            preview.put("counts", counts);
            preview.put("firstItem", items.stream().filter(item -> !"정상".equals(item.get("status")))
                    .findFirst().orElse(items.isEmpty() ? null : items.getFirst()));
        } else {
            Map<String, Object> ranges = object(data.get("공종별_단가_범위"));
            List<?> selected = list(data.get("선택_공종"));
            preview.put("totalProcesses", selected.size());
            Map<String, Object> first = null;
            for (Object name : selected) {
                Map<String, Object> range = object(ranges.get(String.valueOf(name)));
                if (range.get("최소") instanceof Number && range.get("최대") instanceof Number) {
                    first = new LinkedHashMap<>();
                    first.put("name", name);
                    first.put("min", range.get("최소"));
                    first.put("max", range.get("최대"));
                    break;
                }
            }
            preview.put("firstProcess", first);
        }
        return preview;
    }
}
