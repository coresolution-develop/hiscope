package com.hiscope.evaluation.domain.evaluation.rule.controller;

import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.rule.dto.RelationshipDefinitionRuleRequest;
import com.hiscope.evaluation.domain.evaluation.rule.dto.RelationshipDefinitionSetRequest;
import com.hiscope.evaluation.domain.evaluation.rule.dto.RelationshipMatcherRequest;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.service.RelationshipDefinitionAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings/relationships")
@RequiredArgsConstructor
public class RelationshipDefinitionAdminController {

    private final RelationshipDefinitionAdminService relationshipDefinitionAdminService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String index(@RequestParam(required = false) Long setId,
                        @RequestParam(required = false) Long ruleId,
                        @RequestParam(required = false) String setKeyword,
                        @RequestParam(required = false) Boolean setActive,
                        @RequestParam(required = false) String ruleKeyword,
                        @RequestParam(required = false) Boolean ruleActive,
                        @RequestParam(required = false) RelationshipSubjectType matcherSubjectType,
                        @RequestParam(required = false) RelationshipMatcherType matcherType,
                        @RequestParam(required = false) RelationshipRuleOperator matcherOperator,
                        @RequestParam(defaultValue = "0") int setPage,
                        @RequestParam(defaultValue = "0") int rulePage,
                        @RequestParam(defaultValue = "0") int matcherPage,
                        @RequestParam(defaultValue = "10") int size,
                        Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safeSize = Math.max(5, Math.min(size, 50));
        var sets = relationshipDefinitionAdminService.findSets(orgId).stream()
                .filter(set -> setKeyword == null || setKeyword.isBlank() || set.getName().toLowerCase().contains(setKeyword.trim().toLowerCase()))
                .filter(set -> setActive == null || set.isActive() == setActive)
                .toList();
        model.addAttribute("sets", slice(sets, setPage, safeSize));
        model.addAttribute("setTotal", sets.size());
        model.addAttribute("setPage", Math.max(setPage, 0));
        model.addAttribute("selectedSetId", setId);
        model.addAttribute("selectedRuleId", ruleId);
        model.addAttribute("setKeyword", setKeyword);
        model.addAttribute("setActive", setActive);
        model.addAttribute("ruleKeyword", ruleKeyword);
        model.addAttribute("ruleActive", ruleActive);
        model.addAttribute("matcherSubjectType", matcherSubjectType);
        model.addAttribute("matcherType", matcherType);
        model.addAttribute("matcherOperator", matcherOperator);
        model.addAttribute("size", safeSize);
        model.addAttribute("setRequest", new RelationshipDefinitionSetRequest());
        model.addAttribute("ruleRequest", new RelationshipDefinitionRuleRequest());
        model.addAttribute("matcherRequest", new RelationshipMatcherRequest());
        model.addAttribute("relationTypes", RelationshipRuleType.values());
        model.addAttribute("subjectTypes", RelationshipSubjectType.values());
        model.addAttribute("matcherTypes", new RelationshipMatcherType[]{
                RelationshipMatcherType.EMPLOYEE,
                RelationshipMatcherType.DEPARTMENT,
                RelationshipMatcherType.JOB_TITLE,
                RelationshipMatcherType.ATTRIBUTE
        });
        model.addAttribute("operators", new RelationshipRuleOperator[]{
                RelationshipRuleOperator.IN,
                RelationshipRuleOperator.NOT_IN
        });

        if (setId != null) {
            var selectedSet = relationshipDefinitionAdminService.getSet(orgId, setId);
            model.addAttribute("selectedSet", selectedSet);
            var rules = relationshipDefinitionAdminService.findRules(orgId, setId).stream()
                    .filter(rule -> ruleKeyword == null || ruleKeyword.isBlank()
                            || rule.getRuleName().toLowerCase().contains(ruleKeyword.trim().toLowerCase()))
                    .filter(rule -> ruleActive == null || rule.isActive() == ruleActive)
                    .toList();
            model.addAttribute("rules", slice(rules, rulePage, safeSize));
            model.addAttribute("ruleTotal", rules.size());
            model.addAttribute("rulePage", Math.max(rulePage, 0));
            if (ruleId != null) {
                var matchers = relationshipDefinitionAdminService.findMatchers(orgId, setId, ruleId).stream()
                        .filter(m -> matcherSubjectType == null || m.getSubjectType() == matcherSubjectType)
                        .filter(m -> matcherType == null || m.getMatcherType() == matcherType)
                        .filter(m -> matcherOperator == null || m.getOperator() == matcherOperator)
                        .toList();
                model.addAttribute("matchers", slice(matchers, matcherPage, safeSize));
                model.addAttribute("matcherTotal", matchers.size());
                model.addAttribute("matcherPage", Math.max(matcherPage, 0));
            }
        }

        return "admin/settings/relationships";
    }

    @PostMapping("/sets")
    public String createSet(@Valid @ModelAttribute("setRequest") RelationshipDefinitionSetRequest request,
                            BindingResult br,
                            RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/relationships";
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            var set = relationshipDefinitionAdminService.createSet(orgId, request);
            auditLogger.success("REL_DEF_SET_CREATE", "RELATIONSHIP_DEFINITION_SET", String.valueOf(set.getId()),
                    AuditDetail.of("name", set.getName(), "isDefault", set.isDefault(), "active", set.isActive()));
            ra.addFlashAttribute("successMessage", "관계 정의 세트가 생성되었습니다.");
            return "redirect:/admin/settings/relationships?setId=" + set.getId();
        } catch (BusinessException e) {
            auditLogger.fail("REL_DEF_SET_CREATE", "RELATIONSHIP_DEFINITION_SET", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/settings/relationships";
        }
    }

    @PostMapping("/sets/{setId}")
    public String updateSet(@PathVariable Long setId,
                            @Valid @ModelAttribute("setRequest") RelationshipDefinitionSetRequest request,
                            BindingResult br,
                            RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId;
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.updateSet(orgId, setId, request);
            ra.addFlashAttribute("successMessage", "관계 정의 세트가 수정되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId;
    }

    @PostMapping("/sets/{setId}/default")
    public String setDefault(@PathVariable Long setId, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.makeDefault(orgId, setId);
            ra.addFlashAttribute("successMessage", "기본 세트로 지정되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId;
    }

    @PostMapping("/sets/{setId}/clone")
    public String cloneSet(@PathVariable Long setId,
                           @RequestParam String name,
                           RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            var cloned = relationshipDefinitionAdminService.cloneSet(orgId, setId, name);
            ra.addFlashAttribute("successMessage", "세트가 복제되었습니다.");
            return "redirect:/admin/settings/relationships?setId=" + cloned.getId();
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId;
        }
    }

    @PostMapping("/sets/{setId}/rules")
    public String createRule(@PathVariable Long setId,
                             @Valid @ModelAttribute("ruleRequest") RelationshipDefinitionRuleRequest request,
                             BindingResult br,
                             RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId;
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            var rule = relationshipDefinitionAdminService.createRule(orgId, setId, request);
            ra.addFlashAttribute("successMessage", "룰이 생성되었습니다.");
            return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + rule.getId();
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId;
        }
    }

    @PostMapping("/sets/{setId}/rules/{ruleId}")
    public String updateRule(@PathVariable Long setId,
                             @PathVariable Long ruleId,
                             @Valid @ModelAttribute("ruleRequest") RelationshipDefinitionRuleRequest request,
                             BindingResult br,
                             RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.updateRule(orgId, setId, ruleId, request);
            ra.addFlashAttribute("successMessage", "룰이 수정되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
    }

    @PostMapping("/sets/{setId}/rules/{ruleId}/delete")
    public String deleteRule(@PathVariable Long setId,
                             @PathVariable Long ruleId,
                             RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.deleteRule(orgId, setId, ruleId);
            ra.addFlashAttribute("successMessage", "룰이 삭제되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId;
    }

    @PostMapping("/sets/{setId}/rules/{ruleId}/matchers")
    public String createMatcher(@PathVariable Long setId,
                                @PathVariable Long ruleId,
                                @Valid @ModelAttribute("matcherRequest") RelationshipMatcherRequest request,
                                BindingResult br,
                                RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.createMatcher(orgId, setId, ruleId, request);
            ra.addFlashAttribute("successMessage", "매처가 생성되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
    }

    @PostMapping("/sets/{setId}/rules/{ruleId}/matchers/{matcherId}")
    public String updateMatcher(@PathVariable Long setId,
                                @PathVariable Long ruleId,
                                @PathVariable Long matcherId,
                                @Valid @ModelAttribute("matcherRequest") RelationshipMatcherRequest request,
                                BindingResult br,
                                RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.updateMatcher(orgId, setId, ruleId, matcherId, request);
            ra.addFlashAttribute("successMessage", "매처가 수정되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
    }

    @PostMapping("/sets/{setId}/rules/{ruleId}/matchers/{matcherId}/delete")
    public String deleteMatcher(@PathVariable Long setId,
                                @PathVariable Long ruleId,
                                @PathVariable Long matcherId,
                                RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            relationshipDefinitionAdminService.deleteMatcher(orgId, setId, ruleId, matcherId);
            ra.addFlashAttribute("successMessage", "매처가 삭제되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/relationships?setId=" + setId + "&ruleId=" + ruleId;
    }

    private <T> java.util.List<T> slice(java.util.List<T> source, int page, int size) {
        int safePage = Math.max(page, 0);
        int from = safePage * size;
        if (from >= source.size()) {
            return java.util.List.of();
        }
        int to = Math.min(source.size(), from + size);
        return source.subList(from, to);
    }
}
