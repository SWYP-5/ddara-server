package com.app.backend.domain.report.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.report.client.DiscordReportNotifier;
import com.app.backend.domain.report.dto.ReportRequest;
import com.app.backend.domain.report.entity.Report;
import com.app.backend.domain.report.entity.ReportReason;
import com.app.backend.domain.report.repository.ReportRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ShotRepository shotRepository;
    private final CycleRepository cycleRepository;
    private final MembershipRepository membershipRepository;
    private final DiscordReportNotifier discordReportNotifier;

    public ReportService(ReportRepository reportRepository,
                         ShotRepository shotRepository,
                         CycleRepository cycleRepository,
                         MembershipRepository membershipRepository,
                         DiscordReportNotifier discordReportNotifier) {
        this.reportRepository = reportRepository;
        this.shotRepository = shotRepository;
        this.cycleRepository = cycleRepository;
        this.membershipRepository = membershipRepository;
        this.discordReportNotifier = discordReportNotifier;
    }

    @Transactional
    public void report(Long userId, ReportRequest request) {
        if (request.reasonCode() == ReportReason.ETC
                && (request.reasonText() == null || request.reasonText().isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

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

        Report report = reportRepository.save(Report.builder()
                .reporterId(userId)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reasonCode(request.reasonCode())
                .reasonText(request.reasonText())
                .build());

        shot.markUnderReview();

        discordReportNotifier.notify(report);
    }
}