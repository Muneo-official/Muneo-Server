package com.example.muneoserver.domain.ai.service;

import com.example.muneoserver.domain.ai.dto.ChatRequest;
import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.dto.EstimateSaveRequest;
import com.example.muneoserver.domain.ai.dto.RiskAnalyzeRequest;
import com.example.muneoserver.domain.ai.dto.RiskReportSaveRequest;
import com.example.muneoserver.global.security.auth.AuthUser;
import org.springframework.http.ResponseEntity;

public interface AiService {

    ResponseEntity<Object> generateEstimate(AuthUser authUser, EstimateGenerateRequest request);

    ResponseEntity<Object> saveEstimate(AuthUser authUser, EstimateSaveRequest request);

    ResponseEntity<Object> getEstimates(AuthUser authUser);

    ResponseEntity<Object> deleteEstimate(AuthUser authUser, String estimateId);

    ResponseEntity<Object> chat(ChatRequest request);

    ResponseEntity<Object> analyzeRisk(RiskAnalyzeRequest request);

    ResponseEntity<Object> saveRiskReport(AuthUser authUser, RiskReportSaveRequest request);

    ResponseEntity<Object> getRiskReports(AuthUser authUser);

    ResponseEntity<Object> deleteRiskReport(AuthUser authUser, String reportId);
}
