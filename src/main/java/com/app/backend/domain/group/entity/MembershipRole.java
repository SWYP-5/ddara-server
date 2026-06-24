package com.app.backend.domain.group.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MembershipRole {
    OWNER,
    MEMBER;

    // JSON 직렬화 시 소문자로 (owner / member)
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}