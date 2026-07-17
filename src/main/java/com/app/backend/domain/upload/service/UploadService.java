package com.app.backend.domain.upload.service;

import com.app.backend.domain.upload.dto.PresignRequest;
import com.app.backend.domain.upload.dto.PresignResponse;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class UploadService {

    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(5);

    private static final Map<String, String> CONTENT_TYPE_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    // purpose → S3 폴더 경로
    private static final Map<String, String> PURPOSE_TO_DIR = Map.of(
            "shot", "shots",
            "profile", "profiles",
            "asset", "assets"
    );

    // 고정 자산(앱 로고)은 랜덤 UUID가 아니라 알림 코드(#87)가 참조하는 고정 key로 업로드한다.
    private static final String ASSET_PURPOSE = "asset";
    private static final String LOGO_KEY = "assets/ddara-logo.png";

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucket;
    private final String region;
    private final String imageUrlPrefix;

    public UploadService(S3Presigner s3Presigner,
                         S3Client s3Client,
                         @Value("${aws.s3.bucket}") String bucket,
                         @Value("${aws.s3.region}") String region) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
        this.imageUrlPrefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }

    public PresignResponse createPresignedUrl(PresignRequest request) {
        String ext = CONTENT_TYPE_TO_EXT.get(request.contentType());
        if (ext == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        // asset(앱 로고)은 고정 key, 그 외(shot/profile)는 충돌 없는 고유 key({dir}/{uuid}.{ext})
        String dir = PURPOSE_TO_DIR.get(request.purpose());
        String key = ASSET_PURPOSE.equals(request.purpose())
                ? LOGO_KEY
                : dir + "/" + UUID.randomUUID() + "." + ext;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_EXPIRY)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String uploadUrl = presigned.url().toString();
        String imageUrl = imageUrlPrefix + key;

        return new PresignResponse(uploadUrl, imageUrl, (int) PRESIGN_EXPIRY.getSeconds());
    }

    public boolean deleteImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(imageUrlPrefix)) {
            return true;
        }
        String key = imageUrl.substring(imageUrlPrefix.length());
        if (key.startsWith(PURPOSE_TO_DIR.get(ASSET_PURPOSE) + "/")) {
            return true;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("S3 이미지 삭제 실패: {}", imageUrl, e);
            return false;
        }
    }
}
