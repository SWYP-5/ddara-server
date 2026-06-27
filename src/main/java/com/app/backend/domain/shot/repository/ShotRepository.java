package com.app.backend.domain.shot.repository;

import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShotRepository extends JpaRepository<Shot, Long> {

    Optional<Shot> findByCycleIdAndType(Long cycleId, ShotType type);
}