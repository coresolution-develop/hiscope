package com.hiscope.evaluation.domain.dashboard.controller;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/super-admin/dashboard")
    public String superAdminDashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.getSuperAdminDashboard());
        return "super-admin/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        model.addAttribute("dashboard", dashboardService.getAdminDashboard(orgId));
        return "admin/dashboard";
    }
}
