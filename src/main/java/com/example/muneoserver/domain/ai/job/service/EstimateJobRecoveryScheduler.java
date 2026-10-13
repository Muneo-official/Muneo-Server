package com.example.muneoserver.domain.ai.job.service;

import com.example.muneoserver.domain.ai.config.AiProperties;
import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import com.example.muneoserver.domain.ai.job.repository.EstimateJobRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EstimateJobRecoveryScheduler {

    private static final int RECOVERY_BATCH_SIZE = 100;
    private static final long STALE_MARGIN_MS = 60_000L;
    private static final String INTERRUPTED_ERROR = """
            {"code":"ESTIMATE_GENERATION_INTERRUPTED","message":"서버 처리 중단으로 견적 생성에 실패했습니다. 다시 요청해 주세요."}
            """;

    private final EstimateJobRepository estimateJobRepository;
    private final EstimateJobDispatcher estimateJobDispatcher;
    private final EstimateJobStateService estimateJobStateService;
    private final AiProperties aiProperties;

    @Scheduled(initialDelay = 30_000L, fixedDelay = 30_000L)
    public void redispatchPendingJobs() {
        for (UUID jobId : estimateJobRepository.findIdsByStatus(
                EstimateJobStatus.PENDING,
                PageRequest.of(0, RECOVERY_BATCH_SIZE)
        )) {
            estimateJobDispatcher.dispatch(jobId);
        }
    }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 60_000L)
    public void failStaleProcessingJobs() {
        long staleAfterMs = Math.max(60_000L, (long) aiProperties.readTimeoutMs() + STALE_MARGIN_MS);
        Instant cutoff = Instant.now().minusMillis(staleAfterMs);
        for (UUID jobId : estimateJobRepository.findIdsByStatusAndStartedBefore(
                EstimateJobStatus.PROCESSING,
                cutoff,
                PageRequest.of(0, RECOVERY_BATCH_SIZE)
        )) {
            estimateJobStateService.fail(jobId, INTERRUPTED_ERROR.strip());
        }
    }
}
