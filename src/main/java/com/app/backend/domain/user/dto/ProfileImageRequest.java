package com.app.backend.domain.user.dto;

/**
 * 프로필 이미지 변경(U-02) 요청.
 * presign으로 S3에 업로드한 이미지의 접근 URL을 등록한다.
 * imageUrl이 null/blank이면 기본 아바타로 초기화.
 */
public record ProfileImageRequest(
        String imageUrl
) {
}
