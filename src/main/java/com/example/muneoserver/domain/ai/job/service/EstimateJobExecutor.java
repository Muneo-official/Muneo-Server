package com.example.muneoserver.domain.ai.job.service;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.repository.AiApiRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstimateJobExecutor {

    private final EstimateJobStateService estimateJobStateService;
    private final AiApiRepository aiApiRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void execute(UUID jobId) {
        estimateJobStateService.claim(jobId).ifPresent(payload -> executeClaimed(jobId, payload));
    }

    private void executeClaimed(UUID jobId, EstimateJobStateService.ExecutionPayload payload) {
        try {
            EstimateGenerateRequest request = objectMapper.readValue(
                    payload.requestPayload(),
                    EstimateGenerateRequest.class
            );
            ResponseEntity<Object> response = aiApiRepository.generateEstimate(
                    String.valueOf(payload.userId()),
                    request
            );

            String responsePayload = objectMapper.writeValueAsString(response.getBody());
            if (response.getStatusCode().is2xxSuccessful()) {
                estimateJobStateService.succeed(jobId, responsePayload);
                return;
            }

            estimateJobStateService.fail(jobId, responsePayload);
            log.warn("Estimate job failed. jobId={}, aiStatus={}", jobId, response.getStatusCode().value());
        } catch (Exception e) {
            log.error("Estimate job execution failed. jobId={}", jobId, e);
            estimateJobStateService.fail(jobId, writeFallbackError());
        }
    }

    private String writeFallbackError() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "code", "ESTIMATE_GENERATION_FAILED",
                    "message", "견적 생성에 실패했습니다."
            ));
        } catch (Exception ignored) {
            return "{\"code\":\"ESTIMATE_GENERATION_FAILED\",\"message\":\"견적 생성에 실패했습니다.\"}";
        }
    }
}
