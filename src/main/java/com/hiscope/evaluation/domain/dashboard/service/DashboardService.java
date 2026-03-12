package com.hiscope.evaluation.domain.dashboard.service;

import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.dashboard.dto.DashboardDto;
import com.hiscope.evaluation.domain.dashboard.dto.SuperAdminDashboardDto;
import com.hiscope.evaluation.domain.employee.dto.EmployeeResponse;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.upload.repository.UploadHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OrganizationRepository organizationRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationAssignmentRepository assignmentRepository;
    private final UploadHistoryRepository uploadHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmployeeService employeeService;

    public SuperAdminDashboardDto getSuperAdminDashboard() {
        List<Organization> organizations = organizationRepository.findAllByOrderByCreatedAtDesc();
        long activeCount = organizations.stream().filter(org -> "ACTIVE".equals(org.getStatus())).count();
        LocalDate today = LocalDate.now();

        List<SuperAdminDashboardDto.OrganizationOperationSummary> summaries = organizations.stream()
                .map(org -> {
                    long activeUsers = employeeRepository.countActiveByOrg(org.getId());
                    long totalSessions = sessionRepository.countByOrganizationId(org.getId());
                    long inProgressSessions = sessionRepository.countByOrganizationIdAndStatus(org.getId(), "IN_PROGRESS");
                    long overdueInProgressSessions = sessionRepository
                            .findByOrganizationIdAndStatusOrderByCreatedAtDesc(org.getId(), "IN_PROGRESS")
                            .stream()
                            .filter(s -> s.getEndDate() != null && s.getEndDate().isBefore(today))
                            .count();
                    long failedOperationsLast7Days = auditLogRepository
                            .countByOrganizationIdAndOutcomeAndOccurredAtGreaterThanEqual(org.getId(), "FAIL", LocalDateTime.now().minusDays(7));
                    long inProgressTotalAssignments = assignmentRepository
                            .countByOrganizationIdAndSessionStatus(org.getId(), "IN_PROGRESS");
                    long inProgressSubmittedAssignments = assignmentRepository
                            .countSubmittedByOrganizationIdAndSessionStatus(org.getId(), "IN_PROGRESS");
                    double submissionRate = inProgressTotalAssignments == 0 ? 0.0
                            : Math.round((inProgressSubmittedAssignments * 1000.0) / inProgressTotalAssignments) / 10.0;
                    boolean risk = overdueInProgressSessions > 0 || failedOperationsLast7Days > 0;

                    var recentUpload = uploadHistoryRepository.findTopByOrganizationIdOrderByCreatedAtDesc(org.getId()).orElse(null);
                    String recentUploadSummary = recentUpload == null ? "-"
                            : resolveUploadTypeLabel(recentUpload.getUploadType())
                            + " " + resolveUploadStatusLabel(recentUpload.getStatus())
                            + " (" + recentUpload.getSuccessRows() + "/" + recentUpload.getTotalRows() + ")";
                    LocalDateTime recentUploadAt = recentUpload == null ? null : recentUpload.getCreatedAt();

                    var recentError = auditLogRepository
                            .findTopByOrganizationIdAndOutcomeOrderByOccurredAtDesc(org.getId(), "FAIL")
                            .orElse(null);
                    String recentErrorSummary = recentError == null ? "최근 오류 없음"
                            : resolveActionLabel(recentError.getAction()) + " 실패";
                    LocalDateTime recentErrorAt = recentError == null ? null : recentError.getOccurredAt();

                    return SuperAdminDashboardDto.OrganizationOperationSummary.builder()
                            .organizationId(org.getId())
                            .organizationName(org.getName())
                            .organizationCode(org.getCode())
                            .status(org.getStatus())
                            .activeUserCount(activeUsers)
                            .totalSessionCount(totalSessions)
                            .inProgressSessionCount(inProgressSessions)
                            .overdueInProgressSessionCount(overdueInProgressSessions)
                            .failedOperationsLast7Days(failedOperationsLast7Days)
                            .risk(risk)
                            .inProgressSubmissionRate(submissionRate)
                            .recentUploadSummary(recentUploadSummary)
                            .recentUploadAt(recentUploadAt)
                            .recentErrorSummary(recentErrorSummary)
                            .recentErrorAt(recentErrorAt)
                            .build();
                })
                .sorted(java.util.Comparator.comparing(SuperAdminDashboardDto.OrganizationOperationSummary::isRisk).reversed()
                        .thenComparingLong(SuperAdminDashboardDto.OrganizationOperationSummary::getFailedOperationsLast7Days).reversed()
                        .thenComparingLong(SuperAdminDashboardDto.OrganizationOperationSummary::getOverdueInProgressSessionCount).reversed()
                        .thenComparing(SuperAdminDashboardDto.OrganizationOperationSummary::getOrganizationName,
                                java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        long riskOrganizations = summaries.stream().filter(SuperAdminDashboardDto.OrganizationOperationSummary::isRisk).count();
        long failedOperationsLast7Days = summaries.stream()
                .mapToLong(SuperAdminDashboardDto.OrganizationOperationSummary::getFailedOperationsLast7Days)
                .sum();

        return SuperAdminDashboardDto.builder()
                .totalOrganizations(organizations.size())
                .activeOrganizations(activeCount)
                .inactiveOrganizations(organizations.size() - activeCount)
                .riskOrganizations(riskOrganizations)
                .failedOperationsLast7Days(failedOperationsLast7Days)
                .organizationSummaries(summaries)
                .build();
    }

    public DashboardDto getAdminDashboard(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);

        long totalEmployees = employeeRepository.countActiveByOrg(orgId);
        long totalDepts = departmentRepository.findByOrganizationIdAndActiveOrderByNameAsc(orgId, true).size();
        List<EvaluationSession> sessions = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        List<EvaluationSession> inProgressSessions = sessions.stream()
                .filter(s -> "IN_PROGRESS".equals(s.getStatus()))
                .toList();
        LocalDate today = LocalDate.now();
        long activeSessions = inProgressSessions.size();
        long deadlineSoonSessions = inProgressSessions.stream()
                .filter(s -> s.getEndDate() != null)
                .filter(s -> !s.getEndDate().isBefore(today))
                .filter(s -> !s.getEndDate().isAfter(today.plusDays(7)))
                .count();
        long overdueInProgressSessions = inProgressSessions.stream()
                .filter(s -> s.getEndDate() != null && s.getEndDate().isBefore(today))
                .count();
        long failedOperationsLast7Days = auditLogRepository
                .countByOrganizationIdAndOutcomeAndOccurredAtGreaterThanEqual(orgId, "FAIL", LocalDateTime.now().minusDays(7));

        List<DashboardDto.SessionProgress> progressList = sessions.stream()
                .limit(10)
                .map(session -> {
                    long total = assignmentRepository.countBySession(session.getId());
                    long submitted = assignmentRepository.countSubmittedBySession(session.getId());
                    double rate = total > 0 ? Math.round((double) submitted / total * 1000) / 10.0 : 0.0;
                    Long daysUntilDeadline = session.getEndDate() == null ? null
                            : java.time.temporal.ChronoUnit.DAYS.between(today, session.getEndDate());
                    return DashboardDto.SessionProgress.builder()
                            .sessionId(session.getId())
                            .sessionName(session.getName())
                            .status(session.getStatus())
                            .totalAssignments(total)
                            .submittedAssignments(submitted)
                            .submissionRate(rate)
                            .endDate(session.getEndDate())
                            .daysUntilDeadline(daysUntilDeadline)
                            .build();
                }).toList();

        List<DashboardDto.DeadlineAlert> deadlineAlerts = inProgressSessions.stream()
                .filter(s -> s.getEndDate() != null)
                .map(s -> {
                    long total = assignmentRepository.countBySession(s.getId());
                    long submitted = assignmentRepository.countSubmittedBySession(s.getId());
                    double rate = total > 0 ? Math.round((double) submitted / total * 1000) / 10.0 : 0.0;
                    long daysUntilDeadline = java.time.temporal.ChronoUnit.DAYS.between(today, s.getEndDate());
                    return DashboardDto.DeadlineAlert.builder()
                            .sessionId(s.getId())
                            .sessionName(s.getName())
                            .endDate(s.getEndDate())
                            .daysUntilDeadline(daysUntilDeadline)
                            .submissionRate(rate)
                            .build();
                })
                .sorted(java.util.Comparator.comparingLong(DashboardDto.DeadlineAlert::getDaysUntilDeadline))
                .limit(5)
                .toList();

        List<EvaluationAssignment> inProgressAssignments = inProgressSessions.stream()
                .flatMap(session -> assignmentRepository.findBySessionId(session.getId()).stream())
                .toList();
        long totalAssignments = inProgressAssignments.size();
        long submittedAssignments = inProgressAssignments.stream().filter(EvaluationAssignment::isSubmitted).count();
        long pendingAssignments = totalAssignments - submittedAssignments;
        double overallSubmissionRate = totalAssignments == 0 ? 0.0
                : Math.round((submittedAssignments * 1000.0) / totalAssignments) / 10.0;
        long totalEvaluatees = inProgressAssignments.stream().map(EvaluationAssignment::getEvaluateeId).distinct().count();

        Map<Long, EmployeeResponse> employeeMap = employeeService.findAll(orgId).stream()
                .collect(Collectors.toMap(EmployeeResponse::getId, e -> e));
        List<DashboardDto.DepartmentProgress> departmentProgressList = inProgressAssignments.stream()
                .collect(Collectors.groupingBy(a -> {
                    EmployeeResponse e = employeeMap.get(a.getEvaluateeId());
                    return e != null ? e.getDepartmentId() : null;
                }))
                .entrySet().stream()
                .map(entry -> {
                    Long departmentId = entry.getKey();
                    List<EvaluationAssignment> deptAssignments = entry.getValue();
                    long deptTotal = deptAssignments.size();
                    long deptSubmitted = deptAssignments.stream().filter(EvaluationAssignment::isSubmitted).count();
                    long deptPending = deptTotal - deptSubmitted;
                    double deptRate = deptTotal == 0 ? 0.0
                            : Math.round((deptSubmitted * 1000.0) / deptTotal) / 10.0;
                    String departmentName = resolveDepartmentName(departmentId, deptAssignments, employeeMap);
                    return DashboardDto.DepartmentProgress.builder()
                            .departmentId(departmentId)
                            .departmentName(departmentName)
                            .totalAssignments(deptTotal)
                            .submittedAssignments(deptSubmitted)
                            .pendingAssignments(deptPending)
                            .submissionRate(deptRate)
                            .build();
                })
                .sorted(java.util.Comparator.comparingDouble(DashboardDto.DepartmentProgress::getSubmissionRate))
                .limit(10)
                .toList();

        List<DashboardDto.PendingEvaluatorProgress> pendingEvaluatorProgressList = inProgressAssignments.stream()
                .collect(Collectors.groupingBy(EvaluationAssignment::getEvaluatorId))
                .entrySet().stream()
                .map(entry -> {
                    Long evaluatorId = entry.getKey();
                    List<EvaluationAssignment> evaluatorAssignments = entry.getValue();
                    long evaluatorTotal = evaluatorAssignments.size();
                    long evaluatorPending = evaluatorAssignments.stream().filter(a -> !a.isSubmitted()).count();
                    if (evaluatorPending == 0) {
                        return null;
                    }
                    long evaluatorSubmitted = evaluatorTotal - evaluatorPending;
                    double evaluatorSubmissionRate = evaluatorTotal == 0 ? 0.0
                            : Math.round((evaluatorSubmitted * 1000.0) / evaluatorTotal) / 10.0;
                    EmployeeResponse evaluator = employeeMap.get(evaluatorId);
                    return DashboardDto.PendingEvaluatorProgress.builder()
                            .evaluatorId(evaluatorId)
                            .evaluatorName(evaluator != null ? evaluator.getName() : "알 수 없음")
                            .evaluatorEmployeeNumber(evaluator != null ? evaluator.getEmployeeNumber() : null)
                            .evaluatorDepartmentName(evaluator != null ? evaluator.getDepartmentName() : null)
                            .totalAssignments(evaluatorTotal)
                            .pendingAssignments(evaluatorPending)
                            .submissionRate(evaluatorSubmissionRate)
                            .build();
                })
                .filter(row -> row != null)
                .sorted(java.util.Comparator.comparingLong(DashboardDto.PendingEvaluatorProgress::getPendingAssignments).reversed()
                        .thenComparingDouble(DashboardDto.PendingEvaluatorProgress::getSubmissionRate)
                        .thenComparing(DashboardDto.PendingEvaluatorProgress::getEvaluatorName,
                                java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(10)
                .toList();

        List<DashboardDto.UploadActivity> recentUploads = uploadHistoryRepository
                .findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, 5))
                .stream()
                .map(h -> DashboardDto.UploadActivity.builder()
                        .uploadType(h.getUploadType())
                        .uploadTypeLabel(resolveUploadTypeLabel(h.getUploadType()))
                        .fileName(h.getFileName())
                        .status(h.getStatus())
                        .statusLabel(resolveUploadStatusLabel(h.getStatus()))
                        .totalRows(h.getTotalRows())
                        .successRows(h.getSuccessRows())
                        .failRows(h.getFailRows())
                        .historyLink("/admin/uploads/history")
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();

        List<DashboardDto.AuditActivity> recentActivities = auditLogRepository
                .findByOrganizationIdOrderByOccurredAtDesc(orgId, PageRequest.of(0, 8))
                .stream()
                .filter(log -> !Set.of("AUTH_LOGIN", "AUTH_LOGOUT").contains(log.getAction()))
                .map(log -> DashboardDto.AuditActivity.builder()
                        .action(log.getAction())
                        .actionLabel(resolveActionLabel(log.getAction()))
                        .outcome(log.getOutcome())
                        .outcomeLabel(resolveOutcomeLabel(log.getOutcome()))
                        .detail(log.getDetail())
                        .actorLoginId(log.getActorLoginId())
                        .targetType(log.getTargetType())
                        .targetId(log.getTargetId())
                        .targetLabel(resolveTargetLabel(log.getTargetType(), log.getTargetId()))
                        .targetLink(resolveTargetLink(log.getTargetType(), log.getTargetId()))
                        .occurredAt(log.getOccurredAt())
                        .build())
                .toList();

        return DashboardDto.builder()
                .totalEmployees(totalEmployees)
                .totalDepartments(totalDepts)
                .activeSessions(activeSessions)
                .totalEvaluatees(totalEvaluatees)
                .pendingAssignments(pendingAssignments)
                .overallSubmissionRate(overallSubmissionRate)
                .deadlineSoonSessions(deadlineSoonSessions)
                .overdueInProgressSessions(overdueInProgressSessions)
                .failedOperationsLast7Days(failedOperationsLast7Days)
                .sessionProgressList(progressList)
                .departmentProgressList(departmentProgressList)
                .pendingEvaluatorProgressList(pendingEvaluatorProgressList)
                .deadlineAlerts(deadlineAlerts)
                .recentUploads(recentUploads)
                .recentActivities(recentActivities)
                .build();
    }

    private String resolveDepartmentName(Long departmentId,
                                         List<EvaluationAssignment> assignments,
                                         Map<Long, EmployeeResponse> employeeMap) {
        if (departmentId == null) {
            return "미지정";
        }
        for (EvaluationAssignment assignment : assignments) {
            EmployeeResponse evaluatee = employeeMap.get(assignment.getEvaluateeId());
            if (evaluatee != null && evaluatee.getDepartmentName() != null) {
                return evaluatee.getDepartmentName();
            }
        }
        return "부서#" + departmentId;
    }

    private String resolveUploadTypeLabel(String uploadType) {
        if ("DEPARTMENT".equals(uploadType)) {
            return "부서";
        }
        if ("EMPLOYEE".equals(uploadType)) {
            return "사용자";
        }
        if ("QUESTION".equals(uploadType)) {
            return "평가 항목";
        }
        return uploadType;
    }

    private String resolveUploadStatusLabel(String status) {
        if ("SUCCESS".equals(status)) {
            return "성공";
        }
        if ("PARTIAL".equals(status)) {
            return "부분성공";
        }
        if ("FAILED".equals(status)) {
            return "실패";
        }
        return status;
    }

    private String resolveActionLabel(String action) {
        if (action == null) {
            return "-";
        }
        return switch (action) {
            case "ORG_CREATE" -> "기관 생성";
            case "ORG_STATUS_UPDATE" -> "기관 상태 변경";
            case "ORG_ADMIN_CREATE" -> "기관 관리자 생성";
            case "ORG_ADMIN_UPDATE" -> "기관 관리자 수정";
            case "ORG_ADMIN_STATUS_UPDATE" -> "기관 관리자 상태 변경";
            case "ORG_ADMIN_PASSWORD_RESET" -> "기관 관리자 비밀번호 초기화";
            case "DEPT_CREATE" -> "부서 생성";
            case "DEPT_UPDATE" -> "부서 수정";
            case "DEPT_DELETE" -> "부서 삭제";
            case "EMP_CREATE" -> "사용자 생성";
            case "EMP_UPDATE" -> "사용자 수정";
            case "EMP_ACTIVATE" -> "사용자 활성화";
            case "EMP_DEACTIVATE" -> "사용자 비활성화";
            case "DEPT_UPLOAD" -> "부서 업로드";
            case "EMP_UPLOAD" -> "사용자 업로드";
            case "EVAL_QUESTION_UPLOAD" -> "평가 항목 업로드";
            case "UPLOAD_ERROR_DOWNLOAD" -> "업로드 실패내역 다운로드";
            case "EVAL_TEMPLATE_CREATE" -> "평가 템플릿 생성";
            case "EVAL_TEMPLATE_UPDATE" -> "평가 템플릿 수정";
            case "EVAL_TEMPLATE_DELETE" -> "평가 템플릿 삭제";
            case "EVAL_QUESTION_ADD" -> "평가 문항 생성";
            case "EVAL_QUESTION_UPDATE" -> "평가 문항 수정";
            case "EVAL_QUESTION_DELETE" -> "평가 문항 삭제";
            case "EVAL_SESSION_CREATE" -> "평가 세션 생성";
            case "EVAL_SESSION_UPDATE" -> "평가 세션 수정";
            case "EVAL_SESSION_DELETE" -> "평가 세션 삭제";
            case "EVAL_SESSION_CLONE" -> "평가 세션 복제";
            case "EVAL_SESSION_START" -> "평가 세션 시작";
            case "EVAL_SESSION_CLOSE" -> "평가 세션 종료";
            case "EVAL_RELATION_AUTO_GENERATE" -> "관계 자동생성";
            case "EVAL_RELATION_ADD_MANUAL" -> "관계 수동 추가";
            case "EVAL_RELATION_DELETE" -> "관계 수동 삭제";
            case "EVAL_RESPONSE_SAVE_DRAFT" -> "평가 임시저장";
            case "EVAL_RESPONSE_SUBMIT" -> "평가 제출";
            case "EVAL_RESULT_PENDING_DOWNLOAD" -> "미제출자 다운로드";
            case "EVAL_RESULT_EVALUATEE_DOWNLOAD" -> "대상자별 결과 다운로드";
            case "EVAL_RESULT_DEPARTMENT_DOWNLOAD" -> "부서별 결과 다운로드";
            case "EVAL_RESULT_ASSIGNMENT_DOWNLOAD" -> "배정별 결과 다운로드";
            case "ADMIN_SETTINGS_UPDATE" -> "운영 설정 변경";
            default -> action;
        };
    }

    private String resolveOutcomeLabel(String outcome) {
        if (outcome == null) {
            return "-";
        }
        return "SUCCESS".equals(outcome) ? "성공" : "실패";
    }

    private String resolveTargetLabel(String targetType, String targetId) {
        if (targetType == null || targetType.isBlank()) {
            return "-";
        }
        if (targetId == null || targetId.isBlank()) {
            return targetType;
        }
        return targetType + "#" + targetId;
    }

    private String resolveTargetLink(String targetType, String targetId) {
        if (targetType == null || targetType.isBlank()) {
            return null;
        }
        if ("SESSION".equals(targetType) && isNumeric(targetId)) {
            return "/admin/evaluation/sessions/" + targetId;
        }
        if ("EVALUATION_SESSION".equals(targetType) && isNumeric(targetId)) {
            return "/admin/evaluation/sessions/" + targetId;
        }
        if ("TEMPLATE".equals(targetType) && isNumeric(targetId)) {
            return "/admin/evaluation/templates/" + targetId + "/questions";
        }
        if ("EVALUATION_TEMPLATE".equals(targetType)) {
            return "/admin/evaluation/templates";
        }
        if ("UPLOAD".equals(targetType)) {
            return "/admin/uploads/history";
        }
        if ("UPLOAD_HISTORY".equals(targetType)) {
            return "/admin/uploads/history";
        }
        if ("DEPARTMENT".equals(targetType)) {
            return "/admin/departments";
        }
        if ("EMPLOYEE".equals(targetType)) {
            return "/admin/employees";
        }
        if ("ACCOUNT".equals(targetType)) {
            return "/super-admin/organizations";
        }
        if ("ORGANIZATION".equals(targetType)) {
            return "/super-admin/organizations";
        }
        if ("EVALUATION_RESULT".equals(targetType) || "PENDING_SUBMISSIONS".equals(targetType)) {
            return "/admin/evaluation/results";
        }
        if ("EVAL_SESSION".equals(targetType)) {
            return "/admin/evaluation/results";
        }
        if ("AUDIT_LOG".equals(targetType)) {
            return "/admin/audit";
        }
        return null;
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.chars().allMatch(Character::isDigit);
    }
}
