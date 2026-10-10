package com.example.muneoserver.domain.user.dto.admin.activity;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public record AdminRiskFeedbackResponse(
        String id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("report_id") String reportId,
        @JsonProperty("schema_version") int schemaVersion,
        String rating,
        List<String> reasons,
        String comment,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Seoul");

    public static AdminRiskFeedbackResponse from(RiskFeedback feedback) {
        return new AdminRiskFeedbackResponse(
                String.valueOf(feedback.getId()),
                String.valueOf(feedback.getUser().getId()),
                feedback.getReportId(),
                feedback.getSchemaVersion(),
                feedback.getRating().name().toLowerCase(Locale.ROOT),
                feedback.getReasons().stream()
                        .map(reason -> reason.name().toLowerCase(Locale.ROOT))
                        .toList(),
                feedback.getComment(),
                toInstant(feedback.getCreatedAt()),
                toInstant(feedback.getUpdatedAt())
        );
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(APPLICATION_ZONE).toInstant();
    }
}
