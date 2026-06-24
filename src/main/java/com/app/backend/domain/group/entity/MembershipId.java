package com.app.backend.domain.group.entity;

import java.io.Serializable;
import java.util.Objects;

public class MembershipId implements Serializable {

    private Long groupId;
    private Long userId;

    protected MembershipId() {
    }

    public MembershipId(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MembershipId that)) {
            return false;
        }
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}