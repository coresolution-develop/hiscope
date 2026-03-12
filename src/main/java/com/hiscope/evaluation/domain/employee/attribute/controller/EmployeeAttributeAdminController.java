package com.hiscope.evaluation.domain.employee.attribute.controller;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.employee.attribute.dto.EmployeeAttributeRequest;
import com.hiscope.evaluation.domain.employee.attribute.dto.EmployeeAttributeValueRequest;
import com.hiscope.evaluation.domain.employee.attribute.service.EmployeeAttributeAdminService;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings/employee-attributes")
@RequiredArgsConstructor
public class EmployeeAttributeAdminController {

    private final EmployeeAttributeAdminService employeeAttributeAdminService;
    private final EmployeeService employeeService;

    @GetMapping
    public String index(@RequestParam(required = false) Long employeeId,
                        @RequestParam(required = false) String attributeKeyword,
                        @RequestParam(required = false) Boolean attributeActive,
                        @RequestParam(required = false) String valueKeyword,
                        @RequestParam(defaultValue = "0") int attributePage,
                        @RequestParam(defaultValue = "0") int valuePage,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        int safeSize = Math.max(5, Math.min(size, 100));
        var attributes = employeeAttributeAdminService.findAttributes(orgId).stream()
                .filter(attr -> attributeKeyword == null || attributeKeyword.isBlank()
                        || attr.getAttributeName().toLowerCase().contains(attributeKeyword.trim().toLowerCase())
                        || attr.getAttributeKey().toLowerCase().contains(attributeKeyword.trim().toLowerCase()))
                .filter(attr -> attributeActive == null || attr.isActive() == attributeActive)
                .toList();
        model.addAttribute("attributes", slice(attributes, attributePage, safeSize));
        model.addAttribute("attributeNameMap", attributes.stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getId(), a -> a.getAttributeName())));
        model.addAttribute("employees", employeeService.findAll(orgId));
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("attributeKeyword", attributeKeyword);
        model.addAttribute("attributeActive", attributeActive);
        model.addAttribute("valueKeyword", valueKeyword);
        model.addAttribute("attributeTotal", attributes.size());
        model.addAttribute("attributePage", Math.max(attributePage, 0));
        model.addAttribute("valuePage", Math.max(valuePage, 0));
        model.addAttribute("size", safeSize);
        model.addAttribute("attributeRequest", new EmployeeAttributeRequest());
        model.addAttribute("valueRequest", new EmployeeAttributeValueRequest());
        if (employeeId != null) {
            var employeeValues = employeeAttributeAdminService.findEmployeeAttributeValues(employeeId).stream()
                    .filter(value -> valueKeyword == null || valueKeyword.isBlank()
                            || value.getValueText().toLowerCase().contains(valueKeyword.trim().toLowerCase()))
                    .toList();
            model.addAttribute("employeeValues", slice(employeeValues, valuePage, safeSize));
            model.addAttribute("valueTotal", employeeValues.size());
        }
        return "admin/settings/employee-attributes";
    }

    @PostMapping
    public String createAttribute(@Valid @ModelAttribute("attributeRequest") EmployeeAttributeRequest request,
                                  BindingResult br,
                                  RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/employee-attributes";
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            employeeAttributeAdminService.createAttribute(orgId, request);
            ra.addFlashAttribute("successMessage", "직원 속성이 생성되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/employee-attributes";
    }

    @PostMapping("/{attributeId}")
    public String updateAttribute(@PathVariable Long attributeId,
                                  @Valid @ModelAttribute("attributeRequest") EmployeeAttributeRequest request,
                                  BindingResult br,
                                  RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/employee-attributes";
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            employeeAttributeAdminService.updateAttribute(orgId, attributeId, request);
            ra.addFlashAttribute("successMessage", "직원 속성이 수정되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/employee-attributes";
    }

    @PostMapping("/values")
    public String upsertValue(@Valid @ModelAttribute("valueRequest") EmployeeAttributeValueRequest request,
                              BindingResult br,
                              RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", br.getFieldErrors().isEmpty() ? "입력값을 확인해주세요." : br.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/settings/employee-attributes";
        }
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            employeeAttributeAdminService.upsertAttributeValue(orgId, request);
            ra.addFlashAttribute("successMessage", "직원 속성 값이 저장되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings/employee-attributes?employeeId=" + request.getEmployeeId();
    }

    @PostMapping("/values/{valueId}/delete")
    public String deleteValue(@PathVariable Long valueId,
                              @RequestParam(required = false) Long employeeId,
                              RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            employeeAttributeAdminService.deleteAttributeValue(orgId, valueId);
            ra.addFlashAttribute("successMessage", "직원 속성 값이 삭제되었습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        if (employeeId != null) {
            return "redirect:/admin/settings/employee-attributes?employeeId=" + employeeId;
        }
        return "redirect:/admin/settings/employee-attributes";
    }

    private <T> java.util.List<T> slice(java.util.List<T> source, int page, int size) {
        int safePage = Math.max(page, 0);
        int from = safePage * size;
        if (from >= source.size()) {
            return java.util.List.of();
        }
        int to = Math.min(source.size(), from + size);
        return source.subList(from, to);
    }
}
