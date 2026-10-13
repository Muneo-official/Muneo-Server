package com.example.muneoserver.domain.ai.job.service;

import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobAcceptedResponse;
import com.example.muneoserver.domain.ai.job.dto.EstimateJobStatusResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.util.UUID;

public interface EstimateJobService {

    EstimateJobAcceptedResponse submit(AuthUser authUser, EstimateGenerateRequest request);

    EstimateJobStatusResponse get(AuthUser authUser, UUID jobId);
}
