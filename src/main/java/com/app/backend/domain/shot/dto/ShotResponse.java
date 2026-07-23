package com.app.backend.domain.shot.dto;

import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;

import com.app.backend.global.util.KstTime;
import java.time.OffsetDateTime;

public record ShotResponse(
        Long shotId,
        Long cycleId,
        Long userId,
        ShotType type,
        String imageUrl,
        OffsetDateTime uploadedAt
) {
    public static ShotResponse from(Shot shot) {
        return new ShotResponse(
                shot.getId(),
                shot.getCycleId(),
                shot.getUserId(),
                shot.getType(),
                shot.getImageUrl(),
                KstTime.toOffset(shot.getUploadedAt())
        );
    }
}