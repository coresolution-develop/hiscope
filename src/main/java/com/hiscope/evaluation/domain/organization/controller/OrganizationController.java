package com.hiscope.evaluation.domain.organization.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.domain.account.dto.AccountCreateRequest;
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
    public String list(Model model) {
        model.addAttribute("organizations", organizationService.findAll());
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
            return "super-admin/organizations/detail";
        }
        request.setOrganizationId(id);
        var admin = accountService.createOrgAdmin(request);
        auditLogger.success("ORG_ADMIN_CREATE", "ACCOUNT", String.valueOf(admin.getId()),
                "orgId=" + id + ", loginId=" + admin.getLoginId());
        ra.addFlashAttribute("successMessage", "기관 관리자가 생성되었습니다.");
        return "redirect:/super-admin/organizations/" + id;
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
}
