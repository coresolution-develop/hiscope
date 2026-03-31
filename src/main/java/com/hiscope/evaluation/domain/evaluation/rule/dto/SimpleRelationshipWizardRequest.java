package com.hiscope.evaluation.domain.evaluation.rule.dto;

import java.util.List;

public record SimpleRelationshipWizardRequest(
        boolean upwardEnabled,
        List<String> upwardEvaluatorPositions,
        List<String> upwardEvaluateePositions,
        boolean peerEnabled,
        List<String> peerPositions,
        boolean downwardEnabled,
        List<String> downwardEvaluatorPositions,
        List<String> downwardEvaluateePositions
) {}
