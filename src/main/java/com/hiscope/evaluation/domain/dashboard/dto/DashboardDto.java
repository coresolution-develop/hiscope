package com.hiscope.evaluation.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DashboardDto {

    private long totalEmployees;
    private long totalDepartments;
    private long activeSessions;
    private long totalEvaluatees;
    private long pendingAssignments;
    private double overallSubmissionRate;

    private List<SessionProgress> sessionProgressList;
    private List<DepartmentProgress> departmentProgressList;
    private List<UploadActivity> recentUploads;
    private List<AuditActivity> recentActivities;

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

    @Getter
    @Builder
    public static class DepartmentProgress {
        private Long departmentId;
        private String departmentName;
        private long totalAssignments;
        private long submittedAssignments;
        private long pendingAssignments;
        private double submissionRate;
    }

    @Getter
    @Builder
    public static class UploadActivity {
        private String uploadType;
        private String fileName;
        private String status;
        private int totalRows;
        private int successRows;
        private int failRows;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AuditActivity {
        private String action;
        private String outcome;
        private String detail;
        private String actorLoginId;
        private LocalDateTime occurredAt;
    }
}
