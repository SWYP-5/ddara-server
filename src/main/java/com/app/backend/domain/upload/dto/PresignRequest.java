package com.app.backend.domain.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignRequest(
        @NotBlank @Pattern(regexp = "shot|profile", message = "purpose는 shot 또는 profile이어야 합니다.")
        String purpose,
        @NotBlank String contentType
) {
}