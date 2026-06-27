package com.app.backend.domain.shot.dto;

import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;

import java.time.LocalDateTime;

public record ShotResponse(
        Long shotId,
        Long cycleId,
        Long userId,
        ShotType type,
        String imageUrl,
        LocalDateTime uploadedAt
) {
    public static ShotResponse from(Shot shot) {
        return new ShotResponse(
                shot.getId(),
                shot.getCycleId(),
                shot.getUserId(),
                shot.getType(),
                shot.getImageUrl(),
                shot.getUploadedAt()
        );
    }
}