package com.hiscope.evaluation.domain.evaluation.rule.dto;

import java.util.List;

public record SimpleRelationshipPreviewResult(
        int totalCount,
        boolean truncated,
        List<SimpleRelationshipPreviewItem> items,
        List<String> warnings
) {}
