package com.hiscope.evaluation.domain.dashboard.service;

import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.dashboard.dto.DashboardDto;
import com.hiscope.evaluation.domain.employee.dto.EmployeeResponse;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.upload.repository.UploadHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationAssignmentRepository assignmentRepository;
    private final UploadHistoryRepository uploadHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmployeeService employeeService;

    public DashboardDto getAdminDashboard(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);

        long totalEmployees = employeeRepository.countActiveByOrg(orgId);
        long totalDepts = departmentRepository.findByOrganizationIdAndActiveOrderByNameAsc(orgId, true).size();
        List<EvaluationSession> sessions = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        List<EvaluationSession> inProgressSessions = sessions.stream()
                .filter(s -> "IN_PROGRESS".equals(s.getStatus()))
                .toList();
        long activeSessions = inProgressSessions.size();

        List<DashboardDto.SessionProgress> progressList = sessions.stream()
                .limit(10)
                .map(session -> {
                    long total = assignmentRepository.countBySession(session.getId());
                    long submitted = assignmentRepository.countSubmittedBySession(session.getId());
                    double rate = total > 0 ? Math.round((double) submitted / total * 1000) / 10.0 : 0.0;
                    return DashboardDto.SessionProgress.builder()
                            .sessionId(session.getId())
                            .sessionName(session.getName())
                            .status(session.getStatus())
                            .totalAssignments(total)
                            .submittedAssignments(submitted)
                            .submissionRate(rate)
                            .build();
                }).toList();

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

        List<DashboardDto.UploadActivity> recentUploads = uploadHistoryRepository
                .findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, 5))
                .stream()
                .map(h -> DashboardDto.UploadActivity.builder()
                        .uploadType(h.getUploadType())
                        .fileName(h.getFileName())
                        .status(h.getStatus())
                        .totalRows(h.getTotalRows())
                        .successRows(h.getSuccessRows())
                        .failRows(h.getFailRows())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();

        List<DashboardDto.AuditActivity> recentActivities = auditLogRepository
                .findByOrganizationIdOrderByOccurredAtDesc(orgId, PageRequest.of(0, 8))
                .stream()
                .filter(log -> !Set.of("AUTH_LOGIN", "AUTH_LOGOUT").contains(log.getAction()))
                .map(log -> DashboardDto.AuditActivity.builder()
                        .action(log.getAction())
                        .outcome(log.getOutcome())
                        .detail(log.getDetail())
                        .actorLoginId(log.getActorLoginId())
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
                .sessionProgressList(progressList)
                .departmentProgressList(departmentProgressList)
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
}
