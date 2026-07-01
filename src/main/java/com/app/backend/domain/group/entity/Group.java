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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Group(String name, String description, Long ownerUserId, String inviteCode) {
        this.name = name;
        this.description = description;
        this.ownerUserId = ownerUserId;
        this.inviteCode = inviteCode;
    }

    // 전원 나가기 시 소프트 삭제 (5일 후 스케줄러가 완전 삭제)
    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }
}