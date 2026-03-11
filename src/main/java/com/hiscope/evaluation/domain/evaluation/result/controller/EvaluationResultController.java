package com.hiscope.evaluation.domain.evaluation.result.controller;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.service.DepartmentService;
import com.hiscope.evaluation.domain.evaluation.result.service.EvaluationResultService;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class EvaluationResultController {

    private final EvaluationSessionService sessionService;
    private final EvaluationResultService resultService;
    private final DepartmentService departmentService;

    @GetMapping("/admin/evaluation/results")
    public String results(@RequestParam(required = false) Long sessionId,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Long departmentId,
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortDir,
                          Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var sessions = sessionService.findAll(orgId);
        Long selectedSessionId = resolveSessionId(sessionId, sessions);
        int safeSize = Math.max(10, Math.min(size, 100));
        String normalizedKeyword = normalizeKeyword(keyword);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);

        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSessionId", selectedSessionId);
        model.addAttribute("departments", departmentService.findActive(orgId));
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("departmentId", normalizedDepartmentId);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);

        if (selectedSessionId == null) {
            model.addAttribute("resultPage", org.springframework.data.domain.Page.empty());
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
        model.addAttribute("page", resultPage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("summary", summary);
        return "admin/evaluation/results/list";
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
}
