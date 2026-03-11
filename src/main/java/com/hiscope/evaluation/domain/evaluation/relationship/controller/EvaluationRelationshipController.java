package com.hiscope.evaluation.domain.evaluation.relationship.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import com.hiscope.evaluation.domain.evaluation.relationship.dto.RelationshipManualRequest;
import com.hiscope.evaluation.domain.evaluation.relationship.service.EvaluationRelationshipService;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/evaluation/sessions/{sessionId}/relationships")
@RequiredArgsConstructor
public class EvaluationRelationshipController {

    private final EvaluationRelationshipService relationshipService;
    private final EvaluationSessionService sessionService;
    private final EmployeeService employeeService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String list(@PathVariable Long sessionId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "50") int size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String relationType,
                       @RequestParam(required = false) String source,
                       @RequestParam(required = false) String active,
                       @RequestParam(required = false) String sortBy,
                       @RequestParam(required = false) String sortDir,
                       Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedRelationType = normalizeRelationType(relationType);
        String normalizedSource = normalizeSource(source);
        Boolean normalizedActive = parseBooleanStrict(active);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        populateRelationshipPageModel(
                model,
                orgId,
                sessionId,
                page,
                size,
                new EvaluationRelationshipService.RelationshipFilter(
                        normalizedKeyword,
                        normalizedRelationType,
                        normalizedSource,
                        normalizedActive
                ),
                normalizedSortBy,
                normalizedSortDir
        );
        model.addAttribute("request", new RelationshipManualRequest());
        return "admin/evaluation/sessions/relationships";
    }

    @PostMapping("/auto-generate")
    public String autoGenerate(@PathVariable Long sessionId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String relationType,
                               @RequestParam(required = false) String source,
                               @RequestParam(required = false) String active,
                               @RequestParam(required = false) String sortBy,
                               @RequestParam(required = false) String sortDir,
                               RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedRelationType = normalizeRelationType(relationType);
        String normalizedSource = normalizeSource(source);
        Boolean normalizedActive = parseBooleanStrict(active);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        try {
            int count = relationshipService.autoGenerate(orgId, sessionId);
            auditLogger.success("EVAL_RELATION_AUTO_GENERATE", "EVALUATION_SESSION", String.valueOf(sessionId),
                    "generated=" + count);
            ra.addFlashAttribute("successMessage", count + "개의 평가 관계가 자동 생성되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RELATION_AUTO_GENERATE", "EVALUATION_SESSION", String.valueOf(sessionId), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return buildListRedirect(
                sessionId,
                safePage,
                safeSize,
                normalizedKeyword,
                normalizedRelationType,
                normalizedSource,
                normalizedActive,
                normalizedSortBy,
                normalizedSortDir
        );
    }

    @PostMapping("/manual")
    public String addManual(@PathVariable Long sessionId,
                            @Valid @ModelAttribute("request") RelationshipManualRequest request,
                            BindingResult br,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String relationType,
                            @RequestParam(required = false) String source,
                            @RequestParam(required = false) String active,
                            @RequestParam(required = false) String sortBy,
                            @RequestParam(required = false) String sortDir,
                            Model model, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedRelationType = normalizeRelationType(relationType);
        String normalizedSource = normalizeSource(source);
        Boolean normalizedActive = parseBooleanStrict(active);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        if (br.hasErrors()) {
            populateRelationshipPageModel(
                    model,
                    orgId,
                    sessionId,
                    safePage,
                    safeSize,
                    new EvaluationRelationshipService.RelationshipFilter(
                            normalizedKeyword,
                            normalizedRelationType,
                            normalizedSource,
                            normalizedActive
                    ),
                    normalizedSortBy,
                    normalizedSortDir
            );
            model.addAttribute("errorMessage", br.getFieldErrors().isEmpty()
                    ? "입력값을 확인해주세요."
                    : br.getFieldErrors().get(0).getDefaultMessage());
            return "admin/evaluation/sessions/relationships";
        }
        try {
            var rel = relationshipService.addManual(orgId, sessionId, request);
            auditLogger.success("EVAL_RELATION_ADD_MANUAL", "EVALUATION_RELATIONSHIP", String.valueOf(rel.getId()),
                    "sessionId=" + sessionId);
            ra.addFlashAttribute("successMessage", "평가 관계가 추가되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RELATION_ADD_MANUAL", "EVALUATION_SESSION", String.valueOf(sessionId), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return buildListRedirect(
                sessionId,
                safePage,
                safeSize,
                normalizedKeyword,
                normalizedRelationType,
                normalizedSource,
                normalizedActive,
                normalizedSortBy,
                normalizedSortDir
        );
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long sessionId,
                         @PathVariable Long id,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String relationType,
                         @RequestParam(required = false) String source,
                         @RequestParam(required = false) String active,
                         @RequestParam(required = false) String sortBy,
                         @RequestParam(required = false) String sortDir,
                         RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedRelationType = normalizeRelationType(relationType);
        String normalizedSource = normalizeSource(source);
        Boolean normalizedActive = parseBooleanStrict(active);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        try {
            relationshipService.remove(orgId, sessionId, id);
            auditLogger.success("EVAL_RELATION_DELETE", "EVALUATION_RELATIONSHIP", String.valueOf(id),
                    "sessionId=" + sessionId);
            ra.addFlashAttribute("successMessage", "평가 관계가 삭제되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RELATION_DELETE", "EVALUATION_RELATIONSHIP", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return buildListRedirect(
                sessionId,
                safePage,
                safeSize,
                normalizedKeyword,
                normalizedRelationType,
                normalizedSource,
                normalizedActive,
                normalizedSortBy,
                normalizedSortDir
        );
    }

    private void populateRelationshipPageModel(Model model,
                                               Long orgId,
                                               Long sessionId,
                                               int page,
                                               int size,
                                               EvaluationRelationshipService.RelationshipFilter filter,
                                               String sortBy,
                                               String sortDir) {
        int safeSize = Math.max(10, Math.min(size, 200));
        var relationshipPage = relationshipService.findPageBySession(
                orgId,
                sessionId,
                filter,
                PageRequest.of(Math.max(page, 0), safeSize, buildSort(sortBy, sortDir))
        );
        var summary = relationshipService.countSummary(orgId, sessionId, filter);
        var employees = employeeService.findAll(orgId);
        EmployeeMaps employeeMaps = buildEmployeeMaps(employees);

        model.addAttribute("evalSession", sessionService.findById(orgId, sessionId));
        model.addAttribute("relationships", relationshipPage.getContent());
        model.addAttribute("relationshipPage", relationshipPage);
        model.addAttribute("page", relationshipPage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("keyword", filter.keyword());
        model.addAttribute("relationType", filter.relationType());
        model.addAttribute("source", filter.source());
        model.addAttribute("active", filter.active());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("activeRelationshipCount", summary.active());
        model.addAttribute("autoRelationshipCount", summary.autoGenerated());
        model.addAttribute("manualRelationshipCount", summary.manualAdded());
        model.addAttribute("relationTypeCountMap", summary.relationTypeCountMap());
        model.addAttribute("totalRelationshipCount", summary.total());
        model.addAttribute("employeeNameMap", employeeMaps.employeeNameMap());
        model.addAttribute("employeeDeptMap", employeeMaps.employeeDeptMap());
        model.addAttribute("employees", employees);
    }

    private EmployeeMaps buildEmployeeMaps(List<com.hiscope.evaluation.domain.employee.dto.EmployeeResponse> employees) {
        Map<Long, String> employeeNameMap = employees.stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getName()));
        Map<Long, String> employeeDeptMap = employees.stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getDepartmentName() != null ? e.getDepartmentName() : "-"));
        return new EmployeeMaps(employeeNameMap, employeeDeptMap);
    }

    private record EmployeeMaps(Map<Long, String> employeeNameMap, Map<Long, String> employeeDeptMap) {
    }

    private String buildListRedirect(Long sessionId,
                                     int page,
                                     int size,
                                     String keyword,
                                     String relationType,
                                     String source,
                                     Boolean active,
                                     String sortBy,
                                     String sortDir) {
        return "redirect:" + UriComponentsBuilder
                .fromPath("/admin/evaluation/sessions/{sessionId}/relationships")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("keyword", Optional.ofNullable(keyword))
                .queryParamIfPresent("relationType", Optional.ofNullable(relationType))
                .queryParamIfPresent("source", Optional.ofNullable(source))
                .queryParamIfPresent("active", Optional.ofNullable(active))
                .queryParam("sortBy", sortBy)
                .queryParam("sortDir", sortDir)
                .buildAndExpand(sessionId)
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

    private String normalizeRelationType(String relationType) {
        if ("UPWARD".equals(relationType) || "DOWNWARD".equals(relationType)
                || "PEER".equals(relationType) || "CROSS_DEPT".equals(relationType)
                || "MANUAL".equals(relationType)) {
            return relationType;
        }
        return null;
    }

    private String normalizeSource(String source) {
        if ("AUTO_GENERATED".equals(source) || "ADMIN_ADDED".equals(source)) {
            return source;
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
        if ("source".equals(sortBy) || "active".equals(sortBy) || "createdAt".equals(sortBy)
                || "evaluatorId".equals(sortBy) || "evaluateeId".equals(sortBy)) {
            return sortBy;
        }
        return "relationType";
    }

    private String normalizeSortDir(String sortDir) {
        if ("desc".equalsIgnoreCase(sortDir)) {
            return "desc";
        }
        return "asc";
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, sortBy);
    }
}
