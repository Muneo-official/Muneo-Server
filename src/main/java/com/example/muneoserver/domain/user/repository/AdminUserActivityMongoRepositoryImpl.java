package com.example.muneoserver.domain.user.repository;

import com.example.muneoserver.domain.user.document.AdminStoredActivity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminUserActivityMongoRepositoryImpl implements AdminUserActivityMongoRepository {

    private static final String ESTIMATES_COLLECTION = "estimates";
    private static final String RISK_REPORTS_COLLECTION = "risk_reports";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "access_token",
            "refresh_token",
            "estimate_token",
            "authorization",
            "api_key",
            "apikey",
            "secret",
            "internal_key"
    );

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<AdminStoredActivity> findEstimatesByUserId(String userId, Pageable pageable) {
        return findByUserId(ESTIMATES_COLLECTION, userId, pageable);
    }

    @Override
    public Page<AdminStoredActivity> findRiskReportsByUserId(String userId, Pageable pageable) {
        return findByUserId(RISK_REPORTS_COLLECTION, userId, pageable);
    }

    private Page<AdminStoredActivity> findByUserId(String collection, String userId, Pageable pageable) {
        Document facet = mongoTemplate.execute(collection, mongoCollection -> mongoCollection.aggregate(List.of(
                        new Document("$match", new Document("user_id", userId)),
                        new Document("$facet", new Document()
                                .append("content", List.of(
                                        new Document("$sort", new Document("created_at", -1).append("_id", -1)),
                                        new Document("$skip", pageable.getOffset()),
                                        new Document("$limit", pageable.getPageSize()),
                                        new Document("$project", new Document()
                                                .append("_id", 1)
                                                .append("user_id", 1)
                                                .append("created_at", 1)
                                                .append("input", 1)
                                                .append("result", 1))
                                ))
                                .append("metadata", List.of(new Document("$count", "total"))))
                )).first());

        List<Document> contentDocuments = facet == null
                ? List.of()
                : facet.getList("content", Document.class, List.of());
        List<AdminStoredActivity> content = contentDocuments.stream()
                .map(this::toActivity)
                .toList();
        long total = extractTotal(facet);
        return new PageImpl<>(content, pageable, total);
    }

    private long extractTotal(Document facet) {
        if (facet == null) {
            return 0L;
        }
        List<Document> metadata = facet.getList("metadata", Document.class, List.of());
        if (metadata.isEmpty() || !(metadata.getFirst().get("total") instanceof Number total)) {
            return 0L;
        }
        return total.longValue();
    }

    private AdminStoredActivity toActivity(Document document) {
        Object rawId = document.get("_id");
        return new AdminStoredActivity(
                rawId == null ? null : String.valueOf(rawId),
                document.get("user_id") == null ? null : String.valueOf(document.get("user_id")),
                toInstant(document.get("created_at")),
                sanitizeMap(document.get("input")),
                sanitizeMap(document.get("result"))
        );
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException firstFailure) {
                try {
                    return OffsetDateTime.parse(text).toInstant();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Map<String, Object> sanitizeMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        rawMap.forEach((rawKey, rawValue) -> {
            if (!(rawKey instanceof String key) || isSensitiveKey(key) || isBinary(rawValue)) {
                return;
            }
            sanitized.put(key, sanitizeValue(rawValue));
        });
        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return sanitizeMap(value);
        }
        if (value instanceof List<?> values) {
            List<Object> sanitized = new ArrayList<>();
            for (Object element : values) {
                if (!isBinary(element)) {
                    sanitized.add(sanitizeValue(element));
                }
            }
            return sanitized;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    private boolean isBinary(Object value) {
        return value instanceof byte[] || value instanceof Binary;
    }
}
