package com.hiscope.evaluation.domain.evaluation.result.dto;

import java.time.LocalDateTime;

public record PendingSubmissionRowView(
        Long evaluatorId,
        String evaluatorName,
        String evaluatorEmployeeNumber,
        Long evaluatorDepartmentId,
        String evaluatorDepartmentName,
        Long evaluateeId,
        String evaluateeName,
        String evaluateeEmployeeNumber,
        Long evaluateeDepartmentId,
        String evaluateeDepartmentName,
        LocalDateTime assignedAt
) {
}
