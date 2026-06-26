package com.app.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_OAUTH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 로그인 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token 입니다."),
    UNDER_MIN_AGE(HttpStatus.FORBIDDEN, "만 14세 미만은 가입할 수 없습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "약관에 동의해야 합니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인 제공자입니다."),

    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "초대 코드 생성에 실패했습니다."),
    GROUP_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "모임은 최대 20개까지 참여할 수 있습니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."),
    EXPIRED_INVITE_CODE(HttpStatus.GONE, "만료된 초대 코드입니다."),
    ALREADY_JOINED_GROUP(HttpStatus.CONFLICT, "이미 참여한 모임입니다."),
    GROUP_FULL(HttpStatus.CONFLICT, "모임 정원이 가득 찼습니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "해당 모임의 멤버가 아닙니다."),

    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}