package com.app.backend.domain.notification.service;

import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class FcmServiceTest {

    private FcmService fcmService;

    @BeforeEach
    void setUp() {
        // 실제 Firebase 서버로 나가지 않도록 deliver(전송)만 바꿔치기할 수 있는 spy 사용
        fcmService = spy(new FcmService());
        deleteFirebaseApps();
    }

    @AfterEach
    void tearDown() {
        deleteFirebaseApps();
    }

    /** 테스트 간 Firebase 초기화 상태가 새지 않도록 정리 */
    private void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    /** 진짜 키 없이 테스트용 가짜 자격증명으로 Firebase를 초기화(전송은 spy가 가로챔) */
    private void initFakeFirebase() {
        GoogleCredentials fake = GoogleCredentials.create(
                new AccessToken("fake-token", Date.from(Instant.now().plusSeconds(3600))));
        FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(fake)
                .setProjectId("test-project")
                .build());
    }

    private User userWithToken(String fcmToken) {
        User user = User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("p")
                .name("멤버")
                .build();
        if (fcmToken != null) {
            user.updateFcmToken(fcmToken);
        }
        return user;
    }

    @Test
    void 토큰이_없는_유저에게는_발송하지_않는다() throws FirebaseMessagingException {
        initFakeFirebase();
        User user = userWithToken(null);

        fcmService.sendTo(user, "제목", "내용", Map.of());

        verify(fcmService, never()).deliver(any());
    }

    @Test
    void Firebase가_초기화되지_않았으면_발송을_건너뛴다() throws FirebaseMessagingException {
        // CI처럼 키가 없어 초기화가 안 된 환경 — 예외 없이 조용히 스킵해야 한다.
        User user = userWithToken("device-token");

        assertThatCode(() -> fcmService.sendTo(user, "제목", "내용", Map.of()))
                .doesNotThrowAnyException();
        verify(fcmService, never()).deliver(any());
    }

    @Test
    void 토큰과_초기화가_준비되면_푸시를_발송한다() throws FirebaseMessagingException {
        initFakeFirebase();
        doReturn("message-id").when(fcmService).deliver(any());
        User user = userWithToken("device-token");

        fcmService.sendTo(user, "제목", "내용", Map.of("type", "MEMBER_JOIN"));

        verify(fcmService).deliver(any());
    }

    @Test
    void 무효_토큰_응답이면_유저의_토큰을_비운다() throws FirebaseMessagingException {
        initFakeFirebase();
        FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
        given(unregistered.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
        doThrow(unregistered).when(fcmService).deliver(any());
        User user = userWithToken("dead-token");

        assertThatCode(() -> fcmService.sendTo(user, "제목", "내용", Map.of()))
                .doesNotThrowAnyException();   // 발송 실패해도 예외를 밖으로 던지지 않음(인앱 저장 보호)

        assertThat(user.getFcmToken()).isNull();   // 무효 토큰은 제거
    }

    @Test
    void 일시적_오류면_토큰을_유지하고_예외만_삼킨다() throws FirebaseMessagingException {
        initFakeFirebase();
        FirebaseMessagingException transientError = mock(FirebaseMessagingException.class);
        given(transientError.getMessagingErrorCode()).willReturn(MessagingErrorCode.INTERNAL);
        doThrow(transientError).when(fcmService).deliver(any());
        User user = userWithToken("alive-token");

        assertThatCode(() -> fcmService.sendTo(user, "제목", "내용", Map.of()))
                .doesNotThrowAnyException();

        assertThat(user.getFcmToken()).isEqualTo("alive-token");   // 일시 오류는 토큰 유지
    }
}
