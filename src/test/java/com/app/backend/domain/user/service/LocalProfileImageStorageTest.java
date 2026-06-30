package com.app.backend.domain.user.service;

import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalProfileImageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void 이미지를_로컬에_저장하고_접근_URL을_반환한다() throws Exception {
        // given
        LocalProfileImageStorage storage =
                new LocalProfileImageStorage(tempDir.toString(), "http://localhost:8080");
        MultipartFile image = new MockMultipartFile(
                "image", "me.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        // when
        String url = storage.store(image);

        // then: URL 형식 (baseUrl + /images/profiles/{uuid}.jpg)
        assertThat(url).startsWith("http://localhost:8080/images/profiles/");
        assertThat(url).endsWith(".jpg");

        // then: 실제 파일이 디스크에 저장됨
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path saved = tempDir.resolve("profiles").resolve(fileName);
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readAllBytes(saved)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void png도_확장자를_png로_저장한다() {
        // given
        LocalProfileImageStorage storage =
                new LocalProfileImageStorage(tempDir.toString(), "http://localhost:8080");
        MultipartFile image = new MockMultipartFile(
                "image", "me.png", "image/png", new byte[]{1});

        // when
        String url = storage.store(image);

        // then
        assertThat(url).endsWith(".png");
    }

    @Test
    void 지원하지_않는_형식이면_INVALID_IMAGE_FILE_예외를_던진다() {
        // given
        LocalProfileImageStorage storage =
                new LocalProfileImageStorage(tempDir.toString(), "http://localhost:8080");
        MultipartFile notImage = new MockMultipartFile(
                "image", "bad.txt", "text/plain", new byte[]{1});

        // when & then
        assertThatThrownBy(() -> storage.store(notImage))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
    }
}
