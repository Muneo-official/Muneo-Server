package com.example.muneoserver.domain.riskfeedback.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RiskFeedbackRating {

    @JsonProperty("good")
    GOOD,

    @JsonProperty("bad")
    BAD
}
