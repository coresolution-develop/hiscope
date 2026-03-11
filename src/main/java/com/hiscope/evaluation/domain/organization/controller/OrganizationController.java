package com.hiscope.evaluation.domain.organization.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.domain.account.dto.AccountCreateRequest;
import com.hiscope.evaluation.domain.account.dto.AccountUpdateRequest;
import com.hiscope.evaluation.domain.account.dto.AccountResponse;
import com.hiscope.evaluation.domain.account.service.AccountService;
import com.hiscope.evaluation.domain.organization.dto.OrganizationCreateRequest;
import com.hiscope.evaluation.domain.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final AuditLogger auditLogger;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       Model model) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        model.addAttribute("organizations", organizationService.findAll(normalizedKeyword, normalizedStatus));
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("status", normalizedStatus);
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
                "code=" + created.getCode());
        ra.addFlashAttribute("successMessage", "기관이 생성되었습니다.");
        return "redirect:/super-admin/organizations";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("organization", organizationService.findById(id));
        List<AccountResponse> admins = accountService.findOrgAdminsByOrgId(id);
        model.addAttribute("admins", admins);
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
            model.addAttribute("adminUpdateRequest", new AccountUpdateRequest());
            return "super-admin/organizations/detail";
        }
        request.setOrganizationId(id);
        var admin = accountService.createOrgAdmin(request);
        auditLogger.success("ORG_ADMIN_CREATE", "ACCOUNT", String.valueOf(admin.getId()),
                "orgId=" + id + ", loginId=" + admin.getLoginId());
        ra.addFlashAttribute("successMessage", "기관 관리자가 생성되었습니다.");
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
                    "orgId=" + orgId + ", loginId=" + updated.getLoginId());
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
                    "orgId=" + orgId + ", status=" + updated.getStatus());
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
                    "orgId=" + orgId);
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
        auditLogger.success("ORG_STATUS_UPDATE", "ORGANIZATION", String.valueOf(id), "status=" + status);
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
}
