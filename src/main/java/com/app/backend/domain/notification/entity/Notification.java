package com.app.backend.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = @Index(
                name = "idx_noti_user_read_created",
                columnList = "user_id, read_at, created_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수신자
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    // 화면 표시용 데이터(type별 상이). JSON 문자열로 보관.
    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    // 읽은 시각. null이면 안읽음.
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(Long userId, NotificationType type, String payload) {
        this.userId = userId;
        this.type = type;
        this.payload = payload;
    }

    /** 읽음 처리. 이미 읽었으면 그대로 둔다(멱등). (N-02) */
    public void markAsRead(LocalDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
