package com.example.muneoserver.domain.riskfeedback.dto;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackItem;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackVerdict;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RiskFeedbackItemResponse(
        @JsonProperty("item_id") String itemId,
        RiskFeedbackVerdict verdict
) {

    public static RiskFeedbackItemResponse from(RiskFeedbackItem item) {
        return new RiskFeedbackItemResponse(item.getItemId(), item.getVerdict());
    }
}
