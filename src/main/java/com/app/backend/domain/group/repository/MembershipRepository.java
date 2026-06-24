package com.app.backend.domain.group.repository;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {
}