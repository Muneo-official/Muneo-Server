package com.example.muneoserver.domain.riskfeedback.dto;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackVerdict;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RiskFeedbackItemRequest(
        @JsonProperty("item_id")
        @NotBlank(message = "item_id는 필수입니다.")
        String itemId,

        @NotNull(message = "verdict는 필수입니다.")
        RiskFeedbackVerdict verdict
) {
}
