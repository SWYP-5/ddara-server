package com.app.backend.domain.group.repository;

import com.app.backend.domain.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<Group> findByInviteCode(String inviteCode);
}