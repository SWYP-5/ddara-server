package com.app.backend.domain.comment.controller;

import com.app.backend.domain.comment.dto.CommentListResponse;
import com.app.backend.domain.comment.dto.CommentRequest;
import com.app.backend.domain.comment.dto.CommentResponse;
import com.app.backend.domain.comment.dto.CommentUpdateResponse;
import com.app.backend.domain.comment.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // 코멘트 작성 (COMMENT-01)
    @PostMapping("/api/shots/{shotId}/comments")
    public CommentResponse createComment(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long shotId,
                                         @Valid @RequestBody CommentRequest request) {
        return commentService.createComment(userId, shotId, request.content());
    }

    // 코멘트 목록 조회 (COMMENT-02)
    @GetMapping("/api/shots/{shotId}/comments")
    public CommentListResponse getComments(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long shotId) {
        return commentService.getComments(userId, shotId);
    }

    // 코멘트 수정 (COMMENT-03)
    @PatchMapping("/api/comments/{commentId}")
    public CommentUpdateResponse updateComment(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long commentId,
                                               @Valid @RequestBody CommentRequest request) {
        return commentService.updateComment(userId, commentId, request.content());
    }

    // 코멘트 삭제 (COMMENT-04)
    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Long userId,
                              @PathVariable Long commentId) {
        commentService.deleteComment(userId, commentId);
    }

    // 코멘트 읽음 기록 (COMMENT-05)
    @PostMapping("/api/shots/{shotId}/comments/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markCommentsRead(@AuthenticationPrincipal Long userId,
                                 @PathVariable Long shotId) {
        commentService.markCommentsRead(userId, shotId);
    }
}