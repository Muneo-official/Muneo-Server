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
        Map<String, Object> inputMap = mapper.convertValue(input, new TypeReference<>() {});
        Map<String, Object> preview = project(kind, inputMap, result);
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

    private static final List<String> RISK_STATUSES = List.of("누락", "중복", "불분명");
    // 대표 공정을 고를 때 확인이 급한 상태일수록 높게 친다.
    private static final Map<String, Integer> RISK_WEIGHT = Map.of("누락", 3, "불분명", 2, "중복", 1);

    // 비회원 응답은 대표 공정 하나의 전체 내용과 나머지 공정의 이름·건수만 새로 구성한다.
    // 잠긴 공정의 내용은 응답에 넣지 않는다. 화면은 잠긴 자리를 가짜 행으로 흐리게 그린다.
    static Map<String, Object> project(String kind, Map<String, Object> input, Object result) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("kind", kind);
        Map<String, Object> data = object(result);
        if ("analysis".equals(kind) ? !(object(data.get("report")).get("process_sections") instanceof List<?>)
                : !(data.get("선택_공종") instanceof List<?>)) {
            throw new IllegalStateException("올바른 분석 결과를 받지 못했습니다.");
        }
        if ("analysis".equals(kind)) {
            projectAnalysis(preview, object(data.get("report")));
        } else {
            projectEstimate(preview, input, data);
        }
        return preview;
    }

    private static void projectAnalysis(Map<String, Object> preview, Map<String, Object> report) {
        List<?> sections = list(report.get("process_sections"));
        Map<String, Integer> counts = emptyCounts();
        List<Map<String, Object>> summaries = new ArrayList<>();
        int totalItems = 0;
        int revealed = -1;
        int bestScore = -1;
        int bestSize = -1;
        for (int index = 0; index < sections.size(); index++) {
            Map<String, Object> section = object(sections.get(index));
            List<?> items = list(section.get("items"));
            Map<String, Integer> sectionCounts = emptyCounts();
            int score = 0;
            for (Object item : items) {
                String status = status(object(item));
                if (sectionCounts.containsKey(status)) sectionCounts.compute(status, (k, count) -> count + 1);
                score += RISK_WEIGHT.getOrDefault(status, 0);
            }
            sectionCounts.forEach((status, count) -> counts.merge(status, count, Integer::sum));
            totalItems += items.size();
            if (!items.isEmpty() && (score > bestScore || (score == bestScore && items.size() > bestSize))) {
                revealed = index;
                bestScore = score;
                bestSize = items.size();
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", sectionId(section, index));
            summary.put("display_name", sectionName(section));
            summary.put("itemCount", items.size());
            summary.put("counts", sectionCounts);
            summaries.add(summary);
        }
        for (int index = 0; index < summaries.size(); index++) summaries.get(index).put("locked", index != revealed);

        Map<String, Object> visibleSection = revealed < 0 ? null : visibleSection(object(sections.get(revealed)), revealed);
        Map<String, Object> visibleReport = new LinkedHashMap<>();
        visibleReport.put("subtitle_fields",
                pick(object(report.get("subtitle_fields")), "company_name", "pyeong", "analyzed_date"));
        visibleReport.put("construction_info", pick(object(report.get("construction_info")),
                "space_type", "region", "building_age", "floor", "elevator", "room_count"));
        visibleReport.put("process_sections", visibleSection == null ? List.of() : List.of(visibleSection));

        preview.put("report", visibleReport);
        preview.put("sections", summaries);
        preview.put("counts", counts);
        preview.put("totalItems", totalItems);
        // 배포 전환 중 이전 화면 호환용. 대표 공정 안의 첫 확인 항목만 담는다.
        preview.put("firstItem", visibleSection == null ? null : legacyFirstItem(visibleSection));
    }

    private static void projectEstimate(Map<String, Object> preview, Map<String, Object> input,
            Map<String, Object> data) {
        Map<String, Object> ranges = object(data.get("공종별_단가_범위"));
        Map<String, Object> specs = object(data.get("공종별_항목_명세"));
        List<String> selected = strings(data.get("선택_공종"));
        List<String> insufficient = strings(data.get("데이터_부족_공종"));

        String revealed = null;
        long[] best = null;
        for (String name : selected) {
            Map<String, Object> range = object(ranges.get(name));
            if (insufficient.contains(name) || !(range.get("최소") instanceof Number)
                    || !(range.get("최대") instanceof Number max)) continue;
            List<?> items = list(specs.get(name));
            long evidenced = items.stream()
                    .filter(item -> object(item).get("등장_사례_수") instanceof Number count && count.longValue() > 0)
                    .count();
            long[] score = {evidenced, items.size(), max.longValue()};
            if (best == null || compare(score, best) > 0) {
                best = score;
                revealed = name;
            }
        }

        Map<String, Object> visibleRanges = new LinkedHashMap<>();
        Map<String, Object> visibleSpecs = new LinkedHashMap<>();
        List<Map<String, Object>> locked = new ArrayList<>();
        for (String name : selected) {
            // 데이터 부족 공정은 보여줄 금액이 없으므로 잠그지 않고 그대로 안내한다.
            if (name.equals(revealed) || insufficient.contains(name)) {
                if (ranges.containsKey(name)) visibleRanges.put(name, range(object(ranges.get(name))));
                if (specs.containsKey(name)) visibleSpecs.put(name, lineItems(list(specs.get(name))));
            } else {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("name", name);
                summary.put("itemCount", list(specs.get(name)).size());
                summary.put("priced", ranges.containsKey(name));
                locked.add(summary);
            }
        }

        Map<String, Object> visible = new LinkedHashMap<>();
        visible.put("선택_공종", selected);
        if (data.get("시공범위") instanceof String scope) visible.put("시공범위", scope);
        visible.put("보정_적용", strings(data.get("보정_적용")));
        visible.put("참고_사례_수", data.get("참고_사례_수") instanceof Number count ? count : 0);
        visible.put("데이터_부족_공종", insufficient);
        visible.put("공종별_단가_범위", visibleRanges);
        visible.put("공종별_항목_명세", visibleSpecs);

        preview.put("input", input);
        preview.put("result", visible);
        preview.put("revealedProcess", revealed);
        preview.put("lockedProcesses", locked);
        preview.put("totalProcesses", selected.size());
        Map<String, Object> first = null;
        if (revealed != null) {
            Map<String, Object> range = object(ranges.get(revealed));
            first = new LinkedHashMap<>();
            first.put("name", revealed);
            first.put("min", range.get("최소"));
            first.put("max", range.get("최대"));
        }
        // 배포 전환 중 이전 화면 호환용. 대표 공정과 같은 공정을 가리킨다.
        preview.put("firstProcess", first);
    }

    private static Map<String, Integer> emptyCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        RISK_STATUSES.forEach(status -> counts.put(status, 0));
        return counts;
    }

    private static String status(Map<String, Object> item) {
        return String.valueOf(item.getOrDefault("status", "불분명"));
    }

    private static String sectionId(Map<String, Object> section, int index) {
        return section.get("id") instanceof String id && !id.isBlank() ? id : String.valueOf(index);
    }

    private static String sectionName(Map<String, Object> section) {
        Object name = section.get("display_name") instanceof String text ? text : section.get("process");
        return name instanceof String text ? text : "";
    }

    private static Map<String, Object> visibleSection(Map<String, Object> section, int index) {
        Map<String, Object> visible = new LinkedHashMap<>();
        visible.put("id", sectionId(section, index));
        visible.put("process", section.get("process") instanceof String process ? process : "");
        visible.put("display_name", sectionName(section));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object itemValue : list(section.get("items"))) {
            Map<String, Object> item = object(itemValue);
            Map<String, Object> copy = new LinkedHashMap<>();
            if (item.get("id") instanceof String id) copy.put("id", id);
            copy.put("status", status(item));
            for (String field : List.of("title", "description", "guide")) {
                copy.put(field, item.get(field) instanceof String text ? text : "");
            }
            items.add(copy);
        }
        visible.put("items", items);
        return visible;
    }

    private static Map<String, Object> legacyFirstItem(Map<String, Object> section) {
        List<?> items = list(section.get("items"));
        Map<String, Object> item = items.stream().map(TrialStore::object)
                .filter(candidate -> !"정상".equals(candidate.get("status"))).findFirst()
                .orElse(object(items.getFirst()));
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("process", section.get("display_name"));
        for (String field : List.of("status", "title", "description", "guide")) legacy.put(field, item.get(field));
        return legacy;
    }

    private static Map<String, Object> pick(Map<String, Object> source, String... fields) {
        Map<String, Object> picked = new LinkedHashMap<>();
        for (String field : fields) {
            Object value = source.get(field);
            if (value instanceof String || value instanceof Number || value instanceof Boolean) picked.put(field, value);
        }
        return picked;
    }

    private static Map<String, Object> range(Map<String, Object> source) {
        return pick(source, "최소", "중간", "최대");
    }

    private static List<Map<String, Object>> lineItems(List<?> items) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Object itemValue : items) {
            Map<String, Object> item = object(itemValue);
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("description", item.get("description") instanceof String text ? text : "");
            copy.put("amount_range", range(object(item.get("amount_range"))));
            copy.put("등장_사례_수", item.get("등장_사례_수") instanceof Number count ? count : 0);
            visible.add(copy);
        }
        return visible;
    }

    private static List<String> strings(Object value) {
        return list(value).stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static int compare(long[] left, long[] right) {
        for (int index = 0; index < left.length; index++) {
            int result = Long.compare(left[index], right[index]);
            if (result != 0) return result;
        }
        return 0;
    }
}
