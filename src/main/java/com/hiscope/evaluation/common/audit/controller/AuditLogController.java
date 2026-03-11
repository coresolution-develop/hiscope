package com.hiscope.evaluation.common.audit.controller;

import com.hiscope.evaluation.common.audit.service.AuditLogQueryService;
import com.hiscope.evaluation.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/admin/audit")
    public String orgAdminAudit(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                @RequestParam(required = false) String action,
                                @RequestParam(required = false) String outcome,
                                @RequestParam(required = false) String actorLoginId,
                                @RequestParam(required = false) String requestId,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
                                Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        return renderAuditPage(orgId, page, size, action, outcome, actorLoginId, requestId, fromDate, toDate, model, false);
    }

    @GetMapping("/super-admin/audit")
    public String superAdminAudit(@RequestParam(required = false) Long organizationId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) String outcome,
                                  @RequestParam(required = false) String actorLoginId,
                                  @RequestParam(required = false) String requestId,
                                  @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
                                  @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
                                  Model model) {
        return renderAuditPage(organizationId, page, size, action, outcome, actorLoginId, requestId, fromDate, toDate, model, true);
    }

    private String renderAuditPage(Long organizationId, int page, int size,
                                   String action, String outcome, String actorLoginId, String requestId,
                                   LocalDate fromDate, LocalDate toDate, Model model, boolean superAdminView) {
        int safeSize = Math.max(20, Math.min(size, 200));
        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        var auditPage = auditLogQueryService.search(
                organizationId, action, outcome, actorLoginId, requestId, fromDate, toDate, pageable);

        model.addAttribute("auditLogs", auditPage.getContent());
        model.addAttribute("auditPage", auditPage);
        model.addAttribute("page", auditPage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("organizationId", organizationId);
        model.addAttribute("action", action);
        model.addAttribute("outcome", outcome);
        model.addAttribute("actorLoginId", actorLoginId);
        model.addAttribute("requestId", requestId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("superAdminView", superAdminView);
        model.addAttribute("actionOptions", List.of(
                "AUTH_LOGIN", "AUTH_LOGOUT",
                "ORG_CREATE", "ORG_ADMIN_CREATE", "ORG_STATUS_UPDATE",
                "ORG_ADMIN_UPDATE", "ORG_ADMIN_STATUS_UPDATE", "ORG_ADMIN_PASSWORD_RESET",
                "DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE",
                "EMP_CREATE", "EMP_UPDATE", "EMP_ACTIVATE", "EMP_DEACTIVATE",
                "DEPT_UPLOAD", "EMP_UPLOAD", "EVAL_QUESTION_UPLOAD", "UPLOAD_ERROR_DOWNLOAD",
                "EVAL_TEMPLATE_CREATE", "EVAL_TEMPLATE_UPDATE", "EVAL_TEMPLATE_DELETE",
                "EVAL_QUESTION_ADD", "EVAL_QUESTION_UPDATE", "EVAL_QUESTION_DELETE",
                "EVAL_SESSION_CREATE", "EVAL_SESSION_START", "EVAL_SESSION_CLOSE",
                "EVAL_RELATION_AUTO_GENERATE", "EVAL_RELATION_ADD_MANUAL", "EVAL_RELATION_DELETE",
                "EVAL_RESPONSE_SAVE_DRAFT", "EVAL_RESPONSE_SUBMIT"
        ));
        return "admin/audit/list";
    }
}
