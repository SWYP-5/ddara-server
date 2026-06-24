package com.app.backend.domain.group.repository;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {

    // 특정 모임의 내 멤버십 (나간 것 포함) — 재참여 복귀 판단용
    Optional<Membership> findByGroupIdAndUserId(Long groupId, Long userId);

    // 내가 현재 속한 멤버십 목록
    List<Membership> findByUserIdAndLeftAtIsNull(Long userId);

    // 한 모임의 현재 멤버 수
    long countByGroupIdAndLeftAtIsNull(Long groupId);

    // 내가 현재 속한 모임 수
    long countByUserIdAndLeftAtIsNull(Long userId);

    // 내가 이 모임에 이미 참여 중인지
    boolean existsByGroupIdAndUserIdAndLeftAtIsNull(Long groupId, Long userId);
}