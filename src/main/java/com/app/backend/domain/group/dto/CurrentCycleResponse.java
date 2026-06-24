package com.app.backend.domain.group.dto;

public record CurrentCycleResponse(
        Long cycleId,
        String topic,
        String status
) {
}
