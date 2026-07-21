package com.app.backend.domain.comment.service;

import com.app.backend.domain.comment.dto.CommentListResponse;
import com.app.backend.domain.comment.dto.CommentResponse;
import com.app.backend.domain.comment.dto.CommentUpdateResponse;
import com.app.backend.domain.comment.entity.Comment;
import com.app.backend.domain.comment.repository.CommentRepository;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.report.entity.Report;
import com.app.backend.domain.report.entity.ReportTargetType;
import com.app.backend.domain.report.repository.ReportRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CommentRepository commentRepository;
    private final ShotRepository shotRepository;
    private final CycleRepository cycleRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;

    public CommentService(CommentRepository commentRepository,
                          ShotRepository shotRepository,
                          CycleRepository cycleRepository,
                          MembershipRepository membershipRepository,
                          UserRepository userRepository,
                          ReportRepository reportRepository) {
        this.commentRepository = commentRepository;
        this.shotRepository = shotRepository;
        this.cycleRepository = cycleRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
    }

    // 요청자가 신고한 코멘트 id 집합
    private Set<Long> reportedCommentIds(Long userId, List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Set.of();
        }
        return reportRepository
                .findByReporterIdAndTargetTypeAndTargetIdIn(userId, ReportTargetType.COMMENT, commentIds)
                .stream()
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public CommentResponse createComment(Long userId, Long shotId, String content) {
        Shot shot = findValidShot(shotId);
        Cycle cycle = cycleRepository.findById(shot.getCycleId())
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));
        requireMember(cycle.getGroupId(), userId);
        if (shot.isUnderReview()) {
            throw new CustomException(ErrorCode.SHOT_UNDER_REVIEW);
        }
        if (isLocked(shot, cycle, userId)) {
            throw new CustomException(ErrorCode.SHOT_LOCKED);
        }

        Comment comment = commentRepository.save(Comment.builder()
                .shotId(shotId)
                .userId(userId)
                .content(content)
                .build());

        String nickname = membershipRepository.findByGroupIdAndUserId(cycle.getGroupId(), userId)
                .map(Membership::getNickname)
                .orElse(null);
        String profileImageUrl = userRepository.findById(userId)
                .map(User::getProfileImageUrl)
                .orElse(null);
        return new CommentResponse(
                comment.getId(), userId, nickname, profileImageUrl,
                comment.getContent(), toKstOffset(comment.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public CommentListResponse getComments(Long userId, Long shotId) {
        Shot shot = findValidShot(shotId);
        Cycle cycle = cycleRepository.findById(shot.getCycleId())
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));
        requireMember(cycle.getGroupId(), userId);

        List<Comment> comments = commentRepository
                .findByShotIdAndDeletedAtIsNullOrderByCreatedAtAsc(shotId).stream()
                .filter(c -> !c.isRemoved())
                .toList();

        List<Long> writerIds = comments.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(writerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> nicknamesByUser = writerIds.stream()
                .collect(Collectors.toMap(Function.identity(), writerId ->
                        membershipRepository.findByGroupIdAndUserId(cycle.getGroupId(), writerId)
                                .map(Membership::getNickname)
                                .orElse("탈퇴한사용자")));
        Set<Long> reportedByMe = reportedCommentIds(userId, comments.stream().map(Comment::getId).toList());

        List<CommentListResponse.CommentItem> items = comments.stream()
                .map(c -> new CommentListResponse.CommentItem(
                        c.getId(),
                        c.getUserId(),
                        nicknamesByUser.get(c.getUserId()),
                        Optional.ofNullable(usersById.get(c.getUserId()))
                                .map(User::getProfileImageUrl)
                                .orElse(null),
                        c.isUnderReview() ? null : c.getContent(),
                        c.isUnderReview(),
                        reportedByMe.contains(c.getId()),
                        toKstOffset(c.getCreatedAt()),
                        toKstOffset(c.getUpdatedAt())))
                .toList();
        return new CommentListResponse(items);
    }

    @Transactional
    public CommentUpdateResponse updateComment(Long userId, Long commentId, String content) {
        Comment comment = findValidComment(commentId);
        if (!comment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.COMMENT_FORBIDDEN);
        }
        comment.updateContent(content, LocalDateTime.now());
        return new CommentUpdateResponse(
                comment.getId(), comment.getContent(), toKstOffset(comment.getUpdatedAt()));
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = findValidComment(commentId);
        if (!comment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.COMMENT_FORBIDDEN);
        }
        comment.delete(LocalDateTime.now());
    }

    private Shot findValidShot(Long shotId) {
        return shotRepository.findById(shotId)
                .filter(s -> s.getDeletedAt() == null && !s.isRemoved())
                .orElseThrow(() -> new CustomException(ErrorCode.SHOT_NOT_FOUND));
    }

    private Comment findValidComment(Long commentId) {
        return commentRepository.findById(commentId)
                .filter(c -> c.getDeletedAt() == null && !c.isRemoved())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    // SHOT-02와 동일한 잠금 규칙
    private boolean isLocked(Shot shot, Cycle cycle, Long userId) {
        if (cycle.getStatus() == CycleStatus.DONE
                || shot.getType() == ShotType.STARTER
                || shot.getUserId().equals(userId)) {
            return false;
        }
        boolean viewerUploaded = shotRepository.findByCycleIdAndUserId(cycle.getId(), userId)
                .filter(s -> s.getDeletedAt() == null && !s.isRemoved())
                .isPresent();
        return !viewerUploaded;
    }

    private OffsetDateTime toKstOffset(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(SEOUL).toOffsetDateTime();
    }
}