package com.app.backend.domain.user.service;

import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/** 프로필 이미지를 EC2 서버 로컬 디렉터리에 저장하고 접근 URL을 반환한다. (U-02) */
@Component
public class LocalProfileImageStorage implements ProfileImageStorage {

    // 정적 서빙 경로(/images/**) 하위의 프로필 폴더
    private static final String PROFILE_SUBDIR = "profiles";

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    private final String uploadDir;
    private final String baseUrl;

    public LocalProfileImageStorage(@Value("${app.upload.dir}") String uploadDir,
                                    @Value("${app.upload.base-url}") String baseUrl) {
        this.uploadDir = uploadDir;
        this.baseUrl = baseUrl;
    }

    @Override
    public String store(MultipartFile file) {
        String ext = EXT_BY_TYPE.get(file.getContentType());
        if (ext == null) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        String fileName = UUID.randomUUID() + "." + ext;
        try {
            Path dir = Paths.get(uploadDir, PROFILE_SUBDIR);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName));
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        return baseUrl + "/images/" + PROFILE_SUBDIR + "/" + fileName;
    }
}
