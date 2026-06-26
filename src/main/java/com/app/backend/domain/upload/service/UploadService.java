package com.app.backend.domain.upload.service;

import com.app.backend.domain.upload.dto.PresignRequest;
import com.app.backend.domain.upload.dto.PresignResponse;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
            "profile", "profiles"
    );

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String region;

    public UploadService(S3Presigner s3Presigner,
                         @Value("${aws.s3.bucket}") String bucket,
                         @Value("${aws.s3.region}") String region) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.region = region;
    }

    public PresignResponse createPresignedUrl(PresignRequest request) {
        String ext = CONTENT_TYPE_TO_EXT.get(request.contentType());
        if (ext == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        // 충돌 없는 고유 key (uploads/{uuid}.{ext})
        String dir = PURPOSE_TO_DIR.get(request.purpose());
        String key = dir + "/" + UUID.randomUUID() + "." + ext;

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
        String imageUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

        return new PresignResponse(uploadUrl, imageUrl, (int) PRESIGN_EXPIRY.getSeconds());
    }
}
