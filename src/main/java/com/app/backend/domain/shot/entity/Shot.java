package com.app.backend.domain.shot.entity;

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
@Table(name = "shots", indexes = {
        @Index(name = "idx_shots_cycle", columnList = "cycle_id, uploaded_at"),
        @Index(name = "idx_shots_user", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShotType type;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Shot(Long cycleId, Long userId, ShotType type, String imageUrl) {
        this.cycleId = cycleId;
        this.userId = userId;
        this.type = type;
        this.imageUrl = imageUrl;
    }

    public void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
