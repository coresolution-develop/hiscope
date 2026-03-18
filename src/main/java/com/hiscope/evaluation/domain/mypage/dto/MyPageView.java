package com.hiscope.evaluation.domain.mypage.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MyPageView(
        Profile profile,
        Summary summary,
        List<EvaluationResult> evaluations,
        List<QuestionScore> questionScores,
        List<CategoryScore> categoryScores,
        AiSummary aiSummary,
        String aiSummaryMode
) {

    public record Profile(
            Long employeeId,
            String employeeNumber,
            String name,
            String departmentName,
            String position,
            String jobTitle
    ) {
    }

    public record Summary(
            double myOverall,
            double orgOverall,
            double gap,
            int totalReceivedEvaluations,
            int totalScoreItems,
            int totalCommentItems
    ) {
    }

    public record EvaluationResult(
            Long assignmentId,
            String sessionName,
            String evaluatorName,
            LocalDateTime submittedAt,
            Double averageScore,
            String commentPreview
    ) {
    }

    public record QuestionScore(
            Long questionId,
            String category,
            String question,
            Double myAverage,
            Double orgAverage,
            Double delta,
            int responseCount,
            String comments
    ) {
    }

    public record CategoryScore(
            String category,
            Double myAverage,
            Double orgAverage,
            Double delta
    ) {
    }

    public record AiSummary(
            List<String> strengths,
            List<String> improvements,
            String overallComment
    ) {
    }
}
