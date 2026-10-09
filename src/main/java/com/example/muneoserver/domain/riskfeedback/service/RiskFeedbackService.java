package com.example.muneoserver.domain.riskfeedback.service;

import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackRequest;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.util.Optional;

public interface RiskFeedbackService {

    void put(AuthUser authUser, String reportId, RiskFeedbackRequest request);

    Optional<RiskFeedbackResponse> get(AuthUser authUser, String reportId);

    void delete(AuthUser authUser, String reportId);

    void deleteByUserId(Long userId);

    void deleteByUserIdAndReportId(Long userId, String reportId);
}
