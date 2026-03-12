package com.hiscope.evaluation.domain.evaluation.session.dto.view;

import java.util.List;

public record SessionDetailView(
        List<AssignmentRowView> assignmentRows,
        int filteredAssignmentCount,
        int assignmentPage,
        int assignmentSize,
        int assignmentTotalPages,
        int totalAssignmentCount,
        long submittedAssignmentCount,
        long pendingAssignmentCount,
        int assignmentProgressRate,
        List<PendingEvaluatorRowView> pendingEvaluators
) {
}
