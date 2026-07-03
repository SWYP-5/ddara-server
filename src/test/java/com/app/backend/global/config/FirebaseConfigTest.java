package com.app.backend.global.config;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FirebaseConfigTest {

    @Test
    void 키_파일이_없으면_예외없이_초기화를_건너뛴다() {
        // CI 등 키가 없는 환경을 흉내: 존재하지 않는 경로를 준다.
        FirebaseConfig config = new FirebaseConfig();
        config.setServiceAccountPath("firebase/no-such-key.json");

        // 앱이 죽으면 안 되므로, 어떤 예외도 던지지 않아야 한다.
        assertThatCode(config::initialize).doesNotThrowAnyException();
    }

    @Test
    void 키_파일이_있으면_Firebase가_초기화된다() {
        // 실제 서비스 계정 키는 로컬에만 있고 CI엔 없다(gitignore). 없으면 이 테스트는 스킵.
        assumeTrue(new ClassPathResource("firebase/firebase-service-account.json").exists(),
                "서비스 계정 키가 없어 스킵(로컬에서만 검증)");

        FirebaseConfig config = new FirebaseConfig();   // 기본 경로 사용

        boolean initialized = config.initialize();

        assertThat(initialized).isTrue();
        assertThat(FirebaseApp.getApps()).isNotEmpty();
    }
}
