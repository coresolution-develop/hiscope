package com.hiscope.evaluation.domain.employee.controller;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.service.DepartmentService;
import com.hiscope.evaluation.domain.employee.dto.EmployeeRequest;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "50") int size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long departmentId,
                       @RequestParam(required = false) String sortBy,
                       @RequestParam(required = false) String sortDir,
                       Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        var employeePage = employeeService.findPage(
                orgId,
                normalizedKeyword,
                normalizedStatus,
                normalizedDepartmentId,
                PageRequest.of(Math.max(page, 0), safeSize, buildSort(normalizedSortBy, normalizedSortDir))
        );
        model.addAttribute("employees", employeePage.getContent());
        model.addAttribute("employeePage", employeePage);
        model.addAttribute("page", employeePage.getNumber());
        model.addAttribute("size", safeSize);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("status", normalizedStatus);
        model.addAttribute("departmentId", normalizedDepartmentId);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("departments", departmentService.findActive(orgId));
        model.addAttribute("request", new EmployeeRequest());
        return "admin/employees/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") EmployeeRequest request,
                         BindingResult bindingResult,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Long departmentId,
                         @RequestParam(required = false) String sortBy,
                         @RequestParam(required = false) String sortDir,
                         Model model, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        if (bindingResult.hasErrors()) {
            var employeePage = employeeService.findPage(
                    orgId,
                    normalizedKeyword,
                    normalizedStatus,
                    normalizedDepartmentId,
                    PageRequest.of(safePage, safeSize, buildSort(normalizedSortBy, normalizedSortDir))
            );
            model.addAttribute("employees", employeePage.getContent());
            model.addAttribute("employeePage", employeePage);
            model.addAttribute("page", employeePage.getNumber());
            model.addAttribute("size", safeSize);
            model.addAttribute("keyword", normalizedKeyword);
            model.addAttribute("status", normalizedStatus);
            model.addAttribute("departmentId", normalizedDepartmentId);
            model.addAttribute("sortBy", normalizedSortBy);
            model.addAttribute("sortDir", normalizedSortDir);
            model.addAttribute("departments", departmentService.findActive(orgId));
            String message = Optional.ofNullable(bindingResult.getFieldError())
                    .map(fe -> fe.getDefaultMessage())
                    .orElse("입력값을 확인해주세요.");
            model.addAttribute("errorMessage", message);
            return "admin/employees/list";
        }
        employeeService.create(orgId, request);
        ra.addFlashAttribute("successMessage", "직원이 등록되었습니다.");
        return buildListRedirect(safePage, safeSize, normalizedKeyword, normalizedStatus, normalizedDepartmentId, normalizedSortBy, normalizedSortDir);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute EmployeeRequest request,
                         BindingResult bindingResult,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Long departmentId,
                         @RequestParam(required = false) String sortBy,
                         @RequestParam(required = false) String sortDir,
                         RedirectAttributes ra) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        if (bindingResult.hasErrors()) {
            String message = Optional.ofNullable(bindingResult.getFieldError())
                    .map(fe -> fe.getDefaultMessage())
                    .orElse("입력값을 확인해주세요.");
            ra.addFlashAttribute("errorMessage", message);
            return buildListRedirect(safePage, safeSize, normalizedKeyword, normalizedStatus, normalizedDepartmentId, normalizedSortBy, normalizedSortDir);
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        employeeService.update(orgId, id, request);
        ra.addFlashAttribute("successMessage", "직원 정보가 수정되었습니다.");
        return buildListRedirect(safePage, safeSize, normalizedKeyword, normalizedStatus, normalizedDepartmentId, normalizedSortBy, normalizedSortDir);
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String sortBy,
                             @RequestParam(required = false) String sortDir,
                             RedirectAttributes ra) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        try {
            Long orgId = SecurityUtils.getCurrentOrgId();
            employeeService.deactivate(orgId, id);
            ra.addFlashAttribute("successMessage", "직원이 비활성화되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "직원 비활성화 중 오류가 발생했습니다.");
        }
        return buildListRedirect(safePage, safeSize, normalizedKeyword, normalizedStatus, normalizedDepartmentId, normalizedSortBy, normalizedSortDir);
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "50") int size,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) Long departmentId,
                           @RequestParam(required = false) String sortBy,
                           @RequestParam(required = false) String sortDir,
                           RedirectAttributes ra) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(10, Math.min(size, 200));
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Long normalizedDepartmentId = normalizeDepartmentId(departmentId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        try {
            Long orgId = SecurityUtils.getCurrentOrgId();
            employeeService.activate(orgId, id);
            ra.addFlashAttribute("successMessage", "직원이 재활성화되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "직원 재활성화 중 오류가 발생했습니다.");
        }
        return buildListRedirect(safePage, safeSize, normalizedKeyword, normalizedStatus, normalizedDepartmentId, normalizedSortBy, normalizedSortDir);
    }

    private String buildListRedirect(int page,
                                     int size,
                                     String keyword,
                                     String status,
                                     Long departmentId,
                                     String sortBy,
                                     String sortDir) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/employees")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("keyword", Optional.ofNullable(keyword))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParamIfPresent("departmentId", Optional.ofNullable(departmentId))
                .queryParam("sortBy", sortBy)
                .queryParam("sortDir", sortDir)
                .build()
                .toUriString();
    }

    private String normalizeStatus(String status) {
        if ("ACTIVE".equals(status) || "INACTIVE".equals(status)) {
            return status;
        }
        return null;
    }

    private Long normalizeDepartmentId(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            return null;
        }
        return departmentId;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeSortBy(String sortBy) {
        if ("employeeNumber".equals(sortBy) || "status".equals(sortBy) || "createdAt".equals(sortBy)) {
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
}
