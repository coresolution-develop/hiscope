package com.hiscope.evaluation.domain.evaluation.session.dto.view;

import java.time.LocalDateTime;

public record MyEvaluationItemView(
        Long assignmentId,
        Long evaluateeId,
        String evaluateeName,
        String evaluateeDept,
        String relationType,
        String status,
        LocalDateTime submittedAt,
        Long sessionId
) {
    public boolean isSubmitted() {
        return "SUBMITTED".equals(status);
    }
}
