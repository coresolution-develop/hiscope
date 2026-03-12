package com.hiscope.evaluation.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
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
    private long deadlineSoonSessions;
    private long overdueInProgressSessions;
    private long failedOperationsLast7Days;

    private List<SessionProgress> sessionProgressList;
    private List<DepartmentProgress> departmentProgressList;
    private List<PendingEvaluatorProgress> pendingEvaluatorProgressList;
    private List<DeadlineAlert> deadlineAlerts;
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
        private LocalDate endDate;
        private Long daysUntilDeadline;
    }

    @Getter
    @Builder
    public static class DeadlineAlert {
        private Long sessionId;
        private String sessionName;
        private LocalDate endDate;
        private long daysUntilDeadline;
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
    public static class PendingEvaluatorProgress {
        private Long evaluatorId;
        private String evaluatorName;
        private String evaluatorEmployeeNumber;
        private String evaluatorDepartmentName;
        private long totalAssignments;
        private long pendingAssignments;
        private double submissionRate;
    }

    @Getter
    @Builder
    public static class UploadActivity {
        private String uploadType;
        private String uploadTypeLabel;
        private String fileName;
        private String status;
        private String statusLabel;
        private int totalRows;
        private int successRows;
        private int failRows;
        private String historyLink;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AuditActivity {
        private String action;
        private String actionLabel;
        private String outcome;
        private String outcomeLabel;
        private String detail;
        private String actorLoginId;
        private String targetType;
        private String targetId;
        private String targetLabel;
        private String targetLink;
        private LocalDateTime occurredAt;
    }
}
