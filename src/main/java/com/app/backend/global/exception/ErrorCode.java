package com.app.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_OAUTH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 로그인 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token 입니다."),
    UNDER_MIN_AGE(HttpStatus.FORBIDDEN, "만 14세 미만은 가입할 수 없습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "약관에 동의해야 합니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인 제공자입니다."),

    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "초대 코드 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}