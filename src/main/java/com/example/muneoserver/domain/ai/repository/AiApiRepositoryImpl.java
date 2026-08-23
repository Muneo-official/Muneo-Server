package com.example.muneoserver.domain.ai.repository;

import com.example.muneoserver.domain.ai.config.AiProperties;
import com.example.muneoserver.domain.ai.dto.ChatRequest;
import com.example.muneoserver.domain.ai.dto.EstimateGenerateRequest;
import com.example.muneoserver.domain.ai.dto.EstimateSaveRequest;
import com.example.muneoserver.domain.ai.dto.RiskAnalyzeRequest;
import com.example.muneoserver.domain.ai.dto.RiskReportSaveRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Repository
@RequiredArgsConstructor
public class AiApiRepositoryImpl implements AiApiRepository {

    private final RestTemplate aiRestTemplate;
    private final AiProperties aiProperties;

    @Override
    public ResponseEntity<Object> generateEstimate(String userId, EstimateGenerateRequest request) {
        return exchange(HttpMethod.POST, "/estimates/generate", userId, request, MediaType.APPLICATION_JSON);
    }

    @Override
    public ResponseEntity<Object> saveEstimate(String userId, EstimateSaveRequest request) {
        return exchange(HttpMethod.POST, "/estimates/save", userId, request, MediaType.APPLICATION_JSON);
    }

    @Override
    public ResponseEntity<Object> getEstimates(String userId) {
        return exchange(HttpMethod.GET, "/estimates", userId, null, null);
    }

    @Override
    public ResponseEntity<Object> deleteEstimate(String userId, String estimateId) {
        return exchange(HttpMethod.DELETE, "/estimates/" + estimateId, userId, null, null);
    }

    @Override
    public ResponseEntity<Object> chat(ChatRequest request) {
        return exchange(HttpMethod.POST, "/chatbot/chat", null, request, MediaType.APPLICATION_JSON);
    }

    @Override
    public ResponseEntity<Object> analyzeRisk(RiskAnalyzeRequest request) {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("space_type", request.spaceType());
        formData.add("pyeong", String.valueOf(request.pyeong()));
        formData.add("room_count", String.valueOf(request.roomCount()));
        formData.add("floor", String.valueOf(request.floor()));
        formData.add("elevator", String.valueOf(request.elevator()));
        formData.add("region", request.region());
        formData.add("building_age", request.buildingAge());
        formData.add("company_name", request.companyName());

        for (var file : request.files()) {
            try {
                formData.add("files", new NamedByteArrayResource(file.getBytes(), file.getOriginalFilename()));
            } catch (IOException e) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("detail", "파일 읽기에 실패했습니다.");
                return ResponseEntity.internalServerError().body(error);
            }
        }

        return exchange(HttpMethod.POST, "/risk-detector/analyze", null, formData, MediaType.MULTIPART_FORM_DATA);
    }

    @Override
    public ResponseEntity<Object> saveRiskReport(String userId, RiskReportSaveRequest request) {
        return exchange(HttpMethod.POST, "/risk-detector/save", userId, request, MediaType.APPLICATION_JSON);
    }

    @Override
    public ResponseEntity<Object> getRiskReports(String userId) {
        return exchange(HttpMethod.GET, "/risk-detector", userId, null, null);
    }

    @Override
    public ResponseEntity<Object> deleteRiskReport(String userId, String reportId) {
        return exchange(HttpMethod.DELETE, "/risk-detector/" + reportId, userId, null, null);
    }

    private ResponseEntity<Object> exchange(
            HttpMethod method,
            String path,
            String userId,
            Object body,
            MediaType contentType
    ) {
        String url = aiProperties.baseUrl() + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        if (contentType != null) {
            headers.setContentType(contentType);
        }

        if (userId != null && !userId.isBlank()) {
            headers.set("x-user-id", userId);
        }

        HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);

        try {
            return aiRestTemplate.exchange(url, method, requestEntity, Object.class);
        } catch (RestClientException e) {
            if (e instanceof org.springframework.web.client.HttpStatusCodeException ex) {
                return ResponseEntity.status(ex.getStatusCode()).body(parseErrorBody(ex.getResponseBodyAsString()));
            }

            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("detail", "AI 서버 호출에 실패했습니다.");
            return ResponseEntity.internalServerError().body(fallback);
        }
    }

    private Object parseErrorBody(String body) {
        if (body == null || body.isBlank()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("detail", "AI 서버 오류");
            return fallback;
        }

        try {
            return JsonParserFactory.getJsonParser().parseMap(body);
        } catch (Exception ignore) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("detail", body);
            return fallback;
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray);
            this.fileName = fileName == null || fileName.isBlank() ? "file" : fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
