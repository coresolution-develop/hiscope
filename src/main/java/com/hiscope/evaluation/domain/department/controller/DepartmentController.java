package com.hiscope.evaluation.domain.department.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.dto.DepartmentRequest;
import com.hiscope.evaluation.domain.department.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String sortBy,
                       @RequestParam(required = false) String sortDir,
                       Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Boolean normalizedActive = normalizeActive(active);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        populateListModel(model, orgId, normalizedKeyword, normalizedActive, safePage, safeSize, normalizedSortBy, normalizedSortDir);
        model.addAttribute("request", new DepartmentRequest());
        return "admin/departments/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") DepartmentRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (bindingResult.hasErrors()) {
            populateListModel(model, orgId, null, null, 0, 20, "name", "asc");
            return "admin/departments/list";
        }
        var created = departmentService.create(orgId, request);
        auditLogger.success("DEPT_CREATE", "DEPARTMENT", String.valueOf(created.getId()),
                AuditDetail.of("name", created.getName(), "code", created.getCode(), "active", created.isActive()));
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
                AuditDetail.of("name", updated.getName(), "code", updated.getCode(), "active", updated.isActive()));
        ra.addFlashAttribute("successMessage", "부서가 수정되었습니다.");
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var existing = departmentService.findById(orgId, id);
        departmentService.delete(orgId, id);
        auditLogger.success("DEPT_DELETE", "DEPARTMENT", String.valueOf(id),
                AuditDetail.of("name", existing.getName(), "code", existing.getCode(), "deleted", true));
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

    private String normalizeSortBy(String sortBy) {
        if ("name".equals(sortBy) || "code".equals(sortBy) || "active".equals(sortBy) || "createdAt".equals(sortBy)) {
            return sortBy;
        }
        return "name";
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

    private void populateListModel(Model model,
                                   Long orgId,
                                   String keyword,
                                   Boolean active,
                                   int page,
                                   int size,
                                   String sortBy,
                                   String sortDir) {
        var deptPage = departmentService.searchPage(
                orgId,
                keyword,
                active,
                PageRequest.of(page, size, buildSort(sortBy, sortDir))
        );
        model.addAttribute("departments", deptPage.getContent());
        model.addAttribute("allDepartments", departmentService.findAll(orgId));
        model.addAttribute("deptPage", deptPage);
        model.addAttribute("orgId", orgId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("active", active);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
    }
}
