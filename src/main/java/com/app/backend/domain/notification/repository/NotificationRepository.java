package com.app.backend.domain.notification.repository;

import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림을 type 필터 + 최신순으로 (size만큼)
    List<Notification> findByUserIdAndTypeInOrderByCreatedAtDesc(
            Long userId, Collection<NotificationType> types, Pageable pageable);

    // 내 전체 안읽음 개수
    long countByUserIdAndReadAtIsNull(Long userId);

    // 내 안읽음 알림 전부 일괄 읽음 처리 (N-03)
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
