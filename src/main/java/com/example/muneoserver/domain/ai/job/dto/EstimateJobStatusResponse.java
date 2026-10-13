package com.example.muneoserver.domain.ai.job.dto;

import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstimateJobStatusResponse(
        @JsonProperty("job_id") UUID jobId,
        EstimateJobStatus status,
        @JsonProperty("poll_after_ms") Long pollAfterMs,
        Object result,
        Object error,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt
) {
}
