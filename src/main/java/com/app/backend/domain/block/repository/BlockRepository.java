package com.app.backend.domain.block.repository;

import com.app.backend.domain.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    List<Block> findByBlockedId(Long blockedId);

    void deleteByBlockerIdOrBlockedId(Long blockerId, Long blockedId);
}