package com.app.backend.domain.group.repository;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {

    // 내가 현재 속한 멤버십 목록
    List<Membership> findByUserIdAndLeftAtIsNull(Long userId);

    // 한 모임의 현재 멤버 수
    long countByGroupIdAndLeftAtIsNull(Long groupId);

    // 내가 현재 속한 모임 수
    long countByUserIdAndLeftAtIsNull(Long userId);
}