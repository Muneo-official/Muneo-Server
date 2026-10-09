package com.example.muneoserver.domain.riskfeedback.dto;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackRating;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record RiskFeedbackResponse(
        @JsonProperty("schema_version") int schemaVersion,
        RiskFeedbackRating rating,
        List<RiskFeedbackReason> reasons,
        String comment,
        @JsonProperty("item_reviews") List<RiskFeedbackItemResponse> itemReviews,
        @JsonProperty("report_version") String reportVersion,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {

    public static RiskFeedbackResponse from(RiskFeedback feedback) {
        return new RiskFeedbackResponse(
                feedback.getSchemaVersion(),
                feedback.getRating(),
                List.copyOf(feedback.getReasons()),
                feedback.getComment(),
                feedback.getItemReviews().stream()
                        .map(RiskFeedbackItemResponse::from)
                        .toList(),
                feedback.getReportVersion(),
                feedback.getUpdatedAt()
        );
    }
}
