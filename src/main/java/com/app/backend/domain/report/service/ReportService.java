package com.app.backend.domain.report.service;

import com.app.backend.domain.comment.entity.Comment;
import com.app.backend.domain.comment.repository.CommentRepository;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.report.client.DiscordReportNotifier;
import com.app.backend.domain.report.dto.ReportRequest;
import com.app.backend.domain.report.entity.Report;
import com.app.backend.domain.report.entity.ReportReason;
import com.app.backend.domain.report.entity.ReportTargetType;
import com.app.backend.domain.report.repository.ReportRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class ReportService {

    private static final Set<ReportReason> SHOT_REASONS = EnumSet.of(
            ReportReason.OBSCENE, ReportReason.VIOLENCE, ReportReason.UNAUTHORIZED_PHOTO,
            ReportReason.HARASSMENT, ReportReason.ETC);
    private static final Set<ReportReason> COMMENT_REASONS = EnumSet.of(
            ReportReason.ABUSE, ReportReason.SEXUAL, ReportReason.HATE,
            ReportReason.IMPERSONATION, ReportReason.PRIVACY, ReportReason.ETC);
    private static final Set<ReportReason> USER_REASONS = EnumSet.of(
            ReportReason.INAPPROPRIATE_NICKNAME, ReportReason.INAPPROPRIATE_IMAGE,
            ReportReason.HARASSMENT, ReportReason.ETC);

    private final ReportRepository reportRepository;
    private final ShotRepository shotRepository;
    private final CommentRepository commentRepository;
    private final CycleRepository cycleRepository;
    private final MembershipRepository membershipRepository;
    private final DiscordReportNotifier discordReportNotifier;

    public ReportService(ReportRepository reportRepository,
                         ShotRepository shotRepository,
                         CommentRepository commentRepository,
                         CycleRepository cycleRepository,
                         MembershipRepository membershipRepository,
                         DiscordReportNotifier discordReportNotifier) {
        this.reportRepository = reportRepository;
        this.shotRepository = shotRepository;
        this.commentRepository = commentRepository;
        this.cycleRepository = cycleRepository;
        this.membershipRepository = membershipRepository;
        this.discordReportNotifier = discordReportNotifier;
    }

    @Transactional
    public void report(Long userId, ReportRequest request) {
        validateReason(request);
        switch (request.targetType()) {
            case SHOT -> reportShot(userId, request);
            case COMMENT -> reportComment(userId, request);
            case USER -> reportUser(userId, request);
        }
    }

    private void validateReason(ReportRequest request) {
        Set<ReportReason> allowed = switch (request.targetType()) {
            case SHOT -> SHOT_REASONS;
            case COMMENT -> COMMENT_REASONS;
            case USER -> USER_REASONS;
        };
        if (!allowed.contains(request.reasonCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (request.reasonCode() == ReportReason.ETC
                && (request.reasonText() == null || request.reasonText().isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void reportShot(Long userId, ReportRequest request) {
        Shot shot = shotRepository.findById(request.targetId())
                .filter(s -> s.getDeletedAt() == null && !s.isRemoved())
                .orElseThrow(() -> new CustomException(ErrorCode.SHOT_NOT_FOUND));

        if (shot.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Cycle cycle = cycleRepository.findById(shot.getCycleId())
                .orElseThrow(() -> new CustomException(ErrorCode.SHOT_NOT_FOUND));
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(cycle.getGroupId(), userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }

        Report report = saveReport(userId, request, null, null);
        shot.markUnderReview();
        discordReportNotifier.notify(report);
    }

    private void reportComment(Long userId, ReportRequest request) {
        Comment comment = commentRepository.findById(request.targetId())
                .filter(c -> c.getDeletedAt() == null && !c.isRemoved())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Shot shot = shotRepository.findById(comment.getShotId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        Cycle cycle = cycleRepository.findById(shot.getCycleId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(cycle.getGroupId(), userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }

        Report report = saveReport(userId, request, null, comment.getContent());
        discordReportNotifier.notify(report);
    }

    private void reportUser(Long userId, ReportRequest request) {
        if (request.groupId() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (request.targetId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(request.groupId(), userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }
        Membership target = membershipRepository
                .findByGroupIdAndUserId(request.groupId(), request.targetId())
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Report report = saveReport(userId, request, request.groupId(), target.getNickname());
        discordReportNotifier.notify(report);
    }

    private Report saveReport(Long userId, ReportRequest request, Long targetGroupId, String reportedContent) {
        return reportRepository.save(Report.builder()
                .reporterId(userId)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .targetGroupId(targetGroupId)
                .reasonCode(request.reasonCode())
                .reasonText(request.reasonText())
                .reportedContent(reportedContent)
                .build());
    }
}