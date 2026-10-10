package com.example.muneoserver.domain.riskfeedback.dto;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackRating;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RiskFeedbackRequest(
        @JsonProperty("schema_version")
        @NotNull(message = "schema_version은 필수입니다.")
        @Min(value = 1, message = "schema_version은 1이어야 합니다.")
        @Max(value = 1, message = "schema_version은 1이어야 합니다.")
        Integer schemaVersion,

        @NotNull(message = "rating은 필수입니다.")
        RiskFeedbackRating rating,

        List<@NotNull(message = "reasons에는 null을 포함할 수 없습니다.") RiskFeedbackReason> reasons,

        String comment,

        @JsonProperty("item_reviews")
        List<@Valid @NotNull(message = "item_reviews에는 null을 포함할 수 없습니다.") RiskFeedbackItemRequest> itemReviews
) {
}
