package com.hiscope.evaluation.domain.evaluation.session.dto.view;

public record PendingEvaluatorRowView(
        Long evaluatorId,
        String evaluatorName,
        long pendingCount,
        long totalCount
) {
}
