package com.app.backend.domain.report.repository;

import com.app.backend.domain.report.entity.Report;
import com.app.backend.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByReporterIdAndTargetTypeAndTargetIdIn(
            Long reporterId, ReportTargetType targetType, Collection<Long> targetIds);
}