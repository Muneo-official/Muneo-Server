package com.example.muneoserver.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.dto.EstimateSaveRequest;
import com.example.muneoserver.domain.ai.repository.AiApiRepository;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private AiApiRepository aiApiRepository;

    @InjectMocks
    private AiServiceImpl aiService;

    private final AuthUser authUser = new AuthUser(
            42L,
            "user@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.USER
    );

    @Test
    void generateEstimatePassesAuthenticatedUserIdToAiRepository() {
        EstimateGenerateRequest request = mock(EstimateGenerateRequest.class);
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok().build();
        when(aiApiRepository.generateEstimate("42", request)).thenReturn(expectedResponse);

        ResponseEntity<Object> response = aiService.generateEstimate(authUser, request);

        assertThat(response).isSameAs(expectedResponse);
        verify(aiApiRepository).generateEstimate("42", request);
    }

    @Test
    void generateEstimateRejectsMissingAuthentication() {
        EstimateGenerateRequest request = mock(EstimateGenerateRequest.class);

        assertThatThrownBy(() -> aiService.generateEstimate(null, request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(aiApiRepository);
    }

    @Test
    void saveEstimateExtractsTokenFromResultForAiRequest() {
        EstimateSaveRequest request = new EstimateSaveRequest(
                Map.of("평수", 30),
                Map.of("estimate_token", "estimate-token-123", "총_견적_범위", Map.of())
        );
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok().build();
        when(aiApiRepository.saveEstimate("42", "estimate-token-123")).thenReturn(expectedResponse);

        ResponseEntity<Object> response = aiService.saveEstimate(authUser, request);

        assertThat(response).isSameAs(expectedResponse);
        verify(aiApiRepository).saveEstimate("42", "estimate-token-123");
    }

    @Test
    void saveEstimateRejectsMissingTokenWithoutCallingAi() {
        EstimateSaveRequest request = new EstimateSaveRequest(Map.of("평수", 30), Map.of());

        assertThatThrownBy(() -> aiService.saveEstimate(authUser, request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(commonException.getErrors())
                            .containsEntry("result.estimate_token", "estimate_token은 필수입니다.");
                });

        verifyNoInteractions(aiApiRepository);
    }

    @Test
    void saveEstimateRejectsBlankTokenWithoutCallingAi() {
        EstimateSaveRequest request = new EstimateSaveRequest(
                Map.of("평수", 30),
                Map.of("estimate_token", "   ")
        );

        assertThatThrownBy(() -> aiService.saveEstimate(authUser, request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verifyNoInteractions(aiApiRepository);
    }
}
