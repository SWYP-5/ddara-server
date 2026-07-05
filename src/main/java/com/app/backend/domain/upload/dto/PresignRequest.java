package com.app.backend.domain.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignRequest(
        @NotBlank @Pattern(regexp = "shot|profile|asset", message = "purpose는 shot, profile, asset 중 하나여야 합니다.")
        String purpose,
        @NotBlank String contentType
) {
}