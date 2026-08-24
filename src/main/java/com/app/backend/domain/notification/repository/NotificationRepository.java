package com.app.backend.domain.notification.repository;

import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림을 type 필터 + 최신순
    List<Notification> findByUserIdAndTypeInOrderByCreatedAtDesc(
            Long userId, Collection<NotificationType> types);

    // 내 전체 안읽음 개수
    long countByUserIdAndReadAtIsNull(Long userId);

    // 같은 회차·같은 단계의 DEADLINE 알림 중복 생성 방지용.
    // payload는 MySQL JSON 컬럼이라 저장 시 공백이 들어가 정규화되므로 LIKE 문자열 검색은 매칭되지 않는다.
    // JSON_EXTRACT로 값을 정확히 꺼내 비교한다(공백·키순서 무관).
    @Query(value = "SELECT COUNT(*) FROM notifications " +
            "WHERE type = :type " +
            "AND JSON_EXTRACT(payload, '$.cycleId') = :cycleId " +
            "AND JSON_EXTRACT(payload, '$.remainingMinutes') = :stage",
            nativeQuery = true)
    long countDeadlineNotifications(@Param("type") String type,
                                    @Param("cycleId") long cycleId,
                                    @Param("stage") int stage);

    default boolean existsDeadlineNotification(String type, long cycleId, int stage) {
        return countDeadlineNotifications(type, cycleId, stage) > 0;
    }

    // 내 안읽음 알림 전부 일괄 읽음 처리 (N-03)
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 탈퇴 사용자 완전 삭제 시 그 사용자의 알림 일괄 삭제 (U-05)
    void deleteByUserId(Long userId);
}
