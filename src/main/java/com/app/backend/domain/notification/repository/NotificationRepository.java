package com.app.backend.domain.notification.repository;

import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림을 type 필터 + 최신순으로 (size만큼)
    List<Notification> findByUserIdAndTypeInOrderByCreatedAtDesc(
            Long userId, Collection<NotificationType> types, Pageable pageable);

    // 내 전체 안읽음 개수
    long countByUserIdAndReadAtIsNull(Long userId);
}
