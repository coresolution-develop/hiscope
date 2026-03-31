package com.hiscope.evaluation.domain.evaluation.rule.dto;

public record SimpleRelationshipPreviewItem(
        String relationType,
        String evaluatorName,
        String evaluatorDept,
        String evaluatorPosition,
        String evaluateeName,
        String evaluateeDept,
        String evaluateePosition
) {}
