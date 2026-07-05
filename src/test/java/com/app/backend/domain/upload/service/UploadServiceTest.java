package com.app.backend.domain.upload.service;

import com.app.backend.domain.upload.dto.PresignRequest;
import com.app.backend.domain.upload.dto.PresignResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presigned;

    private UploadService uploadService;

    @BeforeEach
    void setUp() throws Exception {
        uploadService = new UploadService(s3Presigner, "ddara-images", "ap-northeast-2");
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presigned);
        given(presigned.url()).willReturn(URI.create("https://upload-url.example").toURL());
    }

    @Test
    void asset_presign은_로고_고정경로로_imageUrl을_반환한다() {
        // when: 앱 로고 업로드용 presign 발급
        PresignResponse res = uploadService.createPresignedUrl(new PresignRequest("asset", "image/png"));

        // then: 알림 코드(#87)가 참조하는 고정 경로와 정확히 일치 (랜덤 UUID 아님)
        assertThat(res.imageUrl())
                .isEqualTo("https://ddara-images.s3.ap-northeast-2.amazonaws.com/assets/ddara-logo.png");
        assertThat(res.uploadUrl()).isEqualTo("https://upload-url.example");
    }

    @Test
    void profile_presign은_기존대로_랜덤_UUID_key를_쓴다() {
        // when
        PresignResponse res = uploadService.createPresignedUrl(new PresignRequest("profile", "image/jpeg"));

        // then: profiles/ 폴더 + 랜덤 파일명 (고정 아님)
        assertThat(res.imageUrl())
                .startsWith("https://ddara-images.s3.ap-northeast-2.amazonaws.com/profiles/")
                .endsWith(".jpg");
    }
}
