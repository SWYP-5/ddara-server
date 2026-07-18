package com.app.backend.domain.comment.entity;

import com.app.backend.domain.shot.entity.ReviewStatus;
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
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_shot", columnList = "shot_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shot_id", nullable = false)
    private Long shotId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Comment(Long shotId, Long userId, String content) {
        this.shotId = shotId;
        this.userId = userId;
        this.content = content;
    }

    public void updateContent(String content, LocalDateTime now) {
        this.content = content;
        this.updatedAt = now;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isUnderReview() {
        return reviewStatus == ReviewStatus.UNDER_REVIEW;
    }

    public boolean isRemoved() {
        return reviewStatus == ReviewStatus.REMOVED;
    }
}