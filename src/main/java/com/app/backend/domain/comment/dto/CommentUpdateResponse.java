package com.app.backend.domain.comment.dto;

import java.time.OffsetDateTime;

public record CommentUpdateResponse(
        Long commentId,
        String content,
        OffsetDateTime updatedAt
) {
}