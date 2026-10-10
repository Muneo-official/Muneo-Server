package com.example.muneoserver.domain.user.document;

import java.time.Instant;
import java.util.Map;

public record AdminStoredActivity(
        String id,
        String userId,
        Instant createdAt,
        Map<String, Object> input,
        Map<String, Object> result
) {
}
