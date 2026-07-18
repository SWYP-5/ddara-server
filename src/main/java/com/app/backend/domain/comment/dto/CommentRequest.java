package com.app.backend.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "코멘트 내용은 필수입니다.")
        @Size(max = 200, message = "코멘트는 최대 200자입니다.")
        String content
) {
}