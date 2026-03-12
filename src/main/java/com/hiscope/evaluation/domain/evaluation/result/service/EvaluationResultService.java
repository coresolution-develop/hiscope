package com.hiscope.evaluation.domain.evaluation.result.service;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.common.util.CsvUtils;
import com.hiscope.evaluation.domain.employee.dto.EmployeeResponse;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.result.dto.EvaluationResultRowView;
import com.hiscope.evaluation.domain.evaluation.result.dto.PendingSubmissionRowView;
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
        List<EvaluationResultRowView> filtered = findResultRowsForExport(orgId, sessionId, keyword, departmentId, sortBy, sortDir);
        int start = (int) pageable.getOffset();
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    public List<EvaluationResultRowView> findResultRowsForExport(Long orgId,
                                                                 Long sessionId,
                                                                 String keyword,
                                                                 Long departmentId,
                                                                 String sortBy,
                                                                 String sortDir) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository
                .findByOrganizationIdAndSessionId(orgId, sessionId);
        if (assignments.isEmpty()) {
            return List.of();
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
        return filtered;
    }

    public EvaluateeResultCsv buildEvaluateeResultCsv(Long orgId,
                                                      Long sessionId,
                                                      String keyword,
                                                      Long departmentId,
                                                      String sortBy,
                                                      String sortDir) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationResultRowView> rows = findResultRowsForExport(orgId, sessionId, keyword, departmentId, sortBy, sortDir);
        StringBuilder csv = new StringBuilder();
        csv.append("evaluatee_name,employee_number,department,submitted_count,total_count,submission_rate,average_score\n");
        for (EvaluationResultRowView row : rows) {
            csv.append(CsvUtils.escape(row.evaluateeName())).append(',')
                    .append(CsvUtils.escape(row.employeeNumber())).append(',')
                    .append(CsvUtils.escape(row.departmentName())).append(',')
                    .append(row.submittedEvaluatorCount()).append(',')
                    .append(row.totalEvaluatorCount()).append(',')
                    .append(row.submissionRate()).append(',')
                    .append(row.averageScore() != null ? row.averageScore() : "")
                    .append('\n');
        }
        return new EvaluateeResultCsv("evaluatee_results_session_" + sessionId + ".csv", csv.toString(), rows.size());
    }

    public DepartmentResultCsv buildDepartmentResultCsv(Long orgId,
                                                        Long sessionId,
                                                        String keyword,
                                                        Long departmentId,
                                                        String sortBy,
                                                        String sortDir) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationResultRowView> rows = findResultRowsForExport(orgId, sessionId, keyword, departmentId, sortBy, sortDir);
        Map<String, List<EvaluationResultRowView>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> row.departmentName() != null ? row.departmentName() : "미지정"));

        StringBuilder csv = new StringBuilder();
        csv.append("department,evaluatee_count,submitted_count,total_count,submission_rate,average_score\n");
        for (var entry : grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String deptName = entry.getKey();
            List<EvaluationResultRowView> deptRows = entry.getValue();
            long evaluateeCount = deptRows.size();
            long submitted = deptRows.stream().mapToLong(EvaluationResultRowView::submittedEvaluatorCount).sum();
            long total = deptRows.stream().mapToLong(EvaluationResultRowView::totalEvaluatorCount).sum();
            double submissionRate = total == 0 ? 0.0 : Math.round((submitted * 1000.0) / total) / 10.0;
            List<Double> averages = deptRows.stream()
                    .map(EvaluationResultRowView::averageScore)
                    .filter(score -> score != null)
                    .toList();
            Double averageScore = averages.isEmpty()
                    ? null
                    : Math.round(averages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100.0) / 100.0;
            csv.append(CsvUtils.escape(deptName)).append(',')
                    .append(evaluateeCount).append(',')
                    .append(submitted).append(',')
                    .append(total).append(',')
                    .append(submissionRate).append(',')
                    .append(averageScore != null ? averageScore : "")
                    .append('\n');
        }
        return new DepartmentResultCsv("department_results_session_" + sessionId + ".csv", csv.toString(), grouped.size());
    }

    public AssignmentResultCsv buildAssignmentResultCsv(Long orgId,
                                                        Long sessionId,
                                                        String keyword,
                                                        Long departmentId,
                                                        String sortBy,
                                                        String sortDir) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository.findByOrganizationIdAndSessionId(orgId, sessionId);
        if (assignments.isEmpty()) {
            return new AssignmentResultCsv("assignment_results_session_" + sessionId + ".csv",
                    "evaluator_name,evaluator_employee_number,evaluator_department,evaluatee_name,evaluatee_employee_number,evaluatee_department,status,submitted_at,average_score\n",
                    0);
        }

        Map<Long, EmployeeResponse> employeeMap = employeeService.findAll(orgId).stream()
                .collect(Collectors.toMap(EmployeeResponse::getId, e -> e));
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

        List<AssignmentResultRow> filtered = assignments.stream()
                .map(assignment -> toAssignmentResultRow(assignment, employeeMap, responseByAssignmentId, itemsByResponseId))
                .filter(row -> row != null)
                .filter(row -> filterAssignmentByKeyword(row, keyword))
                .filter(row -> departmentId == null || departmentId.equals(row.evaluateeDepartmentId()))
                .sorted(buildAssignmentComparator(sortBy, sortDir))
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append("evaluator_name,evaluator_employee_number,evaluator_department,evaluatee_name,evaluatee_employee_number,evaluatee_department,status,submitted_at,average_score\n");
        for (AssignmentResultRow row : filtered) {
            csv.append(CsvUtils.escape(row.evaluatorName())).append(',')
                    .append(CsvUtils.escape(row.evaluatorEmployeeNumber())).append(',')
                    .append(CsvUtils.escape(row.evaluatorDepartmentName())).append(',')
                    .append(CsvUtils.escape(row.evaluateeName())).append(',')
                    .append(CsvUtils.escape(row.evaluateeEmployeeNumber())).append(',')
                    .append(CsvUtils.escape(row.evaluateeDepartmentName())).append(',')
                    .append(CsvUtils.escape(row.status())).append(',')
                    .append(CsvUtils.escape(row.submittedAt() != null ? row.submittedAt().toString() : ""))
                    .append(',')
                    .append(row.averageScore() != null ? row.averageScore() : "")
                    .append('\n');
        }
        return new AssignmentResultCsv("assignment_results_session_" + sessionId + ".csv", csv.toString(), filtered.size());
    }

    public ResultSummary summarize(Long orgId, Long sessionId) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository.findByOrganizationIdAndSessionId(orgId, sessionId);
        long totalAssignments = assignments.size();
        long submittedAssignments = assignments.stream().filter(EvaluationAssignment::isSubmitted).count();
        long pendingAssignments = totalAssignments - submittedAssignments;
        long evaluateeCount = assignments.stream().map(EvaluationAssignment::getEvaluateeId).distinct().count();
        double submissionRate = totalAssignments == 0 ? 0.0
                : Math.round((submittedAssignments * 1000.0) / totalAssignments) / 10.0;
        return new ResultSummary(evaluateeCount, totalAssignments, submittedAssignments, pendingAssignments, submissionRate);
    }

    public List<PendingSubmissionRowView> findPendingRows(Long orgId,
                                                          Long sessionId,
                                                          String keyword,
                                                          Long departmentId,
                                                          String sortBy,
                                                          String sortDir,
                                                          int limit) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository.findByOrganizationIdAndSessionId(orgId, sessionId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Map<Long, EmployeeResponse> employeeMap = employeeService.findAll(orgId).stream()
                .collect(Collectors.toMap(EmployeeResponse::getId, e -> e));

        return assignments.stream()
                .filter(a -> !a.isSubmitted())
                .map(a -> mapToPendingRow(a, employeeMap))
                .filter(row -> row != null)
                .filter(row -> filterPendingByKeyword(row, keyword))
                .filter(row -> departmentId == null || departmentId.equals(row.evaluateeDepartmentId()))
                .sorted(buildPendingComparator(sortBy, sortDir))
                .limit(Math.max(limit, 1))
                .toList();
    }

    public PendingSubmissionCsv buildPendingSubmissionCsv(Long orgId,
                                                          Long sessionId,
                                                          String keyword,
                                                          Long departmentId,
                                                          String sortBy,
                                                          String sortDir) {
        SecurityUtils.checkOrgAccess(orgId);
        List<PendingSubmissionRowView> rows = findPendingRows(
                orgId,
                sessionId,
                keyword,
                departmentId,
                sortBy,
                sortDir,
                Integer.MAX_VALUE
        );
        StringBuilder csv = new StringBuilder();
        csv.append("evaluator_name,evaluator_employee_number,evaluator_department,evaluatee_name,evaluatee_employee_number,evaluatee_department,assigned_at\n");
        for (PendingSubmissionRowView row : rows) {
            csv.append(CsvUtils.escape(row.evaluatorName())).append(',')
                    .append(CsvUtils.escape(row.evaluatorEmployeeNumber())).append(',')
                    .append(CsvUtils.escape(row.evaluatorDepartmentName())).append(',')
                    .append(CsvUtils.escape(row.evaluateeName())).append(',')
                    .append(CsvUtils.escape(row.evaluateeEmployeeNumber())).append(',')
                    .append(CsvUtils.escape(row.evaluateeDepartmentName())).append(',')
                    .append(CsvUtils.escape(row.assignedAt() != null ? row.assignedAt().toString() : ""))
                    .append('\n');
        }
        String filename = "pending_submitters_session_" + sessionId + ".csv";
        return new PendingSubmissionCsv(filename, csv.toString(), rows.size());
    }

    private PendingSubmissionRowView mapToPendingRow(EvaluationAssignment assignment, Map<Long, EmployeeResponse> employeeMap) {
        EmployeeResponse evaluator = employeeMap.get(assignment.getEvaluatorId());
        EmployeeResponse evaluatee = employeeMap.get(assignment.getEvaluateeId());
        if (evaluator == null || evaluatee == null) {
            return null;
        }
        return new PendingSubmissionRowView(
                evaluator.getId(),
                evaluator.getName(),
                evaluator.getEmployeeNumber(),
                evaluator.getDepartmentId(),
                evaluator.getDepartmentName(),
                evaluatee.getId(),
                evaluatee.getName(),
                evaluatee.getEmployeeNumber(),
                evaluatee.getDepartmentId(),
                evaluatee.getDepartmentName(),
                assignment.getCreatedAt()
        );
    }

    private boolean filterPendingByKeyword(PendingSubmissionRowView row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsText(row.evaluatorName(), normalized)
                || containsText(row.evaluatorEmployeeNumber(), normalized)
                || containsText(row.evaluateeName(), normalized)
                || containsText(row.evaluateeEmployeeNumber(), normalized);
    }

    private boolean containsText(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }

    private String normalizeSortText(String value) {
        return value == null ? "" : value;
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

    private Comparator<PendingSubmissionRowView> buildPendingComparator(String sortBy, String sortDir) {
        Comparator<PendingSubmissionRowView> comparator;
        if ("evaluatorName".equals(sortBy)) {
            comparator = Comparator.comparing((PendingSubmissionRowView row) -> normalizeSortText(row.evaluatorName()))
                    .thenComparing(row -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> row.assignedAt() == null ? java.time.LocalDateTime.MIN : row.assignedAt());
        } else if ("evaluateeName".equals(sortBy)) {
            comparator = Comparator.comparing((PendingSubmissionRowView row) -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> normalizeSortText(row.evaluatorName()))
                    .thenComparing(row -> row.assignedAt() == null ? java.time.LocalDateTime.MIN : row.assignedAt());
        } else {
            comparator = Comparator.comparing((PendingSubmissionRowView row) ->
                            row.assignedAt() == null ? java.time.LocalDateTime.MIN : row.assignedAt())
                    .thenComparing(row -> normalizeSortText(row.evaluatorName()))
                    .thenComparing(row -> normalizeSortText(row.evaluateeName()));
        }
        return "asc".equalsIgnoreCase(sortDir) ? comparator : comparator.reversed();
    }

    private AssignmentResultRow toAssignmentResultRow(EvaluationAssignment assignment,
                                                      Map<Long, EmployeeResponse> employeeMap,
                                                      Map<Long, EvaluationResponse> responseByAssignmentId,
                                                      Map<Long, List<EvaluationResponseItem>> itemsByResponseId) {
        EmployeeResponse evaluator = employeeMap.get(assignment.getEvaluatorId());
        EmployeeResponse evaluatee = employeeMap.get(assignment.getEvaluateeId());
        if (evaluator == null || evaluatee == null) {
            return null;
        }
        EvaluationResponse response = responseByAssignmentId.get(assignment.getId());
        Double averageScore = null;
        if (response != null) {
            List<Integer> scores = itemsByResponseId.getOrDefault(response.getId(), List.of()).stream()
                    .map(EvaluationResponseItem::getScoreValue)
                    .filter(score -> score != null)
                    .toList();
            if (!scores.isEmpty()) {
                averageScore = Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0.0) * 100.0) / 100.0;
            }
        }
        return new AssignmentResultRow(
                evaluator.getName(),
                evaluator.getEmployeeNumber(),
                evaluator.getDepartmentName(),
                evaluatee.getName(),
                evaluatee.getEmployeeNumber(),
                evaluatee.getDepartmentId(),
                evaluatee.getDepartmentName(),
                assignment.getStatus(),
                assignment.getSubmittedAt(),
                averageScore
        );
    }

    private boolean filterAssignmentByKeyword(AssignmentResultRow row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsText(row.evaluatorName(), normalized)
                || containsText(row.evaluatorEmployeeNumber(), normalized)
                || containsText(row.evaluateeName(), normalized)
                || containsText(row.evaluateeEmployeeNumber(), normalized);
    }

    private Comparator<AssignmentResultRow> buildAssignmentComparator(String sortBy, String sortDir) {
        Comparator<AssignmentResultRow> comparator;
        if ("evaluatorName".equals(sortBy)) {
            comparator = Comparator.comparing((AssignmentResultRow row) -> normalizeSortText(row.evaluatorName()))
                    .thenComparing(row -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> row.submittedAt() == null ? java.time.LocalDateTime.MIN : row.submittedAt());
        } else if ("evaluateeName".equals(sortBy)) {
            comparator = Comparator.comparing((AssignmentResultRow row) -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> normalizeSortText(row.evaluatorName()))
                    .thenComparing(row -> row.submittedAt() == null ? java.time.LocalDateTime.MIN : row.submittedAt());
        } else if ("status".equals(sortBy)) {
            comparator = Comparator.comparing((AssignmentResultRow row) -> normalizeSortText(row.status()))
                    .thenComparing(row -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> normalizeSortText(row.evaluatorName()));
        } else if ("averageScore".equals(sortBy)) {
            comparator = Comparator.comparing((AssignmentResultRow row) -> row.averageScore() != null ? row.averageScore() : -1.0)
                    .thenComparing(row -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> normalizeSortText(row.evaluatorName()));
        } else {
            comparator = Comparator.comparing((AssignmentResultRow row) ->
                            row.submittedAt() == null ? java.time.LocalDateTime.MIN : row.submittedAt())
                    .thenComparing(row -> normalizeSortText(row.evaluateeName()))
                    .thenComparing(row -> normalizeSortText(row.evaluatorName()));
        }
        return "asc".equalsIgnoreCase(sortDir) ? comparator : comparator.reversed();
    }

    public record ResultSummary(
            long evaluateeCount,
            long totalAssignments,
            long submittedAssignments,
            long pendingAssignments,
            double submissionRate
    ) {
    }

    public record PendingSubmissionCsv(String filename, String content, int rowCount) {
    }

    public record EvaluateeResultCsv(String filename, String content, int rowCount) {
    }

    public record DepartmentResultCsv(String filename, String content, int rowCount) {
    }

    public record AssignmentResultCsv(String filename, String content, int rowCount) {
    }

    private record AssignmentResultRow(
            String evaluatorName,
            String evaluatorEmployeeNumber,
            String evaluatorDepartmentName,
            String evaluateeName,
            String evaluateeEmployeeNumber,
            Long evaluateeDepartmentId,
            String evaluateeDepartmentName,
            String status,
            java.time.LocalDateTime submittedAt,
            Double averageScore
    ) {
    }
}
