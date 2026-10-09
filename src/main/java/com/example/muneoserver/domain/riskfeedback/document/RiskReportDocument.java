package com.example.muneoserver.domain.riskfeedback.document;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "risk_reports")
public class RiskReportDocument {

    @Id
    private ObjectId id;

    @Field("user_id")
    private String userId;

    @Field("created_at")
    private Instant createdAt;

    private Map<String, Object> result;

    public ObjectId getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String resolveVersion() {
        String explicitVersion = stringValue(result, "report_version");
        if (explicitVersion != null) {
            return explicitVersion;
        }

        Map<String, Object> report = mapValue(result, "report");
        explicitVersion = stringValue(report, "report_version");
        return explicitVersion != null ? explicitVersion : id.toHexString();
    }

    public Set<String> resolveItemIds() {
        Set<String> itemIds = new LinkedHashSet<>();
        Map<String, Object> report = mapValue(result, "report");
        List<?> sections = listValue(report, "process_sections");

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            if (!(sections.get(sectionIndex) instanceof Map<?, ?> rawSection)) {
                continue;
            }
            Map<String, Object> section = castMap(rawSection);
            List<?> items = listValue(section, "items");
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                if (!(items.get(itemIndex) instanceof Map<?, ?> rawItem)) {
                    continue;
                }
                Map<String, Object> item = castMap(rawItem);
                String itemId = stringValue(item, "id");
                itemIds.add(itemId != null ? itemId : sectionIndex + "_" + itemIndex);
            }
        }
        return Set.copyOf(itemIds);
    }

    private static Map<String, Object> mapValue(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Map<?, ?> value)) {
            return Map.of();
        }
        return castMap(value);
    }

    private static List<?> listValue(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof List<?> value)) {
            return List.of();
        }
        return value;
    }

    private static String stringValue(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String textKey) {
                result.put(textKey, value);
            }
        });
        return result;
    }
}
