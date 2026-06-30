package com.app.backend.domain.user.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 저장소. (1차: EC2 서버 로컬에 저장하고 접근 URL을 반환)
 * shot 이미지(S3 presign)와 별개로 따로 보관한다.
 */
public interface ProfileImageStorage {

    /** 이미지를 저장하고 접근 가능한 URL을 반환한다. */
    String store(MultipartFile file);
}
