package com.hiscope.evaluation.domain.evaluation.session.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.rule.dto.SimpleRelationshipByDeptRequest;
import com.hiscope.evaluation.domain.evaluation.rule.service.SimpleRelationshipWizardService;
import com.hiscope.evaluation.domain.evaluation.session.dto.SessionCreateRequest;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.ParticipantSearchResult;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.SessionParticipantView;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
import com.hiscope.evaluation.domain.evaluation.session.service.SessionParticipantService;
import com.hiscope.evaluation.domain.evaluation.session.service.read.EvaluationSessionReadService;
import com.hiscope.evaluation.domain.evaluation.template.service.EvaluationTemplateService;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/admin/evaluation/sessions")
@RequiredArgsConstructor
public class EvaluationSessionController {

    private final EvaluationSessionService sessionService;
    private final EvaluationTemplateService templateService;
    private final EvaluationSessionReadService sessionReadService;
    private final AuditLogger auditLogger;
    private final OrganizationSettingService organizationSettingService;
    private final RelationshipDefinitionSetRepository definitionSetRepository;
    private final SessionParticipantService participantService;
    private final SimpleRelationshipWizardService wizardService;

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
        SessionCreateRequest createRequest = new SessionCreateRequest();
        createRequest.setAllowResubmit(organizationSettingService.resolveSessionDefaultAllowResubmit(orgId));
        LocalDate defaultStartDate = LocalDate.now();
        createRequest.setStartDate(defaultStartDate);
        createRequest.setEndDate(defaultStartDate.plusDays(
                organizationSettingService.resolveSessionDefaultDurationDays(orgId)
        ));
        createRequest.setRelationshipGenerationMode(RelationshipGenerationMode.LEGACY);
        definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(orgId)
                .ifPresent(set -> createRequest.setRelationshipDefinitionSetId(set.getId()));
        model.addAttribute("request", createRequest);
        return "admin/evaluation/sessions/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") SessionCreateRequest request,
        BindingResult br,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String filterAllowResubmit,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir,
        Model model, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 100));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Boolean normalizedAllowResubmit = parseBooleanStrict(filterAllowResubmit);
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
                    AuditDetail.of(
                            "name", created.getName(),
                            "status", created.getStatus(),
                            "startDate", created.getStartDate(),
                            "endDate", created.getEndDate(),
                            "templateId", created.getTemplateId(),
                            "allowResubmit", created.isAllowResubmit()
                    ));
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
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String assignmentKeyword,
                         @RequestParam(required = false) String assignmentStatus,
                         @RequestParam(required = false) String assignmentSortBy,
                         @RequestParam(required = false) String assignmentSortDir,
                         @RequestParam(defaultValue = "0") int assignmentPage,
                         @RequestParam(defaultValue = "20") int assignmentSize,
                         Model model) {
        return detailInternal(
                id,
                model,
                null,
                false,
                normalizeAssignmentKeyword(assignmentKeyword),
                normalizeAssignmentStatus(assignmentStatus),
                normalizeAssignmentSortBy(assignmentSortBy),
                normalizeAssignmentSortDir(assignmentSortDir),
                Math.max(assignmentPage, 0),
                normalizeAssignmentSize(assignmentSize)
        );
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("updateRequest") SessionCreateRequest request,
                         BindingResult br,
                         Model model,
                         RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (br.hasErrors()) {
            return detailInternal(id, model, request, true, null, null, null, null, 0, 20);
        }
        try {
            var before = sessionService.findById(orgId, id);
            sessionService.update(orgId, id, request);
            auditLogger.success("EVAL_SESSION_UPDATE", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of(
                            "nameFrom", before.getName(),
                            "nameTo", request.getName(),
                            "startDateFrom", before.getStartDate(),
                            "startDateTo", request.getStartDate(),
                            "endDateFrom", before.getEndDate(),
                            "endDateTo", request.getEndDate(),
                            "allowResubmitFrom", before.isAllowResubmit(),
                            "allowResubmitTo", Boolean.TRUE.equals(request.getAllowResubmit()),
                            "templateIdFrom", before.getTemplateId(),
                            "templateIdTo", request.getTemplateId()
                    ));
            ra.addFlashAttribute("successMessage", "평가 세션이 수정되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_UPDATE", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            var before = sessionService.findById(orgId, id);
            sessionService.delete(orgId, id);
            auditLogger.success("EVAL_SESSION_DELETE", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("name", before.getName(), "status", before.getStatus()));
            ra.addFlashAttribute("successMessage", "평가 세션이 삭제되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_DELETE", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions";
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id,
                         @RequestParam String name,
                         RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            var before = sessionService.findById(orgId, id);
            sessionService.rename(orgId, id, name);
            auditLogger.success("EVAL_SESSION_RENAME", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("nameFrom", before.getName(), "nameTo", name.trim()));
            ra.addFlashAttribute("successMessage", "세션명이 변경되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_RENAME", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id;
    }

    @PostMapping("/{id}/clone")
    public String cloneSession(@PathVariable Long id,
                               @RequestParam(required = false) String cloneName,
                               @RequestParam(required = false) String cloneStartDate,
                               @RequestParam(required = false) String cloneEndDate,
                               @RequestParam(required = false) Boolean copyRelationships,
                               RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            LocalDate parsedCloneStartDate = parseOptionalDate(cloneStartDate, "복제 시작일");
            LocalDate parsedCloneEndDate = parseOptionalDate(cloneEndDate, "복제 종료일");
            String normalizedCloneName = normalizeKeyword(cloneName);
            boolean effectiveCopyRelationships = Boolean.TRUE.equals(copyRelationships);
            var cloneResult = sessionService.cloneSession(
                    orgId,
                    id,
                    effectiveCopyRelationships,
                    normalizedCloneName,
                    parsedCloneStartDate,
                    parsedCloneEndDate
            );
            auditLogger.success("EVAL_SESSION_CLONE", "EVALUATION_SESSION", String.valueOf(cloneResult.session().getId()),
                    AuditDetail.of(
                            "sourceSessionId", id,
                            "cloneName", cloneResult.session().getName(),
                            "cloneStartDate", cloneResult.session().getStartDate(),
                            "cloneEndDate", cloneResult.session().getEndDate(),
                            "copyRelationships", effectiveCopyRelationships,
                            "copiedRelationshipCount", cloneResult.copiedRelationshipCount()
                    ));
            ra.addFlashAttribute("successMessage", "평가 세션이 복제되었습니다.");
            return "redirect:/admin/evaluation/sessions/" + cloneResult.session().getId();
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_SESSION_CLONE", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/evaluation/sessions/" + id;
        }
    }

    // ─────────────────────────────────────────────────────────
    // 참여자 관리
    // ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/participants")
    public String participants(@PathVariable Long id, Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var session = sessionService.findById(orgId, id);
        boolean hasSnapshot = participantService.hasSnapshot(id);
        List<SessionParticipantView> participants = hasSnapshot
                ? participantService.getParticipants(id, orgId) : List.of();
        model.addAttribute("evalSession", session);
        model.addAttribute("hasSnapshot", hasSnapshot);
        model.addAttribute("participants", participants);
        model.addAttribute("activeCount", participants.stream().filter(SessionParticipantView::isActive).count());
        model.addAttribute("removedCount", participants.stream().filter(SessionParticipantView::isRemoved).count());
        return "admin/evaluation/sessions/participants";
    }

    @PostMapping("/{id}/participants/snapshot")
    public String snapshotParticipants(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            participantService.snapshotParticipants(id, orgId);
            auditLogger.success("PARTICIPANT_SNAPSHOT", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("action", "snapshot"));
            ra.addFlashAttribute("successMessage", "참여자 명부가 확정되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("PARTICIPANT_SNAPSHOT", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id + "/participants";
    }

    @PostMapping("/{id}/participants/add")
    public String addParticipant(@PathVariable Long id,
                                 @RequestParam Long employeeId,
                                 @RequestParam String reason,
                                 RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        Long accountId = SecurityUtils.getCurrentUser().getId();
        try {
            participantService.addParticipant(id, employeeId, reason, orgId, accountId);
            auditLogger.success("PARTICIPANT_ADD", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("employeeId", employeeId, "reason", reason));
            ra.addFlashAttribute("successMessage", "직원이 추가되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("PARTICIPANT_ADD", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id + "/participants";
    }

    @PostMapping("/{id}/participants/remove")
    public String removeParticipant(@PathVariable Long id,
                                    @RequestParam Long employeeId,
                                    @RequestParam String reason,
                                    RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        Long accountId = SecurityUtils.getCurrentUser().getId();
        try {
            participantService.removeParticipant(id, employeeId, reason, orgId, accountId);
            auditLogger.success("PARTICIPANT_REMOVE", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("employeeId", employeeId, "reason", reason));
            ra.addFlashAttribute("successMessage", "직원이 제외되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("PARTICIPANT_REMOVE", "EVALUATION_SESSION", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id + "/participants";
    }

    @GetMapping("/{id}/participants/history")
    public String participantHistory(@PathVariable Long id, Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var session = sessionService.findById(orgId, id);
        model.addAttribute("evalSession", session);
        model.addAttribute("history", participantService.getOverrideHistory(id, orgId));
        return "admin/evaluation/sessions/participant-history";
    }

    @GetMapping("/{id}/participants/search")
    @ResponseBody
    public List<ParticipantSearchResult> searchParticipants(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "") String q) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        return participantService.searchEligibleEmployees(id, orgId, q);
    }

    // ─────────────────────────────────────────────────────────
    // 간편 관계 설정 마법사
    // ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/relationships/simple")
    public String relationshipSimple(@PathVariable Long id, Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var session = sessionService.findById(orgId, id);
        model.addAttribute("evalSession", session);
        model.addAttribute("departments", wizardService.getAvailableDepartments(orgId));
        model.addAttribute("wizardRequest", new SimpleRelationshipByDeptRequest());
        return "admin/evaluation/sessions/relationship-simple";
    }

    @PostMapping("/{id}/relationships/simple/preview")
    public String previewSimpleRelationships(
            @PathVariable Long id,
            @ModelAttribute("wizardRequest") SimpleRelationshipByDeptRequest request,
            Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var session = sessionService.findById(orgId, id);
        model.addAttribute("evalSession", session);
        model.addAttribute("departments", wizardService.getAvailableDepartments(orgId));
        model.addAttribute("wizardRequest", request);
        model.addAttribute("previewResult", wizardService.preview(id, orgId, request));
        return "admin/evaluation/sessions/relationship-simple";
    }

    @PostMapping("/{id}/relationships/simple/apply")
    public String applySimpleRelationships(
            @PathVariable Long id,
            @ModelAttribute SimpleRelationshipByDeptRequest request,
            RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        Long accountId = SecurityUtils.getCurrentUser().getId();
        try {
            wizardService.apply(id, orgId, accountId, request);
            ra.addFlashAttribute("successMessage", "간편 설정이 적용되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/sessions/" + id;
    }

    private String detailInternal(Long id,
                                  Model model,
                                  SessionCreateRequest request,
                                  boolean openEditForm,
                                  String assignmentKeyword,
                                  String assignmentStatus,
                                  String assignmentSortBy,
                                  String assignmentSortDir,
                                  int assignmentPage,
                                  int assignmentSize) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var session = sessionService.findById(orgId, id);
        model.addAttribute("evalSession", session);
        model.addAttribute("templates", templateService.findAll(orgId));
        model.addAttribute("relationshipDefinitionSets", definitionSetRepository.findByOrganizationIdOrderByNameAsc(orgId));
        if (request != null) {
            model.addAttribute("updateRequest", request);
        } else if (!model.containsAttribute("updateRequest")) {
            SessionCreateRequest updateRequest = new SessionCreateRequest();
            updateRequest.setName(session.getName());
            updateRequest.setDescription(session.getDescription());
            updateRequest.setStartDate(session.getStartDate());
            updateRequest.setEndDate(session.getEndDate());
            updateRequest.setTemplateId(session.getTemplateId());
            updateRequest.setAllowResubmit(session.isAllowResubmit());
            updateRequest.setRelationshipGenerationMode(
                    session.getRelationshipGenerationMode() != null
                            ? session.getRelationshipGenerationMode()
                            : RelationshipGenerationMode.LEGACY
            );
            updateRequest.setRelationshipDefinitionSetId(session.getRelationshipDefinitionSetId());
            model.addAttribute("updateRequest", updateRequest);
        }
        model.addAttribute("openEditForm", openEditForm);
        model.addAttribute("assignmentKeyword", assignmentKeyword);
        model.addAttribute("assignmentStatus", assignmentStatus);
        model.addAttribute("assignmentSortBy", assignmentSortBy);
        model.addAttribute("assignmentSortDir", assignmentSortDir);
        model.addAttribute("assignmentPage", assignmentPage);
        model.addAttribute("assignmentSize", assignmentSize);

        // 세션 진행 중이면 배정 현황 포함
        if (session.isInProgress() || session.isClosed()) {
            var detailView = sessionReadService.buildSessionDetail(
                    orgId,
                    id,
                    assignmentKeyword,
                    assignmentStatus,
                    assignmentSortBy,
                    assignmentSortDir,
                    assignmentPage,
                    assignmentSize
            );
            model.addAttribute("assignmentRows", detailView.assignmentRows());
            model.addAttribute("filteredAssignmentCount", detailView.filteredAssignmentCount());
            model.addAttribute("assignmentPage", detailView.assignmentPage());
            model.addAttribute("assignmentSize", detailView.assignmentSize());
            model.addAttribute("assignmentTotalPages", detailView.assignmentTotalPages());
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
            auditLogger.success("EVAL_SESSION_START", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("statusFrom", "PENDING", "statusTo", "IN_PROGRESS"));
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
            auditLogger.success("EVAL_SESSION_CLOSE", "EVALUATION_SESSION", String.valueOf(id),
                    AuditDetail.of("statusFrom", "IN_PROGRESS", "statusTo", "CLOSED"));
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
        model.addAttribute("relationshipDefinitionSets", definitionSetRepository.findByOrganizationIdOrderByNameAsc(orgId));
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

    private LocalDate parseOptionalDate(String rawDate, String fieldName) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(com.hiscope.evaluation.common.exception.ErrorCode.INVALID_INPUT,
                    fieldName + " 형식이 올바르지 않습니다. (yyyy-MM-dd)");
        }
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

    private String normalizeAssignmentKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeAssignmentStatus(String status) {
        if ("PENDING".equals(status) || "SUBMITTED".equals(status)) {
            return status;
        }
        return null;
    }

    private String normalizeAssignmentSortBy(String sortBy) {
        if ("evaluatorName".equals(sortBy) || "evaluateeName".equals(sortBy)
                || "status".equals(sortBy) || "submittedAt".equals(sortBy)) {
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

    private int normalizeAssignmentSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, sortBy);
    }
}
