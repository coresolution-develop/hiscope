package com.hiscope.evaluation.common.audit.controller;

import com.hiscope.evaluation.common.audit.service.AuditLogQueryService;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.common.util.CsvUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/admin/audit")
    public String orgAdminAudit(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                @RequestParam(required = false) String actionGroup,
                                @RequestParam(required = false) String action,
                                @RequestParam(required = false) String outcome,
                                @RequestParam(required = false) String actorLoginId,
                                @RequestParam(required = false) String actorRole,
                                @RequestParam(required = false) String ipAddress,
                                @RequestParam(required = false) String targetType,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String requestId,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
                                @RequestParam(required = false) String sortBy,
                                @RequestParam(required = false) String sortDir,
                                Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        return renderAuditPage(orgId, page, size, actionGroup, action, outcome, actorLoginId, actorRole, ipAddress, targetType, keyword, requestId,
                fromDate, toDate, sortBy, sortDir, model, false);
    }

    @GetMapping("/super-admin/audit")
    public String superAdminAudit(@RequestParam(required = false) Long organizationId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size,
                                  @RequestParam(required = false) String actionGroup,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) String outcome,
                                  @RequestParam(required = false) String actorLoginId,
                                  @RequestParam(required = false) String actorRole,
                                  @RequestParam(required = false) String ipAddress,
                                  @RequestParam(required = false) String targetType,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String requestId,
                                  @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
                                  @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
                                  @RequestParam(required = false) String sortBy,
                                  @RequestParam(required = false) String sortDir,
                                  Model model) {
        return renderAuditPage(organizationId, page, size, actionGroup, action, outcome, actorLoginId, actorRole, ipAddress, targetType, keyword, requestId,
                fromDate, toDate, sortBy, sortDir, model, true);
    }

    private String renderAuditPage(Long organizationId, int page, int size,
                                   String actionGroup, String action, String outcome, String actorLoginId, String actorRole, String ipAddress,
                                   String targetType, String keyword, String requestId,
                                   LocalDate fromDate, LocalDate toDate, String sortBy, String sortDir, Model model, boolean superAdminView) {
        int safeSize = Math.max(20, Math.min(size, 200));
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        Sort.Direction direction = "asc".equals(normalizedSortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, normalizedSortBy));
        String normalizedActionGroup = normalizeActionGroup(actionGroup);
        java.util.List<String> groupedActions = resolveActionGroupActions(normalizedActionGroup);
        var auditPage = auditLogQueryService.search(
                organizationId, action, groupedActions, outcome, actorLoginId, actorRole, ipAddress, targetType, keyword, requestId, fromDate, toDate, pageable);

        model.addAttribute("auditLogs", auditPage.getContent());
        model.addAttribute("auditPage", auditPage);
        model.addAttribute("page", auditPage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("organizationId", organizationId);
        model.addAttribute("actionGroup", normalizedActionGroup);
        model.addAttribute("action", action);
        model.addAttribute("outcome", outcome);
        model.addAttribute("actorLoginId", actorLoginId);
        model.addAttribute("actorRole", actorRole);
        model.addAttribute("ipAddress", ipAddress);
        model.addAttribute("targetType", targetType);
        model.addAttribute("keyword", keyword);
        model.addAttribute("requestId", requestId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("superAdminView", superAdminView);
        model.addAttribute("actionGroupOptions", List.of(
                "ORG_ADMIN_OPERATIONS",
                "USER_ADMIN_OPERATIONS",
                "UPLOAD_OPERATIONS",
                "SESSION_OPERATIONS",
                "RELATIONSHIP_OPERATIONS",
                "RESULT_DOWNLOAD_OPERATIONS",
                "AUTH_OPERATIONS"
        ));
        model.addAttribute("targetTypeOptions", List.of(
                "AUTH",
                "ORGANIZATION",
                "ACCOUNT",
                "DEPARTMENT",
                "EMPLOYEE",
                "UPLOAD_HISTORY",
                "EVALUATION_TEMPLATE",
                "EVALUATION_QUESTION",
                "EVALUATION_SESSION",
                "EVALUATION_RELATIONSHIP",
                "EVALUATION_ASSIGNMENT",
                "EVAL_SESSION"
        ));
        model.addAttribute("actionOptions", List.of(
                "AUTH_LOGIN", "AUTH_LOGOUT",
                "ORG_CREATE", "ORG_ADMIN_CREATE", "ORG_STATUS_UPDATE",
                "ORG_ADMIN_UPDATE", "ORG_ADMIN_STATUS_UPDATE", "ORG_ADMIN_PASSWORD_RESET",
                "DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE",
                "EMP_CREATE", "EMP_UPDATE", "EMP_ACTIVATE", "EMP_DEACTIVATE",
                "DEPT_UPLOAD", "EMP_UPLOAD", "EVAL_QUESTION_UPLOAD", "UPLOAD_ERROR_DOWNLOAD",
                "EVAL_TEMPLATE_CREATE", "EVAL_TEMPLATE_UPDATE", "EVAL_TEMPLATE_DELETE",
                "EVAL_QUESTION_ADD", "EVAL_QUESTION_UPDATE", "EVAL_QUESTION_DELETE",
                "EVAL_SESSION_CREATE", "EVAL_SESSION_UPDATE", "EVAL_SESSION_DELETE", "EVAL_SESSION_CLONE", "EVAL_SESSION_START", "EVAL_SESSION_CLOSE",
                "EVAL_RELATION_AUTO_GENERATE", "EVAL_RELATION_ADD_MANUAL", "EVAL_RELATION_DELETE",
                "EVAL_RESPONSE_SAVE_DRAFT", "EVAL_RESPONSE_SUBMIT",
                "EVAL_RESULT_PENDING_DOWNLOAD", "EVAL_RESULT_EVALUATEE_DOWNLOAD", "EVAL_RESULT_DEPARTMENT_DOWNLOAD", "EVAL_RESULT_ASSIGNMENT_DOWNLOAD",
                "ADMIN_SETTINGS_UPDATE"
        ));
        model.addAttribute("actorRoleOptions", List.of("ROLE_SUPER_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "ANONYMOUS"));
        return "admin/audit/list";
    }

    @GetMapping("/admin/audit/export.csv")
    public ResponseEntity<byte[]> orgAdminAuditExport(@RequestParam(required = false) String action,
                                                      @RequestParam(required = false) String actionGroup,
                                                      @RequestParam(required = false) String outcome,
                                                      @RequestParam(required = false) String actorLoginId,
                                                      @RequestParam(required = false) String actorRole,
                                                      @RequestParam(required = false) String ipAddress,
                                                      @RequestParam(required = false) String targetType,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String requestId,
                                                      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
                                                      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
                                                      @RequestParam(required = false) String sortBy,
                                                      @RequestParam(required = false) String sortDir) {
        return buildAuditExport(SecurityUtils.getCurrentOrgId(), actionGroup, action, outcome, actorLoginId, actorRole, ipAddress, targetType, keyword, requestId,
                fromDate, toDate, sortBy, sortDir);
    }

    @GetMapping("/super-admin/audit/export.csv")
    public ResponseEntity<byte[]> superAdminAuditExport(@RequestParam(required = false) Long organizationId,
                                                        @RequestParam(required = false) String actionGroup,
                                                        @RequestParam(required = false) String action,
                                                        @RequestParam(required = false) String outcome,
                                                        @RequestParam(required = false) String actorLoginId,
                                                        @RequestParam(required = false) String actorRole,
                                                        @RequestParam(required = false) String ipAddress,
                                                        @RequestParam(required = false) String targetType,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String requestId,
                                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
                                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
                                                        @RequestParam(required = false) String sortBy,
                                                        @RequestParam(required = false) String sortDir) {
        return buildAuditExport(organizationId, actionGroup, action, outcome, actorLoginId, actorRole, ipAddress, targetType, keyword, requestId,
                fromDate, toDate, sortBy, sortDir);
    }

    private ResponseEntity<byte[]> buildAuditExport(Long organizationId,
                                                    String actionGroup,
                                                    String action,
                                                    String outcome,
                                                    String actorLoginId,
                                                    String actorRole,
                                                    String ipAddress,
                                                    String targetType,
                                                    String keyword,
                                                    String requestId,
                                                    LocalDate fromDate,
                                                    LocalDate toDate,
                                                    String sortBy,
                                                    String sortDir) {
        String normalizedActionGroup = normalizeActionGroup(actionGroup);
        java.util.List<String> groupedActions = resolveActionGroupActions(normalizedActionGroup);
        var logs = auditLogQueryService.searchForExport(
                organizationId,
                action,
                groupedActions,
                outcome,
                actorLoginId,
                actorRole,
                ipAddress,
                targetType,
                keyword,
                requestId,
                fromDate,
                toDate,
                normalizeSortBy(sortBy),
                normalizeSortDir(sortDir),
                5000
        );
        StringBuilder csv = new StringBuilder();
        csv.append("occurred_at,outcome,action,actor_login_id,actor_role,organization_id,target_type,target_id,request_id,ip_address,detail\n");
        for (var log : logs) {
            csv.append(CsvUtils.escape(log.getOccurredAt() != null ? log.getOccurredAt().toString() : "")).append(',')
                    .append(CsvUtils.escape(log.getOutcome())).append(',')
                    .append(CsvUtils.escape(log.getAction())).append(',')
                    .append(CsvUtils.escape(log.getActorLoginId())).append(',')
                    .append(CsvUtils.escape(log.getActorRole())).append(',')
                    .append(CsvUtils.escape(log.getOrganizationId() != null ? String.valueOf(log.getOrganizationId()) : "")).append(',')
                    .append(CsvUtils.escape(log.getTargetType())).append(',')
                    .append(CsvUtils.escape(log.getTargetId())).append(',')
                    .append(CsvUtils.escape(log.getRequestId())).append(',')
                    .append(CsvUtils.escape(log.getIpAddress())).append(',')
                    .append(CsvUtils.escape(log.getDetail()))
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeSortBy(String sortBy) {
        if ("occurredAt".equals(sortBy) || "action".equals(sortBy) || "outcome".equals(sortBy)
                || "actorLoginId".equals(sortBy) || "targetType".equals(sortBy) || "organizationId".equals(sortBy)) {
            return sortBy;
        }
        return "occurredAt";
    }

    private String normalizeSortDir(String sortDir) {
        if ("asc".equalsIgnoreCase(sortDir)) {
            return "asc";
        }
        return "desc";
    }

    private String normalizeActionGroup(String actionGroup) {
        if ("ORG_ADMIN_OPERATIONS".equals(actionGroup)
                || "USER_ADMIN_OPERATIONS".equals(actionGroup)
                || "UPLOAD_OPERATIONS".equals(actionGroup)
                || "SESSION_OPERATIONS".equals(actionGroup)
                || "RELATIONSHIP_OPERATIONS".equals(actionGroup)
                || "RESULT_DOWNLOAD_OPERATIONS".equals(actionGroup)
                || "AUTH_OPERATIONS".equals(actionGroup)) {
            return actionGroup;
        }
        return null;
    }

    private List<String> resolveActionGroupActions(String actionGroup) {
        if (actionGroup == null) {
            return List.of();
        }
        return switch (actionGroup) {
            case "ORG_ADMIN_OPERATIONS" -> List.of(
                    "ORG_CREATE", "ORG_STATUS_UPDATE",
                    "ORG_ADMIN_CREATE", "ORG_ADMIN_UPDATE", "ORG_ADMIN_STATUS_UPDATE", "ORG_ADMIN_PASSWORD_RESET",
                    "ADMIN_SETTINGS_UPDATE"
            );
            case "USER_ADMIN_OPERATIONS" -> List.of(
                    "DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE",
                    "EMP_CREATE", "EMP_UPDATE", "EMP_ACTIVATE", "EMP_DEACTIVATE"
            );
            case "UPLOAD_OPERATIONS" -> List.of(
                    "DEPT_UPLOAD", "EMP_UPLOAD", "EVAL_QUESTION_UPLOAD", "UPLOAD_ERROR_DOWNLOAD"
            );
            case "SESSION_OPERATIONS" -> List.of(
                    "EVAL_SESSION_CREATE", "EVAL_SESSION_UPDATE", "EVAL_SESSION_DELETE",
                    "EVAL_SESSION_CLONE", "EVAL_SESSION_START", "EVAL_SESSION_CLOSE"
            );
            case "RELATIONSHIP_OPERATIONS" -> List.of(
                    "EVAL_RELATION_AUTO_GENERATE", "EVAL_RELATION_ADD_MANUAL", "EVAL_RELATION_DELETE"
            );
            case "RESULT_DOWNLOAD_OPERATIONS" -> List.of(
                    "EVAL_RESULT_PENDING_DOWNLOAD", "EVAL_RESULT_EVALUATEE_DOWNLOAD",
                    "EVAL_RESULT_DEPARTMENT_DOWNLOAD", "EVAL_RESULT_ASSIGNMENT_DOWNLOAD"
            );
            case "AUTH_OPERATIONS" -> List.of("AUTH_LOGIN", "AUTH_LOGOUT");
            default -> List.of();
        };
    }

}
