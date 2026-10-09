package com.example.muneoserver.domain.riskfeedback.controller;

import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackRequest;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackResponse;
import com.example.muneoserver.domain.riskfeedback.service.RiskFeedbackService;
import com.example.muneoserver.global.dto.ApiResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/risk-detector/{reportId}/feedback")
@RequiredArgsConstructor
public class RiskFeedbackController {

    private static final String OBJECT_ID_PATTERN = "^[0-9a-fA-F]{24}$";

    private final RiskFeedbackService riskFeedbackService;

    @PutMapping
    public ResponseEntity<Void> put(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable
            @NotBlank(message = "reportId는 필수입니다.")
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "reportId 형식이 올바르지 않습니다.")
            String reportId,
            @Valid @RequestBody RiskFeedbackRequest request
    ) {
        riskFeedbackService.put(authUser, reportId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RiskFeedbackResponse>> get(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable
            @NotBlank(message = "reportId는 필수입니다.")
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "reportId 형식이 올바르지 않습니다.")
            String reportId
    ) {
        return riskFeedbackService.get(authUser, reportId)
                .map(feedback -> ResponseEntity.ok(
                        ApiResponse.success(feedback, "리스크 피드백 조회에 성공했습니다.")
                ))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable
            @NotBlank(message = "reportId는 필수입니다.")
            @Pattern(regexp = OBJECT_ID_PATTERN, message = "reportId 형식이 올바르지 않습니다.")
            String reportId
    ) {
        riskFeedbackService.delete(authUser, reportId);
        return ResponseEntity.noContent().build();
    }
}
