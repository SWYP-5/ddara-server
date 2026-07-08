package com.app.backend.domain.notification.repository;

import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 MySQL(로컬 도커 · CI mysql 서비스)로 도는 통합 테스트.
 *
 * <p>MySQL의 {@code JSON} 컬럼은 저장 시 {@code "cycleId": 55}처럼 공백을 넣어 정규화하므로,
 * payload를 공백 없는 문자열로 LIKE 부분검색하면 매칭에 실패한다(= 마감 알림이 매 분 중복 발송되던
 * 버그의 원인). {@code JSON_EXTRACT} 기반 조회는 공백·키순서와 무관하게 정확히 매칭돼야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryDeadlineTest {

    @Autowired
    private NotificationRepository notificationRepository;

    // 앱(NotificationService)이 실제로 저장하는 형태 — 공백 없는 compact JSON
    private static final String PAYLOAD =
            "{\"groupId\":7,\"groupName\":\"스위프\",\"cycleId\":55,\"remainingMinutes\":60,"
                    + "\"deadlineAt\":\"2026-07-08T21:00:00+09:00\",\"imageUrl\":\"https://x/logo.png\"}";

    private void saveDeadline() {
        notificationRepository.save(Notification.builder()
                .userId(1L).type(NotificationType.DEADLINE).payload(PAYLOAD).build());
        notificationRepository.flush();
    }

    @Test
    void 같은_회차_같은_단계_알림이_있으면_존재로_판정한다() {
        saveDeadline();

        assertThat(notificationRepository.existsDeadlineNotification("DEADLINE", 55L, 60)).isTrue();
    }

    @Test
    void 다른_단계면_존재하지_않음으로_판정한다() {
        saveDeadline();

        assertThat(notificationRepository.existsDeadlineNotification("DEADLINE", 55L, 30)).isFalse();
    }

    @Test
    void 다른_회차면_존재하지_않음으로_판정한다() {
        saveDeadline();

        assertThat(notificationRepository.existsDeadlineNotification("DEADLINE", 99L, 60)).isFalse();
    }
}
