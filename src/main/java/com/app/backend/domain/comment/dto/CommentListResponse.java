package com.app.backend.domain.comment.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CommentListResponse(
        List<CommentItem> comments
) {
    public record CommentItem(
            Long commentId,
            Long userId,
            String nickname,
            String profileImageUrl,
            String content,
            boolean underReview,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}