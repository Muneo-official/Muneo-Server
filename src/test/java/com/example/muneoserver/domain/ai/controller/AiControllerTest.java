package com.example.muneoserver.domain.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.ai.service.AiService;
import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobAcceptedResponse;
import com.example.muneoserver.domain.ai.job.service.EstimateJobService;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.global.dto.ApiResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private EstimateJobService estimateJobService;

    @InjectMocks
    private AiController aiController;

    private final AuthUser authUser = new AuthUser(
            42L,
            "user@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.USER
    );

    @Test
    void generateEstimateReturnsAcceptedJob() {
        EstimateGenerateRequest request = org.mockito.Mockito.mock(EstimateGenerateRequest.class);
        UUID jobId = UUID.randomUUID();
        EstimateJobAcceptedResponse accepted = new EstimateJobAcceptedResponse(
                jobId,
                EstimateJobStatus.PENDING,
                3_000L
        );
        when(estimateJobService.submit(authUser, request)).thenReturn(accepted);

        ResponseEntity<ApiResponse<EstimateJobAcceptedResponse>> response =
                aiController.generateEstimate(authUser, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo(accepted);
        assertThat(response.getBody().status()).isEqualTo(202);
    }

    @Test
    void getEstimatesWrapsAiArrayInApiResponse() {
        List<Map<String, Object>> estimates = List.of(Map.of("id", "estimate-1"));
        when(aiService.getEstimates(authUser)).thenReturn(ResponseEntity.ok(estimates));

        ResponseEntity<Object> response = aiController.getEstimates(authUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.success()).isTrue();
        assertThat(body.result()).isEqualTo(estimates);
        assertThat(body.message()).isEqualTo("견적 목록 조회에 성공했습니다.");
    }

    @Test
    void getRiskReportsKeepsEmptyArrayInsideApiResponse() {
        List<Object> reports = List.of();
        when(aiService.getRiskReports(authUser)).thenReturn(ResponseEntity.ok(reports));

        ResponseEntity<Object> response = aiController.getRiskReports(authUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.success()).isTrue();
        assertThat(body.result()).isEqualTo(reports);
        assertThat(body.message()).isEqualTo("리스크 보고서 목록 조회에 성공했습니다.");
    }

    @Test
    void getEstimatesKeepsAiErrorResponseUnchanged() {
        Map<String, Object> error = Map.of("detail", "AI server error");
        ResponseEntity<Object> aiResponse = ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        when(aiService.getEstimates(authUser)).thenReturn(aiResponse);

        ResponseEntity<Object> response = aiController.getEstimates(authUser);

        assertThat(response).isSameAs(aiResponse);
    }
}
