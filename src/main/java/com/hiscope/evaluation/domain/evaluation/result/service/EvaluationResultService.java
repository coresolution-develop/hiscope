package com.hiscope.evaluation.domain.evaluation.result.service;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.employee.dto.EmployeeResponse;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.result.dto.EvaluationResultRowView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationResultService {

    private final EvaluationAssignmentRepository assignmentRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationResponseItemRepository responseItemRepository;
    private final EmployeeService employeeService;

    public Page<EvaluationResultRowView> findResultRows(Long orgId,
                                                        Long sessionId,
                                                        String keyword,
                                                        Long departmentId,
                                                        String sortBy,
                                                        String sortDir,
                                                        Pageable pageable) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository
                .findByOrganizationIdAndSessionId(orgId, sessionId);
        if (assignments.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> assignmentIds = assignments.stream().map(EvaluationAssignment::getId).toList();
        List<EvaluationResponse> responses = responseRepository
                .findByOrganizationIdAndFinalSubmitTrueAndAssignmentIdIn(orgId, assignmentIds);
        Map<Long, EvaluationResponse> responseByAssignmentId = responses.stream()
                .collect(Collectors.toMap(EvaluationResponse::getAssignmentId, r -> r));
        Map<Long, List<EvaluationResponseItem>> itemsByResponseId = responses.isEmpty()
                ? Map.of()
                : responseItemRepository.findByResponseIdIn(responses.stream().map(EvaluationResponse::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(EvaluationResponseItem::getResponseId));

        Map<Long, EmployeeResponse> employeeMap = employeeService.findAll(orgId).stream()
                .collect(Collectors.toMap(EmployeeResponse::getId, e -> e));

        Map<Long, List<EvaluationAssignment>> byEvaluatee = assignments.stream()
                .collect(Collectors.groupingBy(EvaluationAssignment::getEvaluateeId));
        List<EvaluationResultRowView> rows = new ArrayList<>();
        byEvaluatee.forEach((evaluateeId, evaluateeAssignments) -> {
            EmployeeResponse evaluatee = employeeMap.get(evaluateeId);
            if (evaluatee == null) {
                return;
            }
            long totalEvaluatorCount = evaluateeAssignments.size();
            long submittedEvaluatorCount = evaluateeAssignments.stream().filter(EvaluationAssignment::isSubmitted).count();
            double submissionRate = totalEvaluatorCount == 0 ? 0.0
                    : Math.round((submittedEvaluatorCount * 1000.0) / totalEvaluatorCount) / 10.0;

            List<Double> responseAverages = evaluateeAssignments.stream()
                    .map(a -> responseByAssignmentId.get(a.getId()))
                    .filter(r -> r != null)
                    .map(r -> {
                        List<EvaluationResponseItem> items = itemsByResponseId.getOrDefault(r.getId(), List.of());
                        List<Integer> scores = items.stream()
                                .map(EvaluationResponseItem::getScoreValue)
                                .filter(score -> score != null)
                                .toList();
                        if (scores.isEmpty()) {
                            return null;
                        }
                        return scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                    })
                    .filter(avg -> avg != null)
                    .toList();
            Double averageScore = responseAverages.isEmpty()
                    ? null
                    : Math.round(responseAverages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100.0) / 100.0;

            rows.add(new EvaluationResultRowView(
                    evaluatee.getId(),
                    evaluatee.getName(),
                    evaluatee.getEmployeeNumber(),
                    evaluatee.getDepartmentId(),
                    evaluatee.getDepartmentName(),
                    totalEvaluatorCount,
                    submittedEvaluatorCount,
                    submissionRate,
                    averageScore
            ));
        });

        List<EvaluationResultRowView> filtered = rows.stream()
                .filter(row -> filterByKeyword(row, keyword))
                .filter(row -> departmentId == null || departmentId.equals(row.departmentId()))
                .sorted(buildComparator(sortBy, sortDir))
                .toList();

        int start = (int) pageable.getOffset();
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    public ResultSummary summarize(Long orgId, Long sessionId) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository.findByOrganizationIdAndSessionId(orgId, sessionId);
        long totalAssignments = assignments.size();
        long submittedAssignments = assignments.stream().filter(EvaluationAssignment::isSubmitted).count();
        long evaluateeCount = assignments.stream().map(EvaluationAssignment::getEvaluateeId).distinct().count();
        double submissionRate = totalAssignments == 0 ? 0.0
                : Math.round((submittedAssignments * 1000.0) / totalAssignments) / 10.0;
        return new ResultSummary(evaluateeCount, totalAssignments, submittedAssignments, submissionRate);
    }

    private boolean filterByKeyword(EvaluationResultRowView row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        String name = row.evaluateeName() != null ? row.evaluateeName().toLowerCase() : "";
        String number = row.employeeNumber() != null ? row.employeeNumber().toLowerCase() : "";
        return name.contains(normalized) || number.contains(normalized);
    }

    private Comparator<EvaluationResultRowView> buildComparator(String sortBy, String sortDir) {
        Comparator<EvaluationResultRowView> comparator;
        if ("averageScore".equals(sortBy)) {
            comparator = Comparator.comparing(row -> row.averageScore() != null ? row.averageScore() : -1.0);
        } else if ("submittedEvaluatorCount".equals(sortBy)) {
            comparator = Comparator.comparingLong(EvaluationResultRowView::submittedEvaluatorCount);
        } else if ("submissionRate".equals(sortBy)) {
            comparator = Comparator.comparingDouble(EvaluationResultRowView::submissionRate);
        } else {
            comparator = Comparator.comparing(row -> row.evaluateeName() != null ? row.evaluateeName() : "");
        }
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    public record ResultSummary(
            long evaluateeCount,
            long totalAssignments,
            long submittedAssignments,
            double submissionRate
    ) {
    }
}
