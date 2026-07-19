package com.app.backend.domain.report.dto;

import com.app.backend.domain.report.entity.ReportReason;
import com.app.backend.domain.report.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "신고 대상 종류는 필수입니다.") ReportTargetType targetType,
        @NotNull(message = "신고 대상 id는 필수입니다.") Long targetId,
        Long groupId,
        @NotNull(message = "신고 사유는 필수입니다.") ReportReason reasonCode,
        @Size(max = 200, message = "신고 내용은 최대 200자입니다.") String reasonText
) {
}