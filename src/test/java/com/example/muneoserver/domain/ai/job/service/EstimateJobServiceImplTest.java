package com.example.muneoserver.domain.ai.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.job.domain.EstimateJob;
import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobAcceptedResponse;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobStatusResponse;
import com.example.muneoserver.domain.ai.job.repository.EstimateJobRepository;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstimateJobServiceImplTest {

    @Mock
    private EstimateJobRepository estimateJobRepository;

    @Mock
    private EstimateJobDispatcher estimateJobDispatcher;

    @Mock
    private ObjectMapper objectMapper;

    private EstimateJobServiceImpl estimateJobService;

    private final AuthUser authUser = new AuthUser(
            42L,
            "user@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.USER
    );

    @BeforeEach
    void setUp() {
        estimateJobService = new EstimateJobServiceImpl(
                estimateJobRepository,
                estimateJobDispatcher,
                objectMapper
        );
    }

    @Test
    void submitPersistsPendingJobAndDispatchesIt() throws Exception {
        EstimateGenerateRequest request = mock(EstimateGenerateRequest.class);
        when(objectMapper.writeValueAsString(request)).thenReturn("{\"평수\":30}");
        when(estimateJobRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(EstimateJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EstimateJobAcceptedResponse response = estimateJobService.submit(authUser, request);

        assertThat(response.jobId()).isNotNull();
        assertThat(response.status()).isEqualTo(EstimateJobStatus.PENDING);
        assertThat(response.pollAfterMs()).isEqualTo(3_000L);
        verify(estimateJobDispatcher).dispatch(response.jobId());
    }

    @Test
    void submitRejectsMissingAuthentication() {
        EstimateGenerateRequest request = mock(EstimateGenerateRequest.class);

        assertThatThrownBy(() -> estimateJobService.submit(null, request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(estimateJobRepository, estimateJobDispatcher, objectMapper);
    }

    @Test
    void getReturnsOnlyOwnersJob() throws Exception {
        EstimateJob job = EstimateJob.pending(42L, "{}");
        UUID jobId = job.getId();
        when(estimateJobRepository.findByIdAndUserId(jobId, 42L)).thenReturn(Optional.of(job));

        EstimateJobStatusResponse response = estimateJobService.get(authUser, jobId);

        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(EstimateJobStatus.PENDING);
        assertThat(response.pollAfterMs()).isEqualTo(3_000L);
        assertThat(response.result()).isNull();
        assertThat(response.error()).isNull();
    }

    @Test
    void getHidesUnknownOrOtherUsersJobAsNotFound() {
        UUID jobId = UUID.randomUUID();
        when(estimateJobRepository.findByIdAndUserId(jobId, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estimateJobService.get(authUser, jobId))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ESTIMATE_JOB_NOT_FOUND));
    }
}
