package com.hiscope.evaluation.domain.organization.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.domain.account.dto.AccountCreateRequest;
import com.hiscope.evaluation.domain.account.dto.AccountUpdateRequest;
import com.hiscope.evaluation.domain.account.dto.AccountResponse;
import com.hiscope.evaluation.domain.account.service.AccountService;
import com.hiscope.evaluation.domain.organization.dto.OrganizationCreateRequest;
import com.hiscope.evaluation.domain.organization.service.OrganizationService;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/super-admin/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final AccountService accountService;
    private final OrganizationSettingService organizationSettingService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String sortBy,
                       @RequestParam(required = false) String sortDir,
                       Model model) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);

        var orgPage = organizationService.search(
                normalizedKeyword,
                normalizedStatus,
                PageRequest.of(safePage, safeSize, buildSort(normalizedSortBy, normalizedSortDir))
        );

        model.addAttribute("orgPage", orgPage);
        model.addAttribute("organizations", orgPage.getContent());
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("status", normalizedStatus);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        return "super-admin/organizations/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("request", new OrganizationCreateRequest());
        return "super-admin/organizations/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") OrganizationCreateRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return "super-admin/organizations/form";
        }
        var created = organizationService.create(request);
        auditLogger.success("ORG_CREATE", "ORGANIZATION", String.valueOf(created.getId()),
                AuditDetail.of("name", created.getName(), "code", created.getCode(), "status", created.getStatus()));
        ra.addFlashAttribute("successMessage", "기관이 생성되었습니다.");
        return "redirect:/super-admin/organizations";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("organization", organizationService.findById(id));
        List<AccountResponse> admins = accountService.findOrgAdminsByOrgId(id);
        model.addAttribute("admins", admins);
        model.addAttribute("passwordMinLength", organizationSettingService.resolvePasswordMinLength(id));
        model.addAttribute("adminRequest", new AccountCreateRequest());
        model.addAttribute("adminUpdateRequest", new AccountUpdateRequest());
        return "super-admin/organizations/detail";
    }

    @PostMapping("/{id}/admins")
    public String createAdmin(@PathVariable Long id,
                               @Valid @ModelAttribute("adminRequest") AccountCreateRequest request,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("organization", organizationService.findById(id));
            model.addAttribute("admins", accountService.findOrgAdminsByOrgId(id));
            model.addAttribute("passwordMinLength", organizationSettingService.resolvePasswordMinLength(id));
            model.addAttribute("adminUpdateRequest", new AccountUpdateRequest());
            return "super-admin/organizations/detail";
        }
        request.setOrganizationId(id);
        try {
            var admin = accountService.createOrgAdmin(request);
            auditLogger.success("ORG_ADMIN_CREATE", "ACCOUNT", String.valueOf(admin.getId()),
                    AuditDetail.of("orgId", id, "loginId", admin.getLoginId(), "status", admin.getStatus()));
            ra.addFlashAttribute("successMessage", "기관 관리자가 생성되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("ORG_ADMIN_CREATE", "ACCOUNT", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/super-admin/organizations/" + id;
    }

    @PostMapping("/{orgId}/admins/{adminId}/update")
    public String updateAdmin(@PathVariable Long orgId,
                              @PathVariable Long adminId,
                              @Valid @ModelAttribute("adminUpdateRequest") AccountUpdateRequest request,
                              BindingResult bindingResult,
                              RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("errorMessage", bindingResult.getFieldErrors().isEmpty()
                    ? "입력값을 확인해주세요."
                    : bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/super-admin/organizations/" + orgId;
        }
        try {
            var updated = accountService.updateOrgAdmin(orgId, adminId, request);
            auditLogger.success("ORG_ADMIN_UPDATE", "ACCOUNT", String.valueOf(adminId),
                    AuditDetail.of("orgId", orgId, "loginId", updated.getLoginId(), "name", updated.getName(), "email", updated.getEmail()));
            ra.addFlashAttribute("successMessage", "기관 관리자 정보가 수정되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("ORG_ADMIN_UPDATE", "ACCOUNT", String.valueOf(adminId), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/super-admin/organizations/" + orgId;
    }

    @PostMapping("/{orgId}/admins/{adminId}/status")
    public String updateAdminStatus(@PathVariable Long orgId,
                                    @PathVariable Long adminId,
                                    @RequestParam String status,
                                    RedirectAttributes ra) {
        try {
            var updated = accountService.updateOrgAdminStatus(orgId, adminId, status);
            auditLogger.success("ORG_ADMIN_STATUS_UPDATE", "ACCOUNT", String.valueOf(adminId),
                    AuditDetail.of("orgId", orgId, "status", updated.getStatus()));
            ra.addFlashAttribute("successMessage", "기관 관리자 상태가 변경되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("ORG_ADMIN_STATUS_UPDATE", "ACCOUNT", String.valueOf(adminId), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/super-admin/organizations/" + orgId;
    }

    @PostMapping("/{orgId}/admins/{adminId}/reset-password")
    public String resetAdminPassword(@PathVariable Long orgId,
                                     @PathVariable Long adminId,
                                     RedirectAttributes ra) {
        try {
            String temporaryPassword = accountService.resetOrgAdminPassword(orgId, adminId);
            auditLogger.success("ORG_ADMIN_PASSWORD_RESET", "ACCOUNT", String.valueOf(adminId),
                    AuditDetail.of("orgId", orgId, "passwordReset", true));
            ra.addFlashAttribute("successMessage",
                    "기관 관리자 비밀번호가 초기화되었습니다. 임시 비밀번호: " + temporaryPassword);
        } catch (BusinessException e) {
            auditLogger.fail("ORG_ADMIN_PASSWORD_RESET", "ACCOUNT", String.valueOf(adminId), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/super-admin/organizations/" + orgId;
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam String status,
                               RedirectAttributes ra) {
        organizationService.updateStatus(id, status);
        auditLogger.success("ORG_STATUS_UPDATE", "ORGANIZATION", String.valueOf(id), AuditDetail.of("statusTo", status));
        ra.addFlashAttribute("successMessage", "기관 상태가 변경되었습니다.");
        return "redirect:/super-admin/organizations";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeStatus(String status) {
        if ("ACTIVE".equals(status) || "INACTIVE".equals(status)) {
            return status;
        }
        return null;
    }

    private String normalizeSortBy(String sortBy) {
        if ("name".equals(sortBy) || "code".equals(sortBy) || "status".equals(sortBy) || "createdAt".equals(sortBy)) {
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
