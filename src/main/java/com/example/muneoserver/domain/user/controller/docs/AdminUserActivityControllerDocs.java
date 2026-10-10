package com.example.muneoserver.domain.user.controller.docs;

import com.example.muneoserver.domain.user.dto.admin.activity.AdminRiskFeedbackResponse;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminStoredActivityResponse;
import com.example.muneoserver.global.dto.ApiResponse;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin User Activity", description = "관리자 사용자 이용 내역 조회 API")
public interface AdminUserActivityControllerDocs {

    @Operation(summary = "사용자 저장 예상 견적 조회")
    ResponseEntity<ApiResponse<PageResponse<AdminStoredActivityResponse>>> getEstimates(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "조회할 사용자 ID") @Positive(message = "userId는 양수여야 합니다.") Long userId,
            @Parameter(description = "페이지 번호", example = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    );

    @Operation(summary = "사용자 저장 견적서 진단 조회")
    ResponseEntity<ApiResponse<PageResponse<AdminStoredActivityResponse>>> getRiskReports(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "조회할 사용자 ID") @Positive(message = "userId는 양수여야 합니다.") Long userId,
            @Parameter(description = "페이지 번호", example = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    );

    @Operation(summary = "사용자 진단 피드백 조회")
    ResponseEntity<ApiResponse<PageResponse<AdminRiskFeedbackResponse>>> getFeedback(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "조회할 사용자 ID") @Positive(message = "userId는 양수여야 합니다.") Long userId,
            @Parameter(description = "페이지 번호", example = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    );
}
