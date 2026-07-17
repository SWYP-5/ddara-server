package com.app.backend.domain.cycle.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CycleStatus {
    IN_PROGRESS,
    DONE,
    REMOVED;

    // JSON 직렬화 시 소문자로 (in_progress / done)
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}