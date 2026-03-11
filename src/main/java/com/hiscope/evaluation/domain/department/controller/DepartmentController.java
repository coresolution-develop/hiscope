package com.hiscope.evaluation.domain.department.controller;

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

    @GetMapping
    public String list(Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        model.addAttribute("departments", departmentService.findAll(orgId));
        model.addAttribute("request", new DepartmentRequest());
        model.addAttribute("orgId", orgId);
        return "admin/departments/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") DepartmentRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentService.findAll(orgId));
            return "admin/departments/list";
        }
        departmentService.create(orgId, request);
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
        departmentService.update(orgId, id, request);
        ra.addFlashAttribute("successMessage", "부서가 수정되었습니다.");
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        departmentService.delete(orgId, id);
        ra.addFlashAttribute("successMessage", "부서가 삭제되었습니다.");
        return "redirect:/admin/departments";
    }
}
