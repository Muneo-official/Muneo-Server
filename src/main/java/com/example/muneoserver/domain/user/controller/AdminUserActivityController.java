package com.example.muneoserver.domain.user.controller;

import com.example.muneoserver.domain.user.controller.docs.AdminUserActivityControllerDocs;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminRiskFeedbackResponse;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminStoredActivityResponse;
import com.example.muneoserver.domain.user.service.admin.AdminUserActivityService;
import com.example.muneoserver.global.dto.ApiResponse;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserActivityController implements AdminUserActivityControllerDocs {

    private final AdminUserActivityService adminUserActivityService;

    @Override
    @GetMapping("/estimates")
    public ResponseEntity<ApiResponse<PageResponse<AdminStoredActivityResponse>>> getEstimates(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return noStore(ApiResponse.success(
                adminUserActivityService.getEstimates(authUser, userId, PageRequest.of(page, size)),
                "사용자 예상 견적 조회에 성공했습니다."
        ));
    }

    @Override
    @GetMapping("/risk-reports")
    public ResponseEntity<ApiResponse<PageResponse<AdminStoredActivityResponse>>> getRiskReports(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return noStore(ApiResponse.success(
                adminUserActivityService.getRiskReports(authUser, userId, PageRequest.of(page, size)),
                "사용자 견적서 진단 조회에 성공했습니다."
        ));
    }

    @Override
    @GetMapping("/feedback")
    public ResponseEntity<ApiResponse<PageResponse<AdminRiskFeedbackResponse>>> getFeedback(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return noStore(ApiResponse.success(
                adminUserActivityService.getFeedback(authUser, userId, PageRequest.of(page, size)),
                "사용자 진단 피드백 조회에 성공했습니다."
        ));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
