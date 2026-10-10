package com.example.muneoserver.global.error.exception;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부 에러"),
    VALIDATION_FAILED(400, "VALIDATION_FAILED", "요청 값이 올바르지 않습니다."),
    INVALID_PARAMETER_TYPE(400, "INVALID_PARAMETER_TYPE", "적절하지 않은 파라미터 타입입니다."),
    INVALID_REQUEST_FORMAT(400, "INVALID_REQUEST_FORMAT", "올바르지 않은 요청 형식입니다."),
    DATA_INTEGRITY_VIOLATION(400, "DATA_INTEGRITY_VIOLATION", "데이터 무결성 오류가 발생했습니다."),
    UNPROCESSABLE_ENTITY(422, "UNPROCESSABLE_ENTITY", "요청 내용을 처리할 수 없습니다."),
    UNAUTHORIZED(401, "UNAUTHORIZED", "인증이 필요합니다."),
    RISK_REPORT_ACCESS_FORBIDDEN(403, "RISK_REPORT_ACCESS_FORBIDDEN", "해당 리스크 보고서에 접근할 권한이 없습니다."),
    RISK_REPORT_NOT_FOUND(404, "RISK_REPORT_NOT_FOUND", "리스크 보고서를 찾을 수 없습니다."),
    RISK_FEEDBACK_CONFLICT(409, "RISK_FEEDBACK_CONFLICT", "피드백이 동시에 변경되었습니다. 다시 시도해 주세요."),
    RISK_REPORT_STORE_UNAVAILABLE(503, "RISK_REPORT_STORE_UNAVAILABLE", "리스크 보고서 저장소를 일시적으로 사용할 수 없습니다."),
    ADMIN_ACTIVITY_STORE_UNAVAILABLE(503, "ADMIN_ACTIVITY_STORE_UNAVAILABLE", "사용자 이용 내역 저장소를 일시적으로 사용할 수 없습니다."),
    MEMBER_ALREADY_EXISTS(409, "MEMBER_ALREADY_EXISTS", "이미 가입된 이메일입니다."),
    INVALID_LOGIN_INFO(401, "INVALID_LOGIN_INFO", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(401, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(401, "REFRESH_TOKEN_NOT_FOUND", "만료되었거나 로그아웃된 토큰입니다."),
    REFRESH_TOKEN_MISMATCH(401, "REFRESH_TOKEN_MISMATCH", "리프레시 토큰이 일치하지 않습니다."),
    ADMIN_LOGIN_FORBIDDEN(403, "ADMIN_LOGIN_FORBIDDEN", "관리자 계정만 로그인할 수 있습니다."),
    ADMIN_ACCESS_FORBIDDEN(403, "ADMIN_ACCESS_FORBIDDEN", "관리자 권한이 필요합니다."),
    ADMIN_SELF_ACTION_FORBIDDEN(403, "ADMIN_SELF_ACTION_FORBIDDEN", "관리자 본인 계정에는 수행할 수 없는 작업입니다."),
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_ROLE_ALREADY_ASSIGNED(409, "USER_ROLE_ALREADY_ASSIGNED", "이미 해당 권한을 가진 사용자입니다."),
    USER_ALREADY_DELETED(409, "USER_ALREADY_DELETED", "이미 탈퇴 처리된 사용자입니다."),
    USER_NOT_DELETED(409, "USER_NOT_DELETED", "탈퇴 처리된 사용자가 아닙니다."),
    SOCIAL_SIGNUP_TICKET_EXPIRED(401, "SOCIAL_SIGNUP_TICKET_EXPIRED", "소셜 회원가입 티켓이 만료되었습니다."),
    SOCIAL_PROVIDER_MISMATCH(400, "SOCIAL_PROVIDER_MISMATCH", "소셜 로그인 제공자가 일치하지 않습니다."),
    UNSUPPORTED_SOCIAL_PROVIDER(400, "UNSUPPORTED_SOCIAL_PROVIDER", "지원하지 않는 소셜 로그인 제공자입니다."),
    SOCIAL_USER_PROFILE_RESTRICTION(400, "SOCIAL_USER_PROFILE_RESTRICTION", "소셜 회원은 일반 회원 정보 수정 API를 사용할 수 없습니다."),
    LOCAL_USER_PROFILE_RESTRICTION(400, "LOCAL_USER_PROFILE_RESTRICTION", "일반 회원은 소셜 회원 정보 수정 API를 사용할 수 없습니다."),
    EMAIL_FEATURE_DISABLED(503, "EMAIL_FEATURE_DISABLED", "현재 이메일 기능이 비활성화되어 있습니다."),
    EMAIL_VERIFICATION_CODE_EXPIRED(400, "EMAIL_VERIFICATION_CODE_EXPIRED", "이메일 인증 코드가 만료되었습니다."),
    EMAIL_VERIFICATION_CODE_MISMATCH(400, "EMAIL_VERIFICATION_CODE_MISMATCH", "이메일 인증 코드가 일치하지 않습니다."),
    PASSWORD_RESET_CODE_EXPIRED(400, "PASSWORD_RESET_CODE_EXPIRED", "비밀번호 재설정 코드가 만료되었습니다."),
    PASSWORD_RESET_CODE_MISMATCH(400, "PASSWORD_RESET_CODE_MISMATCH", "비밀번호 재설정 코드가 일치하지 않습니다."),
    INVALID_FILE_UPLOAD_TYPE(400, "INVALID_FILE_UPLOAD_TYPE", "지원하지 않는 파일 업로드 타입입니다."),
    FILE_UPLOAD_CONFIGURATION_INVALID(500, "FILE_UPLOAD_CONFIGURATION_INVALID", "파일 업로드 설정이 올바르지 않습니다."),
    FILE_UPLOAD_PRESIGNED_URL_GENERATION_FAILED(500, "FILE_UPLOAD_PRESIGNED_URL_GENERATION_FAILED", "Presigned URL 생성에 실패했습니다.");

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
