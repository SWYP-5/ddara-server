package com.app.backend.domain.notification.service;

import com.app.backend.domain.user.entity.User;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * FCM 푸시 발송. 인앱 알림 저장(NotificationService)과 별개로,
 * 수신자의 기기 토큰(users.fcm_token)으로 실제 푸시를 보낸다.
 *
 * <p>발송 실패는 절대 밖으로 던지지 않는다 — 푸시는 부가 기능이고,
 * 인앱 알림 저장이 FCM 때문에 롤백되면 안 되기 때문.
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    /**
     * 유저에게 푸시 1건 발송. 토큰이 없거나 Firebase가 초기화되지 않은 환경(CI 등)이면 조용히 스킵.
     * 무효 토큰(UNREGISTERED/INVALID_ARGUMENT) 응답이면 유저의 토큰을 비운다(재등록 유도).
     */
    public void sendTo(User user, String title, String body, Map<String, String> data) {
        String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            return;   // 토큰 미등록 유저 — 보낼 곳이 없음
        }
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase가 초기화되지 않아 FCM 발송을 건너뜁니다 (userId={})", user.getId());
            return;
        }

        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();

        try {
            deliver(message);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                // 앱 삭제·토큰 만료 등으로 죽은 토큰 — 비워서 다음 발송 시도 자체를 막는다.
                user.clearFcmToken();
                log.info("무효 FCM 토큰 제거 (userId={}, code={})", user.getId(), code);
            } else {
                log.warn("FCM 발송 실패 (userId={}, code={})", user.getId(), code, e);
            }
        }
    }

    /** 실제 전송(네트워크). 테스트에서 바꿔치기할 수 있도록 분리. */
    String deliver(Message message) throws FirebaseMessagingException {
        return FirebaseMessaging.getInstance().send(message);
    }
}
