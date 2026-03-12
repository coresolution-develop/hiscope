package com.hiscope.evaluation.domain.evaluation.session.dto.view;

import java.time.LocalDateTime;

public record AssignmentRowView(
        Long id,
        Long evaluatorId,
        String evaluatorName,
        String evaluatorEmployeeNumber,
        Long evaluateeId,
        String evaluateeName,
        String evaluateeEmployeeNumber,
        String status,
        LocalDateTime submittedAt
) {
}
