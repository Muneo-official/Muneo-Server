package com.example.muneoserver.domain.riskfeedback.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RiskFeedbackVerdict {

    @JsonProperty("confirmed_issue")
    CONFIRMED_ISSUE,

    @JsonProperty("not_issue")
    NOT_ISSUE
}
