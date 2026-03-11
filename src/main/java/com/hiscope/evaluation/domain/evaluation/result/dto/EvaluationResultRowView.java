package com.hiscope.evaluation.domain.evaluation.result.dto;

public record EvaluationResultRowView(
        Long evaluateeId,
        String evaluateeName,
        String employeeNumber,
        Long departmentId,
        String departmentName,
        long totalEvaluatorCount,
        long submittedEvaluatorCount,
        double submissionRate,
        Double averageScore
) {
}
