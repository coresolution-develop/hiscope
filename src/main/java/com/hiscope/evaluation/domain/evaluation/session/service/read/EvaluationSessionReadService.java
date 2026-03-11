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

    public SessionDetailView buildSessionDetail(Long orgId, Long sessionId) {
        List<EvaluationAssignment> assignments = assignmentRepository.findBySessionId(sessionId);
        Map<Long, String> employeeNameMap = employeeService.findAll(orgId).stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getName()));

        List<AssignmentRowView> assignmentRows = assignments.stream()
                .map(assignment -> toAssignmentRow(assignment, employeeNameMap))
                .toList();

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
                assignmentRows,
                assignments.size(),
                submittedCount,
                pendingCount,
                progressRate,
                pendingEvaluators
        );
    }

    private AssignmentRowView toAssignmentRow(EvaluationAssignment assignment, Map<Long, String> employeeNameMap) {
        return new AssignmentRowView(
                assignment.getId(),
                assignment.getEvaluatorId(),
                employeeNameMap.getOrDefault(assignment.getEvaluatorId(), "직원#" + assignment.getEvaluatorId()),
                assignment.getEvaluateeId(),
                employeeNameMap.getOrDefault(assignment.getEvaluateeId(), "직원#" + assignment.getEvaluateeId()),
                assignment.getStatus(),
                assignment.getSubmittedAt()
        );
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
