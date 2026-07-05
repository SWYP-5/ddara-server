package com.app.backend.domain.auth.oauth;

import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 애플 로그인 — Firebase Auth 경유.
 * 앱이 Firebase Authentication으로 애플 로그인 후 보낸 Firebase ID 토큰을
 * Firebase Admin SDK로 검증하고 uid·name을 추출한다.
 * 이름은 앱이 최초 애플 로그인 때 Firebase 프로필(displayName)에 저장해 두므로
 * 재로그인에도 토큰 name 클레임에 항상 포함된다.
 */
@Component
public class AppleOAuthClient implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(AppleOAuthClient.class);

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.APPLE;
    }

    @Override
    public OAuthUserInfo getUserInfo(String idToken) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.error("Firebase가 초기화되지 않아 애플 로그인을 처리할 수 없습니다 (서비스 계정 키 확인)");
            throw new CustomException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }

        FirebaseToken token;
        try {
            token = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            throw new CustomException(ErrorCode.INVALID_OAUTH_TOKEN);
        }

        return new OAuthUserInfo(token.getUid(), token.getName());
    }
}
