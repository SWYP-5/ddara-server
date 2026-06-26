package com.app.backend.domain.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "`groups`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String description;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    private String inviteCode;

    @Column(name = "invite_code_expires_at", nullable = false)
    private LocalDateTime inviteCodeExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Group(String name, String description, Long ownerUserId,
                  String inviteCode, LocalDateTime inviteCodeExpiresAt) {
        this.name = name;
        this.description = description;
        this.ownerUserId = ownerUserId;
        this.inviteCode = inviteCode;
        this.inviteCodeExpiresAt = inviteCodeExpiresAt;
    }

    // 초대코드가 만료됐는지 판단
    public boolean isInviteCodeExpired(LocalDateTime now) {
        return now.isAfter(inviteCodeExpiresAt);
    }

    // 만료된 초대코드를 새 코드로 재발급 + 만료시각 갱신
    public void reissueInviteCode(String newCode, LocalDateTime newExpiresAt) {
        this.inviteCode = newCode;
        this.inviteCodeExpiresAt = newExpiresAt;
    }
}