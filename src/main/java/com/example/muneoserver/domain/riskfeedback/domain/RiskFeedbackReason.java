package com.example.muneoserver.domain.riskfeedback.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RiskFeedbackReason {

    @JsonProperty("found_risks")
    FOUND_RISKS(RiskFeedbackRating.GOOD),

    @JsonProperty("clear_explanation")
    CLEAR_EXPLANATION(RiskFeedbackRating.GOOD),

    @JsonProperty("useful_questions")
    USEFUL_QUESTIONS(RiskFeedbackRating.GOOD),

    @JsonProperty("incorrect_result")
    INCORRECT_RESULT(RiskFeedbackRating.BAD),

    @JsonProperty("missing_risks")
    MISSING_RISKS(RiskFeedbackRating.BAD),

    @JsonProperty("unclear_explanation")
    UNCLEAR_EXPLANATION(RiskFeedbackRating.BAD);

    private final RiskFeedbackRating rating;

    RiskFeedbackReason(RiskFeedbackRating rating) {
        this.rating = rating;
    }

    public boolean supports(RiskFeedbackRating rating) {
        return this.rating == rating;
    }
}
