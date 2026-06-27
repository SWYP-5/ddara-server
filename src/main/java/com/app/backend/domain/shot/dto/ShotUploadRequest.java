package com.app.backend.domain.shot.dto;

import jakarta.validation.constraints.NotBlank;

public record ShotUploadRequest(
        @NotBlank String imageUrl
) {
}