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

    // 마감 임박(1시간 내) 회차 조회 — 마감 임박 알림용 (#77)
    List<Cycle> findByStatusAndDeadlineAtBetween(CycleStatus status, LocalDateTime from, LocalDateTime to);

    Optional<Cycle> findTopByGroupIdAndStatusOrderByCycleNumberDesc(Long groupId, CycleStatus status);

    List<Cycle> findByGroupIdAndStatusOrderByCycleNumberDesc(Long groupId, CycleStatus status);

    List<Cycle> findByGroupId(Long groupId);

    void deleteByGroupId(Long groupId);
}