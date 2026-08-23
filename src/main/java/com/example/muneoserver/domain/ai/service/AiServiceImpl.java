package com.example.muneoserver.domain.ai.service;

import com.example.muneoserver.domain.ai.dto.ChatRequest;
import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.dto.EstimateSaveRequest;
import com.example.muneoserver.domain.ai.dto.RiskAnalyzeRequest;
import com.example.muneoserver.domain.ai.dto.RiskReportSaveRequest;
import com.example.muneoserver.domain.ai.repository.AiApiRepository;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiApiRepository aiApiRepository;

    @Override
    public ResponseEntity<Object> generateEstimate(AuthUser authUser, EstimateGenerateRequest request) {
        return aiApiRepository.generateEstimate(requireAuthUser(authUser), request);
    }

    @Override
    public ResponseEntity<Object> saveEstimate(AuthUser authUser, EstimateSaveRequest request) {
        return aiApiRepository.saveEstimate(requireAuthUser(authUser), request);
    }

    @Override
    public ResponseEntity<Object> getEstimates(AuthUser authUser) {
        return aiApiRepository.getEstimates(requireAuthUser(authUser));
    }

    @Override
    public ResponseEntity<Object> deleteEstimate(AuthUser authUser, String estimateId) {
        return aiApiRepository.deleteEstimate(requireAuthUser(authUser), estimateId);
    }

    @Override
    public ResponseEntity<Object> chat(ChatRequest request) {
        return aiApiRepository.chat(request);
    }

    @Override
    public ResponseEntity<Object> analyzeRisk(RiskAnalyzeRequest request) {
        return aiApiRepository.analyzeRisk(request);
    }

    @Override
    public ResponseEntity<Object> saveRiskReport(AuthUser authUser, RiskReportSaveRequest request) {
        return aiApiRepository.saveRiskReport(requireAuthUser(authUser), request);
    }

    @Override
    public ResponseEntity<Object> getRiskReports(AuthUser authUser) {
        return aiApiRepository.getRiskReports(requireAuthUser(authUser));
    }

    @Override
    public ResponseEntity<Object> deleteRiskReport(AuthUser authUser, String reportId) {
        return aiApiRepository.deleteRiskReport(requireAuthUser(authUser), reportId);
    }

    private String requireAuthUser(AuthUser authUser) {
        if (authUser == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }
        return String.valueOf(authUser.id());
    }
}
