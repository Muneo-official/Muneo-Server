package com.example.muneoserver.domain.ai.job.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EstimateJobDispatcher {

    private final EstimateJobExecutor estimateJobExecutor;

    public void dispatch(UUID jobId) {
        estimateJobExecutor.execute(jobId);
    }
}
