package com.hiscope.evaluation.domain.evaluation.result.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.service.DepartmentService;
import com.hiscope.evaluation.domain.evaluation.result.service.EvaluationResultService;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class EvaluationResultController {

    private final EvaluationSessionService sessionService;
    private final EvaluationResultService resultService;
    private final DepartmentService departmentService;
    private final AuditLogger auditLogger;

    @GetMapping("/admin/evaluation/results")
    public String results(@RequestParam(required = false) Long sessionId,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Long departmentId,
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortDir,
                          @RequestParam(required = false) String pendingSortBy,
                          @RequestParam(required = false) String pendingSortDir,
                          Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var sessions = sessionService.findAll(orgId);
        Long selectedSessionId = resolveSessionId(sessionId, sessions);
        int safeSize = Math.max(10, Math.min(size, 100));
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        String normalizedPendingSortBy = normalizePendingSortBy(pendingSortBy);
        String normalizedPendingSortDir = normalizePendingSortDir(pendingSortDir);

        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSessionId", selectedSessionId);
        model.addAttribute("departments", departmentService.findActive(orgId));
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("departmentId", normalizedDepartmentId);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("pendingSortBy", normalizedPendingSortBy);
        model.addAttribute("pendingSortDir", normalizedPendingSortDir);

        if (selectedSessionId == null) {
            model.addAttribute("resultPage", org.springframework.data.domain.Page.empty());
            model.addAttribute("pendingRows", java.util.List.of());
            return "admin/evaluation/results/list";
        }

        var resultPage = resultService.findResultRows(
                orgId,
                selectedSessionId,
                normalizedKeyword,
                normalizedDepartmentId,
                normalizedSortBy,
                normalizedSortDir,
                PageRequest.of(Math.max(page, 0), safeSize)
        );
        var summary = resultService.summarize(orgId, selectedSessionId);
        model.addAttribute("resultPage", resultPage);
        model.addAttribute("rows", resultPage.getContent());
        model.addAttribute("pendingRows", resultService.findPendingRows(
                orgId,
                selectedSessionId,
                normalizedKeyword,
                normalizedDepartmentId,
                normalizedPendingSortBy,
                normalizedPendingSortDir,
                30
        ));
        model.addAttribute("page", resultPage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("summary", summary);
        return "admin/evaluation/results/list";
    }

    @GetMapping("/admin/evaluation/results/pending.csv")
    public ResponseEntity<byte[]> downloadPendingSubmitters(@RequestParam Long sessionId,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Long departmentId,
                                                            @RequestParam(required = false) String pendingSortBy,
                                                            @RequestParam(required = false) String pendingSortDir) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedPendingSortBy = normalizePendingSortBy(pendingSortBy);
        String normalizedPendingSortDir = normalizePendingSortDir(pendingSortDir);
        try {
            var csv = resultService.buildPendingSubmissionCsv(
                    orgId,
                    sessionId,
                    normalizedKeyword,
                    normalizedDepartmentId,
                    normalizedPendingSortBy,
                    normalizedPendingSortDir
            );
            auditLogger.success("EVAL_RESULT_PENDING_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId),
                    AuditDetail.of(
                            "rowCount", csv.rowCount(),
                            "pendingSortBy", normalizedPendingSortBy,
                            "pendingSortDir", normalizedPendingSortDir
                    ));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv.content().getBytes(StandardCharsets.UTF_8));
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RESULT_PENDING_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/admin/evaluation/results/evaluatees.csv")
    public ResponseEntity<byte[]> downloadEvaluateeResults(@RequestParam Long sessionId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Long departmentId,
                                                           @RequestParam(required = false) String sortBy,
                                                           @RequestParam(required = false) String sortDir) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        try {
            var csv = resultService.buildEvaluateeResultCsv(
                    orgId,
                    sessionId,
                    normalizedKeyword,
                    normalizedDepartmentId,
                    normalizedSortBy,
                    normalizedSortDir
            );
            auditLogger.success("EVAL_RESULT_EVALUATEE_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId),
                    AuditDetail.of("rowCount", csv.rowCount()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv.content().getBytes(StandardCharsets.UTF_8));
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RESULT_EVALUATEE_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/admin/evaluation/results/departments.csv")
    public ResponseEntity<byte[]> downloadDepartmentResults(@RequestParam Long sessionId,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Long departmentId,
                                                            @RequestParam(required = false) String sortBy,
                                                            @RequestParam(required = false) String sortDir) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        try {
            var csv = resultService.buildDepartmentResultCsv(
                    orgId,
                    sessionId,
                    normalizedKeyword,
                    normalizedDepartmentId,
                    normalizedSortBy,
                    normalizedSortDir
            );
            auditLogger.success("EVAL_RESULT_DEPARTMENT_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId),
                    AuditDetail.of("rowCount", csv.rowCount()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv.content().getBytes(StandardCharsets.UTF_8));
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RESULT_DEPARTMENT_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/admin/evaluation/results/assignments.csv")
    public ResponseEntity<byte[]> downloadAssignmentResults(@RequestParam Long sessionId,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) Long departmentId,
                                                            @RequestParam(required = false) String assignmentSortBy,
                                                            @RequestParam(required = false) String assignmentSortDir) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedAssignmentSortBy = normalizeAssignmentSortBy(assignmentSortBy);
        String normalizedAssignmentSortDir = normalizeAssignmentSortDir(assignmentSortDir);
        try {
            var csv = resultService.buildAssignmentResultCsv(
                    orgId,
                    sessionId,
                    normalizedKeyword,
                    normalizedDepartmentId,
                    normalizedAssignmentSortBy,
                    normalizedAssignmentSortDir
            );
            auditLogger.success("EVAL_RESULT_ASSIGNMENT_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId),
                    AuditDetail.of(
                            "rowCount", csv.rowCount(),
                            "assignmentSortBy", normalizedAssignmentSortBy,
                            "assignmentSortDir", normalizedAssignmentSortDir
                    ));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv.content().getBytes(StandardCharsets.UTF_8));
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RESULT_ASSIGNMENT_DOWNLOAD", "EVAL_SESSION", String.valueOf(sessionId), e.getMessage());
            throw e;
        }
    }

    private Long resolveSessionId(Long requestedSessionId, java.util.List<com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession> sessions) {
        if (sessions.isEmpty()) {
            return null;
        }
        if (requestedSessionId == null) {
            return sessions.get(0).getId();
        }
        boolean exists = sessions.stream().anyMatch(s -> s.getId().equals(requestedSessionId));
        return exists ? requestedSessionId : sessions.get(0).getId();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private Long normalizeDepartmentId(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            return null;
        }
        return departmentId;
    }

    private String normalizeSortBy(String sortBy) {
        if ("averageScore".equals(sortBy) || "submittedEvaluatorCount".equals(sortBy) || "submissionRate".equals(sortBy)) {
            return sortBy;
        }
        return "evaluateeName";
    }

    private String normalizeSortDir(String sortDir) {
        if ("desc".equalsIgnoreCase(sortDir)) {
            return "desc";
        }
        return "asc";
    }

    private String normalizePendingSortBy(String sortBy) {
        if ("evaluatorName".equals(sortBy) || "evaluateeName".equals(sortBy)) {
            return sortBy;
        }
        return "assignedAt";
    }

    private String normalizePendingSortDir(String sortDir) {
        if ("asc".equalsIgnoreCase(sortDir)) {
            return "asc";
        }
        return "desc";
    }

    private String normalizeAssignmentSortBy(String sortBy) {
        if ("evaluatorName".equals(sortBy)
                || "evaluateeName".equals(sortBy)
                || "status".equals(sortBy)
                || "averageScore".equals(sortBy)) {
            return sortBy;
        }
        return "submittedAt";
    }

    private String normalizeAssignmentSortDir(String sortDir) {
        if ("asc".equalsIgnoreCase(sortDir)) {
            return "asc";
        }
        return "desc";
    }
}
