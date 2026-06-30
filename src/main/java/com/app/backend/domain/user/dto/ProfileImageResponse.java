package com.app.backend.domain.user.dto;

/** 프로필 이미지 변경(U-02) 응답. imageUrl이 null이면 디폴트 아바타로 초기화된 상태. */
public record ProfileImageResponse(
        String profileImageUrl
) {
}
