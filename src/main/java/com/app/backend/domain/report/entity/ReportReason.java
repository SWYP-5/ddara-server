package com.app.backend.domain.report.entity;

public enum ReportReason {
    // 사진(SHOT)
    OBSCENE,
    VIOLENCE,
    UNAUTHORIZED_PHOTO,
    HARASSMENT,
    // 코멘트(COMMENT)
    ABUSE,
    SEXUAL,
    HATE,
    IMPERSONATION,
    PRIVACY,
    // 유저(USER)
    INAPPROPRIATE_NICKNAME,
    INAPPROPRIATE_IMAGE,
    // 공통
    ETC
}