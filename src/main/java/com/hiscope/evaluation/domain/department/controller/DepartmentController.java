package com.hiscope.evaluation.domain.department.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.dto.DepartmentRequest;
import com.hiscope.evaluation.domain.department.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String active,
                       Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Boolean normalizedActive = normalizeActive(active);
        model.addAttribute("departments", departmentService.findAll(orgId, normalizedKeyword, normalizedActive));
        model.addAttribute("request", new DepartmentRequest());
        model.addAttribute("orgId", orgId);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("active", normalizedActive);
        return "admin/departments/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") DepartmentRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentService.findAll(orgId, null, null));
            return "admin/departments/list";
        }
        var created = departmentService.create(orgId, request);
        auditLogger.success("DEPT_CREATE", "DEPARTMENT", String.valueOf(created.getId()),
                "name=" + created.getName() + ", code=" + created.getCode());
        ra.addFlashAttribute("successMessage", "부서가 등록되었습니다.");
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("request") DepartmentRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "입력값을 확인해주세요.");
            return "redirect:/admin/departments";
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        var updated = departmentService.update(orgId, id, request);
        auditLogger.success("DEPT_UPDATE", "DEPARTMENT", String.valueOf(id),
                "name=" + updated.getName() + ", active=" + updated.isActive());
        ra.addFlashAttribute("successMessage", "부서가 수정되었습니다.");
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        departmentService.delete(orgId, id);
        auditLogger.success("DEPT_DELETE", "DEPARTMENT", String.valueOf(id), "deleted");
        ra.addFlashAttribute("successMessage", "부서가 삭제되었습니다.");
        return "redirect:/admin/departments";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private Boolean normalizeActive(String active) {
        if (active == null || active.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(active)) {
            return true;
        }
        if ("false".equalsIgnoreCase(active)) {
            return false;
        }
        return null;
    }
}
