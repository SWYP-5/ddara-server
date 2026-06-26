package com.app.backend.domain.upload.dto;

public record PresignResponse(
        String uploadUrl,
        String imageUrl,
        int expiresIn
) {
}