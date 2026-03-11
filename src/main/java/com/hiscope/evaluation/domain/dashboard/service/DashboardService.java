package com.hiscope.evaluation.domain.dashboard.service;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.dashboard.dto.DashboardDto;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationAssignmentRepository assignmentRepository;

    public DashboardDto getAdminDashboard(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);

        long totalEmployees = employeeRepository.countActiveByOrg(orgId);
        long totalDepts = departmentRepository.findByOrganizationIdAndActiveOrderByNameAsc(orgId, true).size();
        List<EvaluationSession> sessions = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        long activeSessions = sessions.stream().filter(s -> "IN_PROGRESS".equals(s.getStatus())).count();

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

        return DashboardDto.builder()
                .totalEmployees(totalEmployees)
                .totalDepartments(totalDepts)
                .activeSessions(activeSessions)
                .sessionProgressList(progressList)
                .build();
    }
}
