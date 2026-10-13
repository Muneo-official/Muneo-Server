package com.example.muneoserver.domain.ai.job.dto;

import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record EstimateJobAcceptedResponse(
        @JsonProperty("job_id") UUID jobId,
        EstimateJobStatus status,
        @JsonProperty("poll_after_ms") long pollAfterMs
) {
}
