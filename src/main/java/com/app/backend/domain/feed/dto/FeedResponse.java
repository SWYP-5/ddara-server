package com.app.backend.domain.feed.dto;

import com.app.backend.domain.shot.entity.ShotType;

import java.time.OffsetDateTime;
import java.util.List;

public record FeedResponse(
        long updateCount,
        List<FeedItem> items
) {
    public record FeedItem(
            Long shotId,
            ShotType type,
            String imageUrl,
            boolean imageUnderReview,
            Long userId,
            String nickname,
            Long groupId,
            String groupName,
            Long cycleId,
            String topic,
            boolean locked,
            long commentCount,
            List<LatestComment> latestComments,
            OffsetDateTime uploadedAt
    ) {
    }

    public record LatestComment(
            Long userId,
            String nickname,
            String profileImageUrl,
            String content,
            boolean underReview
    ) {
    }
}