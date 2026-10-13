package com.example.muneoserver.global.dto;

import com.example.muneoserver.global.error.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String code,
        Instant timestamp,
        String message,
        T result,
        Map<String, String> error
) {

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(
                true,
                200,
                "SUCCESS",
                Instant.now(),
                "요청에 성공했습니다.",
                result,
                null
        );
    }

    public static <T> ApiResponse<T> success(T result, String message) {
        return new ApiResponse<>(
                true,
                200,
                "SUCCESS",
                Instant.now(),
                message,
                result,
                null
        );
    }

    public static <T> ApiResponse<T> created(T result, String message) {
        return new ApiResponse<>(
                true,
                201,
                "CREATED",
                Instant.now(),
                message,
                result,
                null
        );
    }

    public static <T> ApiResponse<T> accepted(T result, String message) {
        return new ApiResponse<>(
                true,
                202,
                "ACCEPTED",
                Instant.now(),
                message,
                result,
                null
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(
                false,
                errorCode.status(),
                errorCode.code(),
                Instant.now(),
                errorCode.message(),
                null,
                null
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(
                false,
                errorCode.status(),
                errorCode.code(),
                Instant.now(),
                message,
                null,
                null
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, Map<String, String> errors) {
        return new ApiResponse<>(
                false,
                errorCode.status(),
                errorCode.code(),
                Instant.now(),
                errorCode.message(),
                null,
                errors
        );
    }
}
