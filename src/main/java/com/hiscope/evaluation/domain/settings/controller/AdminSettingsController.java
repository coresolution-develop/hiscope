package com.hiscope.evaluation.domain.settings.controller;

import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.settings.dto.AdminSettingsRequest;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final OrganizationSettingService organizationSettingService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String settings(Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var settings = organizationSettingService.getAdminSettings(orgId);
        if (!model.containsAttribute("request")) {
            AdminSettingsRequest request = new AdminSettingsRequest();
            request.setUploadMaxRows(settings.getUploadMaxRows());
            request.setUploadMaxFileSizeMb(settings.getUploadMaxFileSizeMb());
            request.setUploadAllowedExtensions(settings.getUploadAllowedExtensions());
            request.setPasswordMinLength(settings.getPasswordMinLength());
            request.setSessionDefaultDurationDays(settings.getSessionDefaultDurationDays());
            request.setSessionDefaultAllowResubmit(settings.isSessionDefaultAllowResubmit());
            model.addAttribute("request", request);
        }
        model.addAttribute("current", settings);
        return "admin/settings/index";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("request") AdminSettingsRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes ra,
                         Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (bindingResult.hasErrors()) {
            model.addAttribute("current", organizationSettingService.getAdminSettings(orgId));
            return "admin/settings/index";
        }

        organizationSettingService.updateAdminSettings(orgId, request);
        auditLogger.success("ADMIN_SETTINGS_UPDATE", "ORG_SETTINGS", String.valueOf(orgId),
                AuditDetail.of(
                        "uploadMaxRows", request.getUploadMaxRows(),
                        "uploadMaxFileSizeMb", request.getUploadMaxFileSizeMb(),
                        "uploadAllowedExtensions", request.getUploadAllowedExtensions(),
                        "passwordMinLength", request.getPasswordMinLength(),
                        "sessionDefaultDurationDays", request.getSessionDefaultDurationDays(),
                        "sessionDefaultAllowResubmit", request.isSessionDefaultAllowResubmit()
                ));
        ra.addFlashAttribute("successMessage", "운영 설정이 저장되었습니다.");
        return "redirect:/admin/settings";
    }
}
