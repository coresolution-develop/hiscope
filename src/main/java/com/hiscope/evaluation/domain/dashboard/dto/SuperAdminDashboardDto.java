package com.hiscope.evaluation.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SuperAdminDashboardDto {

    private long totalOrganizations;
    private long activeOrganizations;
    private long inactiveOrganizations;
    private long riskOrganizations;
    private long failedOperationsLast7Days;
    private List<OrganizationOperationSummary> organizationSummaries;

    @Getter
    @Builder
    public static class OrganizationOperationSummary {
        private Long organizationId;
        private String organizationName;
        private String organizationCode;
        private String status;
        private long activeUserCount;
        private long totalSessionCount;
        private long inProgressSessionCount;
        private long overdueInProgressSessionCount;
        private long failedOperationsLast7Days;
        private boolean risk;
        private double inProgressSubmissionRate;
        private String recentUploadSummary;
        private LocalDateTime recentUploadAt;
        private String recentErrorSummary;
        private LocalDateTime recentErrorAt;
    }
}
