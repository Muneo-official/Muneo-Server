package com.example.muneoserver.domain.ai.job.service;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.job.domain.EstimateJob;
import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobAcceptedResponse;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobStatusResponse;
import com.example.muneoserver.domain.ai.job.repository.EstimateJobRepository;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EstimateJobServiceImpl implements EstimateJobService {

    private static final long POLL_AFTER_MS = 3_000L;

    private final EstimateJobRepository estimateJobRepository;
    private final EstimateJobDispatcher estimateJobDispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public EstimateJobAcceptedResponse submit(AuthUser authUser, EstimateGenerateRequest request) {
        Long userId = requireAuthUser(authUser);
        EstimateJob job = EstimateJob.pending(userId, writeJson(request));
        estimateJobRepository.saveAndFlush(job);
        estimateJobDispatcher.dispatch(job.getId());
        return new EstimateJobAcceptedResponse(job.getId(), job.getStatus(), POLL_AFTER_MS);
    }

    @Override
    public EstimateJobStatusResponse get(AuthUser authUser, UUID jobId) {
        Long userId = requireAuthUser(authUser);
        EstimateJob job = estimateJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new CommonException(ErrorCode.ESTIMATE_JOB_NOT_FOUND));

        Long pollAfterMs = isTerminal(job.getStatus()) ? null : POLL_AFTER_MS;
        return new EstimateJobStatusResponse(
                job.getId(),
                job.getStatus(),
                pollAfterMs,
                readJson(job.getResultPayload()),
                readJson(job.getErrorPayload()),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }

    private Long requireAuthUser(AuthUser authUser) {
        if (authUser == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.id();
    }

    private boolean isTerminal(EstimateJobStatus status) {
        return status == EstimateJobStatus.SUCCEEDED || status == EstimateJobStatus.FAILED;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private Object readJson(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
