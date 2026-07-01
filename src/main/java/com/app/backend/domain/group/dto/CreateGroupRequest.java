package com.app.backend.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "모임 이름은 필수입니다.")
        @Size(max = 20, message = "모임 이름은 최대 20자입니다.")
        String name,

        @Size(max = 100, message = "모임 설명은 최대 100자입니다.")
        String description,

        @NotBlank(message = "모임 닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "모임 닉네임은 2~10자입니다.")
        String nickname
) {
}