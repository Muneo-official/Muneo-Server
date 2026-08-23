package com.example.muneoserver.domain.ai.controller;

import com.example.muneoserver.domain.ai.dto.ChatRequest;
import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.dto.EstimateSaveRequest;
import com.example.muneoserver.domain.ai.dto.RiskAnalyzeRequest;
import com.example.muneoserver.domain.ai.dto.RiskReportSaveRequest;
import com.example.muneoserver.domain.ai.service.AiService;
import com.example.muneoserver.global.security.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/estimates/generate")
    public ResponseEntity<Object> generateEstimate(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody EstimateGenerateRequest request
    ) {
        return aiService.generateEstimate(authUser, request);
    }

    @PostMapping("/estimates/save")
    public ResponseEntity<Object> saveEstimate(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody EstimateSaveRequest request
    ) {
        return aiService.saveEstimate(authUser, request);
    }

    @GetMapping("/estimates")
    public ResponseEntity<Object> getEstimates(@AuthenticationPrincipal AuthUser authUser) {
        return aiService.getEstimates(authUser);
    }

    @DeleteMapping("/estimates/{id}")
    public ResponseEntity<Object> deleteEstimate(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable("id") String id
    ) {
        return aiService.deleteEstimate(authUser, id);
    }

    @PostMapping("/chatbot/chat")
    public ResponseEntity<Object> chat(@Valid @RequestBody ChatRequest request) {
        return aiService.chat(request);
    }

    @PostMapping(value = "/risk-detector/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> analyzeRisk(
            @RequestParam("space_type") @NotBlank(message = "space_type은 필수입니다.") String spaceType,
            @RequestParam("pyeong") @NotNull(message = "pyeong은 필수입니다.") @Min(value = 1, message = "pyeong은 1 이상이어야 합니다.") Integer pyeong,
            @RequestParam("room_count") @NotNull(message = "room_count는 필수입니다.") @Min(value = 0, message = "room_count는 0 이상이어야 합니다.") Integer roomCount,
            @RequestParam("floor") @NotNull(message = "floor는 필수입니다.") Integer floor,
            @RequestParam("elevator") @NotNull(message = "elevator는 필수입니다.") Boolean elevator,
            @RequestParam("region") @NotBlank(message = "region은 필수입니다.") String region,
            @RequestParam("building_age") @NotBlank(message = "building_age는 필수입니다.") String buildingAge,
            @RequestParam("company_name") @NotBlank(message = "company_name은 필수입니다.") String companyName,
            @RequestParam("files") MultipartFile[] files
    ) {
        RiskAnalyzeRequest request = new RiskAnalyzeRequest(
                spaceType,
                pyeong,
                roomCount,
                floor,
                elevator,
                region,
                buildingAge,
                companyName,
                files
        );
        return aiService.analyzeRisk(request);
    }

    @PostMapping("/risk-detector/save")
    public ResponseEntity<Object> saveRiskReport(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody RiskReportSaveRequest request
    ) {
        return aiService.saveRiskReport(authUser, request);
    }

    @GetMapping("/risk-detector")
    public ResponseEntity<Object> getRiskReports(@AuthenticationPrincipal AuthUser authUser) {
        return aiService.getRiskReports(authUser);
    }

    @DeleteMapping("/risk-detector/{reportId}")
    public ResponseEntity<Object> deleteRiskReport(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable("reportId") String reportId
    ) {
        return aiService.deleteRiskReport(authUser, reportId);
    }
}
