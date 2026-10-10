package com.example.muneoserver.domain.user.dto.admin.activity;

import com.example.muneoserver.domain.user.document.AdminStoredActivity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record AdminStoredActivityResponse(
        String id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("created_at") Instant createdAt,
        Map<String, Object> input,
        Map<String, Object> result
) {

    public static AdminStoredActivityResponse from(AdminStoredActivity activity) {
        return new AdminStoredActivityResponse(
                activity.id(),
                activity.userId(),
                activity.createdAt(),
                activity.input(),
                activity.result()
        );
    }
}
