package com.hiscope.evaluation.domain.upload.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import com.hiscope.evaluation.domain.upload.service.UploadService;
import com.hiscope.evaluation.domain.upload.service.UploadValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final UploadValidationService uploadValidationService;
    private final AuditLogger auditLogger;

    @Value("${app.operations.upload-history-view-limit:1000}")
    private int uploadHistoryViewLimit;

    @GetMapping("/history")
    public String history(Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        model.addAttribute("histories", uploadService.findRecentHistory(orgId, uploadHistoryViewLimit));
        model.addAttribute("historyLimit", uploadHistoryViewLimit);
        return "admin/uploads/history";
    }

    @PostMapping("/departments")
    public String uploadDepartments(@RequestParam MultipartFile file, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            uploadValidationService.validateExcelFile(file);
            UploadResult result = uploadService.uploadDepartments(orgId, file);
            ra.addFlashAttribute("uploadResult", result);
            auditLogger.success("DEPT_UPLOAD", "UPLOAD_HISTORY", "-", "status=" + result.getStatus());
        } catch (BusinessException e) {
            auditLogger.fail("DEPT_UPLOAD", "UPLOAD_HISTORY", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            auditLogger.fail("DEPT_UPLOAD", "UPLOAD_HISTORY", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", "부서 업로드 처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/employees")
    public String uploadEmployees(@RequestParam MultipartFile file, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            uploadValidationService.validateExcelFile(file);
            UploadResult result = uploadService.uploadEmployees(orgId, file);
            ra.addFlashAttribute("uploadResult", result);
            auditLogger.success("EMP_UPLOAD", "UPLOAD_HISTORY", "-", "status=" + result.getStatus());
        } catch (BusinessException e) {
            auditLogger.fail("EMP_UPLOAD", "UPLOAD_HISTORY", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            auditLogger.fail("EMP_UPLOAD", "UPLOAD_HISTORY", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", "직원 업로드 처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/employees";
    }

    @GetMapping("/template/departments")
    public ResponseEntity<Resource> downloadDeptTemplate() {
        return downloadTemplate("templates/dept_template.xlsx", "부서_업로드_템플릿.xlsx");
    }

    @GetMapping("/template/employees")
    public ResponseEntity<Resource> downloadEmpTemplate() {
        return downloadTemplate("templates/emp_template.xlsx", "직원_업로드_템플릿.xlsx");
    }

    @GetMapping("/template/questions")
    public ResponseEntity<Resource> downloadQuestionTemplate() {
        return downloadTemplate("templates/question_template.xlsx", "평가문항_업로드_템플릿.xlsx");
    }

    private ResponseEntity<Resource> downloadTemplate(String path, String filename) {
        Resource resource = new ClassPathResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}
