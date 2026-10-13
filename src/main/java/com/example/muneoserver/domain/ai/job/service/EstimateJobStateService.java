package com.example.muneoserver.domain.ai.job.service;

import com.example.muneoserver.domain.ai.job.domain.EstimateJob;
import com.example.muneoserver.domain.ai.job.repository.EstimateJobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EstimateJobStateService {

    private final EstimateJobRepository estimateJobRepository;

    @Transactional
    public Optional<ExecutionPayload> claim(UUID jobId) {
        return estimateJobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.start(Instant.now()))
                .map(job -> new ExecutionPayload(job.getUserId(), job.getRequestPayload()));
    }

    @Transactional
    public void succeed(UUID jobId, String resultPayload) {
        estimateJobRepository.findByIdForUpdate(jobId)
                .ifPresent(job -> job.succeed(resultPayload, Instant.now()));
    }

    @Transactional
    public void fail(UUID jobId, String errorPayload) {
        estimateJobRepository.findByIdForUpdate(jobId)
                .ifPresent(job -> job.fail(errorPayload, Instant.now()));
    }

    public record ExecutionPayload(Long userId, String requestPayload) {
    }
}
