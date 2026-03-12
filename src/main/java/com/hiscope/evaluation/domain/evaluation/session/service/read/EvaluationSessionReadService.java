package com.hiscope.evaluation.domain.evaluation.session.service.read;

import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.AssignmentRowView;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.PendingEvaluatorRowView;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.SessionDetailView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationSessionReadService {

    private final EvaluationAssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;

    public SessionDetailView buildSessionDetail(Long orgId,
                                                Long sessionId,
                                                String assignmentKeyword,
                                                String assignmentStatus,
                                                String assignmentSortBy,
                                                String assignmentSortDir,
                                                int assignmentPage,
                                                int assignmentSize) {
        List<EvaluationAssignment> assignments = assignmentRepository.findByOrganizationIdAndSessionId(orgId, sessionId);
        Map<Long, com.hiscope.evaluation.domain.employee.dto.EmployeeResponse> employeeMap = employeeService.findAll(orgId).stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e));
        Map<Long, String> employeeNameMap = employeeMap.values().stream()
                .collect(Collectors.toMap(
                        com.hiscope.evaluation.domain.employee.dto.EmployeeResponse::getId,
                        com.hiscope.evaluation.domain.employee.dto.EmployeeResponse::getName
                ));

        List<AssignmentRowView> assignmentRows = assignments.stream()
                .map(assignment -> toAssignmentRow(assignment, employeeMap))
                .filter(row -> filterByAssignmentKeyword(row, assignmentKeyword))
                .filter(row -> filterByAssignmentStatus(row, assignmentStatus))
                .sorted(buildAssignmentComparator(assignmentSortBy, assignmentSortDir))
                .toList();

        int safeSize = Math.max(1, Math.min(assignmentSize, 100));
        int totalFiltered = assignmentRows.size();
        int totalPages = totalFiltered == 0 ? 1 : (int) Math.ceil(totalFiltered / (double) safeSize);
        int safePage = Math.max(0, Math.min(assignmentPage, totalPages - 1));
        int fromIndex = Math.min(safePage * safeSize, totalFiltered);
        int toIndex = Math.min(fromIndex + safeSize, totalFiltered);
        List<AssignmentRowView> pagedRows = assignmentRows.subList(fromIndex, toIndex);

        long submittedCount = assignments.stream().filter(EvaluationAssignment::isSubmitted).count();
        long pendingCount = assignments.size() - submittedCount;
        int progressRate = assignments.isEmpty() ? 0 : (int) ((submittedCount * 100) / assignments.size());

        List<PendingEvaluatorRowView> pendingEvaluators = assignments.stream()
                .collect(Collectors.groupingBy(EvaluationAssignment::getEvaluatorId))
                .entrySet().stream()
                .map(entry -> toPendingEvaluatorRow(entry.getKey(), entry.getValue(), employeeNameMap))
                .filter(row -> row.pendingCount() > 0)
                .sorted(Comparator.comparingLong(PendingEvaluatorRowView::pendingCount).reversed())
                .toList();

        return new SessionDetailView(
                pagedRows,
                totalFiltered,
                safePage,
                safeSize,
                totalPages,
                assignments.size(),
                submittedCount,
                pendingCount,
                progressRate,
                pendingEvaluators
        );
    }

    private AssignmentRowView toAssignmentRow(EvaluationAssignment assignment,
                                              Map<Long, com.hiscope.evaluation.domain.employee.dto.EmployeeResponse> employeeMap) {
        var evaluator = employeeMap.get(assignment.getEvaluatorId());
        var evaluatee = employeeMap.get(assignment.getEvaluateeId());
        String evaluatorName = evaluator != null ? evaluator.getName() : "직원#" + assignment.getEvaluatorId();
        String evaluatorEmployeeNumber = evaluator != null ? evaluator.getEmployeeNumber() : null;
        String evaluateeName = evaluatee != null ? evaluatee.getName() : "직원#" + assignment.getEvaluateeId();
        String evaluateeEmployeeNumber = evaluatee != null ? evaluatee.getEmployeeNumber() : null;
        return new AssignmentRowView(
                assignment.getId(),
                assignment.getEvaluatorId(),
                evaluatorName,
                evaluatorEmployeeNumber,
                assignment.getEvaluateeId(),
                evaluateeName,
                evaluateeEmployeeNumber,
                assignment.getStatus(),
                assignment.getSubmittedAt()
        );
    }

    private boolean filterByAssignmentKeyword(AssignmentRowView row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(row.evaluatorName(), normalized)
                || containsIgnoreCase(row.evaluateeName(), normalized)
                || containsIgnoreCase(row.evaluatorEmployeeNumber(), normalized)
                || containsIgnoreCase(row.evaluateeEmployeeNumber(), normalized);
    }

    private boolean filterByAssignmentStatus(AssignmentRowView row, String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        return status.equals(row.status());
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }

    private Comparator<AssignmentRowView> buildAssignmentComparator(String sortBy, String sortDir) {
        Comparator<AssignmentRowView> comparator = switch (sortBy) {
            case "evaluatorName" -> Comparator.comparing(AssignmentRowView::evaluatorName, String.CASE_INSENSITIVE_ORDER);
            case "evaluateeName" -> Comparator.comparing(AssignmentRowView::evaluateeName, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(AssignmentRowView::status, String.CASE_INSENSITIVE_ORDER);
            case "submittedAt" -> Comparator.comparing(
                    AssignmentRowView::submittedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(AssignmentRowView::id);
        };
        if (!"asc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private PendingEvaluatorRowView toPendingEvaluatorRow(Long evaluatorId,
                                                          List<EvaluationAssignment> evaluatorAssignments,
                                                          Map<Long, String> employeeNameMap) {
        long total = evaluatorAssignments.size();
        long submitted = evaluatorAssignments.stream().filter(EvaluationAssignment::isSubmitted).count();
        long pending = total - submitted;
        return new PendingEvaluatorRowView(
                evaluatorId,
                employeeNameMap.getOrDefault(evaluatorId, "직원#" + evaluatorId),
                pending,
                total
        );
    }
}
