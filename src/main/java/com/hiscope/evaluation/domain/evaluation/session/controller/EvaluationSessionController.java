package com.hiscope.evaluation.domain.evaluation.session.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.session.dto.SessionCreateRequest;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
import com.hiscope.evaluation.domain.evaluation.session.service.read.EvaluationSessionReadService;
import com.hiscope.evaluation.domain.evaluation.template.service.EvaluationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/evaluation/sessions")
@RequiredArgsConstructor
public class EvaluationSessionController {

    private final EvaluationSessionService sessionService;
    private final EvaluationTemplateService templateService;
    private final EvaluationSessionReadService sessionReadService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String allowResubmit,
                       @RequestParam(required = false) String sortBy,
                       @RequestParam(required = false) String sortDir,
                       Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        populateSessionListModel(
                model,
                orgId,
                page,
                size,
                normalizeKeyword(keyword),
                normalizeStatus(status),
                parseBooleanStrict(allowResubmit),
                normalizedSortBy,
                normalizedSortDir
        );
        model.addAttribute("request", new SessionCreateRequest());
        return "admin/evaluation/sessions/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") SessionCreateRequest request,
        BindingResult br,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String allowResubmit,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir,
        Model model, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 100));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Boolean normalizedAllowResubmit = parseBooleanStrict(allowResubmit);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        if (br.hasErrors()) {
            populateSessionListModel(
                    model,
                    orgId,
                    safePage,
                    safeSize,
                    normalizedKeyword,
                    normalizedStatus,
                    normalizedAllowResubmit,
                    normalizedSortBy,
                    normalizedSortDir
            );
            model.addAttribute("openCreateModal", true);
            return "admin/evaluation/sessions/list";
        }
        try {
            var created = sessionService.create(orgId, request);
            auditLogger.success("EVAL_SESSION_CREATE", "EVALUATION_SESSION", String.valueOf(created.getId()),
                    "name=" + created.getName());
            ra.addFlashAttribute("successMessage", "평가 세션이 생성되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_CREATE", "EVALUATION_SESSION", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return buildListRedirect(
                safePage,
                safeSize,
                normalizedKeyword,
                normalizedStatus,
                normalizedAllowResubmit,
                normalizedSortBy,
                normalizedSortDir
        );
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var session = sessionService.findById(orgId, id);
        model.addAttribute("evalSession", session);
        // 세션 진행 중이면 배정 현황 포함
        if (session.isInProgress() || session.isClosed()) {
            var detailView = sessionReadService.buildSessionDetail(orgId, id);
            model.addAttribute("assignmentRows", detailView.assignmentRows());
            model.addAttribute("totalAssignmentCount", detailView.totalAssignmentCount());
            model.addAttribute("submittedAssignmentCount", detailView.submittedAssignmentCount());
            model.addAttribute("pendingAssignmentCount", detailView.pendingAssignmentCount());
            model.addAttribute("assignmentProgressRate", detailView.assignmentProgressRate());
            model.addAttribute("pendingEvaluators", detailView.pendingEvaluators());
        }
        return "admin/evaluation/sessions/detail";
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            sessionService.start(orgId, id);
            auditLogger.success("EVAL_SESSION_START", "EVALUATION_SESSION", String.valueOf(id), "평가 시작");
            ra.addFlashAttribute("successMessage", "평가가 시작되었습니다. 배정이 확정되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_START", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            sessionService.close(orgId, id);
            auditLogger.success("EVAL_SESSION_CLOSE", "EVALUATION_SESSION", String.valueOf(id), "평가 종료");
            ra.addFlashAttribute("successMessage", "평가 세션이 종료되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_CLOSE", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id;
    }

    private void populateSessionListModel(Model model,
                                          Long orgId,
                                          int page,
                                          int size,
                                          String keyword,
                                          String status,
                                          Boolean allowResubmit,
                                          String sortBy,
                                          String sortDir) {
        int safeSize = Math.max(10, Math.min(size, 100));
        var sessionPage = sessionService.findPage(
                orgId,
                keyword,
                status,
                allowResubmit,
                PageRequest.of(Math.max(page, 0), safeSize, buildSort(sortBy, sortDir))
        );
        var summary = sessionService.countSummary(orgId, keyword, status, allowResubmit);
        model.addAttribute("sessions", sessionPage.getContent());
        model.addAttribute("sessionPage", sessionPage);
        model.addAttribute("page", sessionPage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("allowResubmit", allowResubmit);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("templates", templateService.findAll(orgId));
        model.addAttribute("totalSessionCount", summary.total());
        model.addAttribute("pendingSessionCount", summary.pending());
        model.addAttribute("inProgressSessionCount", summary.inProgress());
        model.addAttribute("closedSessionCount", summary.closed());
    }

    private String buildListRedirect(int page,
                                     int size,
                                     String keyword,
                                     String status,
                                     Boolean allowResubmit,
                                     String sortBy,
                                     String sortDir) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/evaluation/sessions")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("keyword", Optional.ofNullable(keyword))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParamIfPresent("allowResubmit", Optional.ofNullable(allowResubmit))
                .queryParam("sortBy", sortBy)
                .queryParam("sortDir", sortDir)
                .build()
                .toUriString();
    }

    private Boolean parseBooleanStrict(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }

    private String normalizeStatus(String status) {
        if ("PENDING".equals(status) || "IN_PROGRESS".equals(status) || "CLOSED".equals(status)) {
            return status;
        }
        return null;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeSortBy(String sortBy) {
        if ("name".equals(sortBy) || "status".equals(sortBy) || "startDate".equals(sortBy) || "endDate".equals(sortBy)) {
            return sortBy;
        }
        return "createdAt";
    }

    private String normalizeSortDir(String sortDir) {
        if ("asc".equalsIgnoreCase(sortDir)) {
            return "asc";
        }
        return "desc";
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, sortBy);
    }
}
