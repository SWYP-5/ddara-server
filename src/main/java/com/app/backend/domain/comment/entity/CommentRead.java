package com.app.backend.domain.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 유저가 특정 사진의 코멘트를 마지막으로 본 시각
@Entity
@Table(name = "comment_reads")
@IdClass(CommentReadId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentRead {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "shot_id")
    private Long shotId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    public CommentRead(Long userId, Long shotId, LocalDateTime readAt) {
        this.userId = userId;
        this.shotId = shotId;
        this.readAt = readAt;
    }

    public void updateReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
