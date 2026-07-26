package com.app.backend.domain.comment.entity;

import java.io.Serializable;
import java.util.Objects;

public class CommentReadId implements Serializable {

    private Long userId;
    private Long shotId;

    protected CommentReadId() {
    }

    public CommentReadId(Long userId, Long shotId) {
        this.userId = userId;
        this.shotId = shotId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CommentReadId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(shotId, that.shotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, shotId);
    }
}
