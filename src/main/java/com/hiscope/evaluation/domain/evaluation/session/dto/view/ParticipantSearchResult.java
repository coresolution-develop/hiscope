package com.hiscope.evaluation.domain.evaluation.session.dto.view;

public record ParticipantSearchResult(
        Long employeeId,
        String name,
        String departmentName,
        String position
) {}
