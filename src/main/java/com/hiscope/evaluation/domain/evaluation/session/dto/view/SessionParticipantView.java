package com.hiscope.evaluation.domain.evaluation.session.dto.view;

public record SessionParticipantView(
        Long employeeId,
        String name,
        String departmentName,
        String position,
        boolean isActive,
        String overrideAction,   // null | "ADD" | "REMOVE" | "UPDATE"
        String overrideReason
) {
    public boolean isRemoved() {
        return "REMOVE".equals(overrideAction);
    }

    public boolean isAdded() {
        return "ADD".equals(overrideAction);
    }
}
