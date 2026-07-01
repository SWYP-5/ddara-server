package com.app.backend.domain.group.repository;

import com.app.backend.domain.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    boolean existsByInviteCode(String inviteCode);
    
    Optional<Group> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

    List<Group> findByDeletedAtBefore(LocalDateTime time);
}