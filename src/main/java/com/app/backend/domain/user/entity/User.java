package com.app.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // 알림 설정(JSON). null이면 전체 on. (U-03/04)
    @Column(name = "notification_prefs", columnDefinition = "json")
    private String notificationPrefs;

    // FCM 푸시 토큰. 유저당 1개(덮어쓰기), 로그아웃/무효 토큰 시 null. (U-06)
    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 탈퇴 시각(soft delete). null이면 활성 사용자. (U-05)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private User(AuthProvider provider, String providerId, String email,
                 String name, String profileImageUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
    }

    /** 프로필 이미지 URL 변경. null이면 디폴트 아바타로 초기화. (U-02) */
    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /** 알림 설정(JSON 문자열) 전체 교체. (U-04) */
    public void updateNotificationPrefs(String notificationPrefs) {
        this.notificationPrefs = notificationPrefs;
    }

    /** FCM 토큰 등록 — 유저당 1개, 기존 값 덮어쓰기. (U-06) */
    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    /** FCM 토큰 제거 — 로그아웃/무효 토큰 시 null. (U-06) */
    public void clearFcmToken() {
        this.fcmToken = null;
    }

    /** 회원 탈퇴 — soft delete + 개인정보 익명화. (U-05) */
    public void withdraw(LocalDateTime now) {
        this.deletedAt = now;
        this.name = "탈퇴한사용자";
        this.email = null;
        this.profileImageUrl = null;
    }

    /** 탈퇴(soft delete) 상태인지 */
    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    /** 탈퇴 계정을 같은 소셜로 재가입 시 재활성화 (deletedAt 해제 + 이름 소셜값으로 리셋) */
    public void reactivate(String name) {
        this.deletedAt = null;
        this.name = name;
    }
}
