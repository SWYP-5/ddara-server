package com.app.backend.domain.cycle.repository;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CycleRepository extends JpaRepository<Cycle, Long> {

    boolean existsByGroupIdAndStatus(Long groupId, CycleStatus status);

    Optional<Cycle> findByGroupIdAndStatus(Long groupId, CycleStatus status);

    long countByGroupId(Long groupId);

    List<Cycle> findByStatusAndDeadlineAtBefore(CycleStatus status, LocalDateTime time);

    Optional<Cycle> findTopByGroupIdAndStatusOrderByCycleNumberDesc(Long groupId, CycleStatus status);

    List<Cycle> findByGroupIdAndStatusOrderByCycleNumberDesc(Long groupId, CycleStatus status);
}