package com.hiscope.evaluation.domain.dashboard.controller;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.dashboard.service.DashboardService;
import com.hiscope.evaluation.domain.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final OrganizationService organizationService;

    @GetMapping("/super-admin/dashboard")
    public String superAdminDashboard(Model model) {
        var organizations = organizationService.findAll();
        long activeCount = organizations.stream()
                .filter(org -> "ACTIVE".equals(org.getStatus()))
                .count();

        model.addAttribute("organizations", organizations);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", organizations.size() - activeCount);
        return "super-admin/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        model.addAttribute("dashboard", dashboardService.getAdminDashboard(orgId));
        return "admin/dashboard";
    }
}
