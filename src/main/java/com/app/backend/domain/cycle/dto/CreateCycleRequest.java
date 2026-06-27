package com.app.backend.domain.cycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCycleRequest(
        @NotBlank @Size(max = 100) String topic,
        @NotBlank String imageUrl
) {
}