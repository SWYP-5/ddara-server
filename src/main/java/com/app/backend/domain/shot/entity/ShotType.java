package com.app.backend.domain.shot.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ShotType {
    STARTER, // 스타터가 올린 가이드 사진
    MEMBER;  // 멤버가 따라 찍은 사진

    // JSON 직렬화 시 소문자로 (starter / member)
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}