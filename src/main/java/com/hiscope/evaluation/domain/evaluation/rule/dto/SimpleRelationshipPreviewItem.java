package com.hiscope.evaluation.domain.evaluation.rule.dto;

public record SimpleRelationshipPreviewItem(
        String relationType,
        Long evaluatorId,
        String evaluatorName,
        String evaluatorDept,
        String evaluatorJobTitle,
        Long evaluateeId,
        String evaluateeName,
        String evaluateeDept,
        String evaluateeJobTitle
) {}
