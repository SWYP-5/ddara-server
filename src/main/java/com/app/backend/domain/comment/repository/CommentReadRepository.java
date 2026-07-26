package com.app.backend.domain.comment.repository;

import com.app.backend.domain.comment.entity.CommentRead;
import com.app.backend.domain.comment.entity.CommentReadId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentReadRepository extends JpaRepository<CommentRead, CommentReadId> {

    Optional<CommentRead> findByUserIdAndShotId(Long userId, Long shotId);

    List<CommentRead> findByUserIdAndShotIdIn(Long userId, List<Long> shotIds);
}
