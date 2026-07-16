package com.app.backend.domain.block.dto;

import jakarta.validation.constraints.NotNull;

public record BlockRequest(
        @NotNull(message = "차단할 유저 id는 필수입니다.") Long userId
) {
}