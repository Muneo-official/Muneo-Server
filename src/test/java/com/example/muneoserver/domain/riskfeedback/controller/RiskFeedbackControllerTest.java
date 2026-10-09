package com.example.muneoserver.domain.riskfeedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackRating;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackRequest;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackResponse;
import com.example.muneoserver.domain.riskfeedback.service.RiskFeedbackService;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.global.dto.ApiResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RiskFeedbackControllerTest {

    private static final String REPORT_ID = "507f1f77bcf86cd799439011";

    @Mock
    private RiskFeedbackService riskFeedbackService;

    @InjectMocks
    private RiskFeedbackController riskFeedbackController;

    private final AuthUser authUser = new AuthUser(
            42L,
            "user@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.USER
    );

    @Test
    void putReturnsNoContent() {
        RiskFeedbackRequest request = new RiskFeedbackRequest(
                1,
                RiskFeedbackRating.GOOD,
                List.of(),
                null,
                List.of()
        );

        ResponseEntity<Void> response = riskFeedbackController.put(authUser, REPORT_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(riskFeedbackService).put(authUser, REPORT_ID, request);
    }

    @Test
    void getReturnsNoContentWhenFeedbackDoesNotExist() {
        when(riskFeedbackService.get(authUser, REPORT_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<RiskFeedbackResponse>> response = riskFeedbackController.get(authUser, REPORT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void getWrapsFeedbackInApiResponse() {
        RiskFeedbackResponse feedback = new RiskFeedbackResponse(
                1,
                RiskFeedbackRating.BAD,
                List.of(),
                "설명이 달라요.",
                List.of(),
                REPORT_ID,
                LocalDateTime.of(2026, 10, 10, 8, 0)
        );
        when(riskFeedbackService.get(authUser, REPORT_ID)).thenReturn(Optional.of(feedback));

        ResponseEntity<ApiResponse<RiskFeedbackResponse>> response = riskFeedbackController.get(authUser, REPORT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo(feedback);
    }

    @Test
    void deleteReturnsNoContent() {
        ResponseEntity<Void> response = riskFeedbackController.delete(authUser, REPORT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(riskFeedbackService).delete(authUser, REPORT_ID);
    }
}
