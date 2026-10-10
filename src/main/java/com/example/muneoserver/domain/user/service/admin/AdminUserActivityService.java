package com.example.muneoserver.domain.user.service.admin;

import com.example.muneoserver.domain.user.dto.admin.activity.AdminRiskFeedbackResponse;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminStoredActivityResponse;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import org.springframework.data.domain.Pageable;

public interface AdminUserActivityService {

    PageResponse<AdminStoredActivityResponse> getEstimates(AuthUser authUser, Long userId, Pageable pageable);

    PageResponse<AdminStoredActivityResponse> getRiskReports(AuthUser authUser, Long userId, Pageable pageable);

    PageResponse<AdminRiskFeedbackResponse> getFeedback(AuthUser authUser, Long userId, Pageable pageable);
}
