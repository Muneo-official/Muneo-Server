package com.example.muneoserver.global.error.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.muneoserver.global.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void oversizedUploadUsesCommonErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUploadSize(
                new MaxUploadSizeExceededException(21L * 1024 * 1024)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().code()).isEqualTo("FILE_UPLOAD_SIZE_EXCEEDED");
    }

    @Test
    void missingMultipartPartUsesValidationErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingPart(
                new MissingServletRequestPartException("files")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error()).containsEntry("files", "필수 요청 항목입니다.");
    }
}
