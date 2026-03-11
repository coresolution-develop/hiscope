package com.hiscope.evaluation.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardDto {

    private long totalEmployees;
    private long totalDepartments;
    private long activeSessions;

    private List<SessionProgress> sessionProgressList;

    @Getter
    @Builder
    public static class SessionProgress {
        private Long sessionId;
        private String sessionName;
        private String status;
        private long totalAssignments;
        private long submittedAssignments;
        private double submissionRate;
    }
}
