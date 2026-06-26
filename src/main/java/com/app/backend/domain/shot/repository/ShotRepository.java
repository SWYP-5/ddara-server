package com.app.backend.domain.shot.repository;

import com.app.backend.domain.shot.entity.Shot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShotRepository extends JpaRepository<Shot, Long> {
}