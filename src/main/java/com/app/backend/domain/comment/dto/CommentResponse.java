package com.app.backend.domain.comment.dto;

import java.time.OffsetDateTime;

public record CommentResponse(
        Long commentId,
        Long userId,
        String nickname,
        String profileImageUrl,
        String content,
        OffsetDateTime createdAt
) {
}