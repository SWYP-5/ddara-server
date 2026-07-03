package com.app.backend.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 앱 기동 시 Firebase Admin SDK를 초기화한다(FCM 푸시 발송의 사전 준비).
 *
 * <p>서비스 계정 키(JSON)는 절대 커밋하지 않으므로(gitignore), 키가 없는 환경(CI 등)에서는
 * 초기화를 건너뛰고 경고만 남긴다. 즉 키가 없어도 앱/테스트는 정상 기동한다.
 * 실제 푸시 발송은 이 초기화가 끝난 뒤 2차에서 붙인다.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    // 서비스 계정 키 경로(클래스패스 기준). 기본값은 src/main/resources/firebase/ 아래.
    @Value("${firebase.service-account-path:firebase/firebase-service-account.json}")
    private String serviceAccountPath = "firebase/firebase-service-account.json";

    @PostConstruct
    public void init() {
        initialize();
    }

    /**
     * Firebase를 초기화한다. 초기화(또는 이미 초기화됨)되면 true, 키가 없어 건너뛰면 false.
     * (테스트에서 결과를 확인할 수 있도록 분리)
     */
    boolean initialize() {
        // 이미 초기화된 경우 중복 초기화 방지(테스트/재기동 대비).
        if (!FirebaseApp.getApps().isEmpty()) {
            return true;
        }

        ClassPathResource resource = new ClassPathResource(serviceAccountPath);
        if (!resource.exists()) {
            log.warn("Firebase 서비스 계정 키가 없어 FCM 초기화를 건너뜁니다: {}", serviceAccountPath);
            return false;
        }

        try (InputStream keyStream = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(keyStream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase 초기화 완료");
            return true;
        } catch (IOException e) {
            log.error("Firebase 초기화 실패 (키를 읽지 못함): {}", serviceAccountPath, e);
            return false;
        }
    }

    // 테스트에서 키 경로를 바꿔 끼우기 위한 setter.
    void setServiceAccountPath(String serviceAccountPath) {
        this.serviceAccountPath = serviceAccountPath;
    }
}
