package com.app.backend.domain.comment.repository;

import com.app.backend.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByShotIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long shotId);

    List<Comment> findByShotIdInAndDeletedAtIsNull(List<Long> shotIds);
}