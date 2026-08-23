package com.example.muneoserver.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.repository.AiApiRepository;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
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

    @Test
    void generateEstimatePassesAuthenticatedUserIdToAiRepository() {
        AuthUser authUser = new AuthUser(42L, "user@example.com", AuthProvider.LOCAL, true, UserRole.USER);
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
}
