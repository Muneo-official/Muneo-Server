package com.example.muneoserver.domain.user.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackRating;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackReason;
import com.example.muneoserver.domain.riskfeedback.repository.RiskFeedbackRepository;
import com.example.muneoserver.domain.user.document.AdminStoredActivity;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminRiskFeedbackResponse;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminStoredActivityResponse;
import com.example.muneoserver.domain.user.repository.AdminUserActivityMongoRepository;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserActivityServiceImplTest {

    private static final Long USER_ID = 42L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminUserActivityMongoRepository adminUserActivityMongoRepository;

    @Mock
    private RiskFeedbackRepository riskFeedbackRepository;

    @InjectMocks
    private AdminUserActivityServiceImpl service;

    private final AuthUser admin = new AuthUser(
            1L,
            "admin@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.ADMIN
    );

    @Test
    void getEstimatesReturnsPagedMongoSnapshots() {
        Pageable request = PageRequest.of(0, 10);
        AdminStoredActivity activity = new AdminStoredActivity(
                "507f1f77bcf86cd799439011",
                "42",
                Instant.parse("2026-10-10T00:00:00Z"),
                Map.of("region", "서울"),
                Map.of("총_견적_범위", "1,000만원~1,200만원")
        );
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(adminUserActivityMongoRepository.findEstimatesByUserId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(activity), request, 1));

        PageResponse<AdminStoredActivityResponse> response = service.getEstimates(admin, USER_ID, request);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("507f1f77bcf86cd799439011");
            assertThat(item.userId()).isEqualTo("42");
            assertThat(item.input()).containsEntry("region", "서울");
        });

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminUserActivityMongoRepository)
                .findEstimatesByUserId(org.mockito.ArgumentMatchers.eq("42"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void getFeedbackReturnsContractFieldsWithoutItemReviews() {
        User user = User.create(
                "user@example.com",
                "encoded-password",
                "사용자",
                "010-1234-5678",
                LocalDate.of(2000, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        RiskFeedback feedback = RiskFeedback.create(user, "507f1f77bcf86cd799439011", "v1");
        feedback.replace(
                1,
                RiskFeedbackRating.GOOD,
                List.of(RiskFeedbackReason.FOUND_RISKS),
                "도움이 됐어요.",
                List.of()
        );
        ReflectionTestUtils.setField(feedback, "id", 7L);
        ReflectionTestUtils.setField(feedback, "createdAt", LocalDateTime.of(2026, 10, 10, 9, 0));
        ReflectionTestUtils.setField(feedback, "updatedAt", LocalDateTime.of(2026, 10, 10, 9, 5));

        Pageable request = PageRequest.of(0, 10);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(riskFeedbackRepository.findAllByUser_Id(org.mockito.ArgumentMatchers.eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(feedback), request, 1));

        PageResponse<AdminRiskFeedbackResponse> response = service.getFeedback(admin, USER_ID, request);

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("7");
            assertThat(item.userId()).isEqualTo("42");
            assertThat(item.rating()).isEqualTo("good");
            assertThat(item.reasons()).containsExactly("found_risks");
            assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
            assertThat(item.updatedAt()).isEqualTo(Instant.parse("2026-10-10T00:05:00Z"));
        });
    }

    @Test
    void nonAdminCannotReadAnotherUsersActivity() {
        AuthUser user = new AuthUser(
                USER_ID,
                "user@example.com",
                AuthProvider.LOCAL,
                true,
                UserRole.USER
        );

        assertThatThrownBy(() -> service.getEstimates(user, USER_ID, PageRequest.of(0, 10)))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ADMIN_ACCESS_FORBIDDEN));

        verify(userRepository, never()).existsById(any());
    }

    @Test
    void missingTargetUserReturnsNotFoundInsteadOfEmptyPage() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getRiskReports(admin, USER_ID, PageRequest.of(0, 10)))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(adminUserActivityMongoRepository, never()).findRiskReportsByUserId(any(), any());
    }

    @Test
    void mongoFailureReturnsServiceUnavailable() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(adminUserActivityMongoRepository.findRiskReportsByUserId(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("mongo unavailable"));

        assertThatThrownBy(() -> service.getRiskReports(admin, USER_ID, PageRequest.of(0, 10)))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ADMIN_ACTIVITY_STORE_UNAVAILABLE));
    }
}
