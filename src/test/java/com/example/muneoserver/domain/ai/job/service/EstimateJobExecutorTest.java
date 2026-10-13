package com.example.muneoserver.domain.ai.job.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.repository.AiApiRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class EstimateJobExecutorTest {

    @Mock
    private EstimateJobStateService estimateJobStateService;

    @Mock
    private AiApiRepository aiApiRepository;

    @Mock
    private ObjectMapper objectMapper;

    private EstimateJobExecutor estimateJobExecutor;

    @BeforeEach
    void setUp() {
        estimateJobExecutor = new EstimateJobExecutor(
                estimateJobStateService,
                aiApiRepository,
                objectMapper
        );
    }

    @Test
    void successfulAiResponseCompletesJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        EstimateGenerateRequest request = org.mockito.Mockito.mock(EstimateGenerateRequest.class);
        Map<String, Object> result = Map.of("estimate_token", "token");
        when(estimateJobStateService.claim(jobId))
                .thenReturn(Optional.of(new EstimateJobStateService.ExecutionPayload(42L, "{}")));
        when(objectMapper.readValue("{}", EstimateGenerateRequest.class)).thenReturn(request);
        when(aiApiRepository.generateEstimate("42", request)).thenReturn(ResponseEntity.ok(result));
        when(objectMapper.writeValueAsString(result)).thenReturn("{\"estimate_token\":\"token\"}");

        estimateJobExecutor.execute(jobId);

        verify(estimateJobStateService).succeed(jobId, "{\"estimate_token\":\"token\"}");
    }

    @Test
    void failedAiResponseFailsJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        EstimateGenerateRequest request = org.mockito.Mockito.mock(EstimateGenerateRequest.class);
        Map<String, Object> error = Map.of("detail", "invalid input");
        when(estimateJobStateService.claim(jobId))
                .thenReturn(Optional.of(new EstimateJobStateService.ExecutionPayload(42L, "{}")));
        when(objectMapper.readValue("{}", EstimateGenerateRequest.class)).thenReturn(request);
        when(aiApiRepository.generateEstimate("42", request))
                .thenReturn(ResponseEntity.unprocessableEntity().body(error));
        when(objectMapper.writeValueAsString(error)).thenReturn("{\"detail\":\"invalid input\"}");

        estimateJobExecutor.execute(jobId);

        verify(estimateJobStateService).fail(jobId, "{\"detail\":\"invalid input\"}");
    }
}
