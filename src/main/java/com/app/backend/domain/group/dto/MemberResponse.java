package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
import com.app.backend.domain.user.entity.User;

public record MemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        MembershipRole role
) {
    public static MemberResponse of(Membership membership, User user) {
        return new MemberResponse(
                user.getId(),
                user.getName(),
                user.getProfileImageUrl(),
                membership.getRole()
        );
    }
}