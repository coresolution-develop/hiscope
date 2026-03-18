package com.hiscope.evaluation.domain.upload.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import com.hiscope.evaluation.domain.upload.dto.EmployeeUploadPreview;
import com.hiscope.evaluation.domain.organization.service.OrganizationService;
import com.hiscope.evaluation.domain.upload.service.UploadService;
import com.hiscope.evaluation.domain.upload.service.UploadTemplateService;
import com.hiscope.evaluation.domain.upload.service.UploadValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final UploadValidationService uploadValidationService;
    private final UploadTemplateService uploadTemplateService;
    private final OrganizationService organizationService;
    private final AuditLogger auditLogger;

    @Value("${app.operations.upload-history-view-limit:1000}")
    private int uploadHistoryViewLimit;

    @GetMapping("/history")
    public String history(@RequestParam(required = false) String uploadType,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String sortBy,
                          @RequestParam(required = false) String sortDir,
                          Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedType = normalizeUploadType(uploadType);
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        model.addAttribute("histories", uploadService.findRecentHistory(
                orgId,
                uploadHistoryViewLimit,
                normalizedType,
                normalizedStatus,
                dateFrom,
                dateTo,
                normalizedKeyword,
                normalizedSortBy,
                normalizedSortDir
        ));
        model.addAttribute("historyLimit", uploadHistoryViewLimit);
        model.addAttribute("uploadType", normalizedType);
        model.addAttribute("status", normalizedStatus);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        return "admin/uploads/history";
    }

    @GetMapping("/history/{id}/errors.csv")
    public ResponseEntity<byte[]> downloadUploadErrors(@PathVariable Long id) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            var failureCsv = uploadService.buildFailureCsv(orgId, id);
            auditLogger.success("UPLOAD_ERROR_DOWNLOAD", "UPLOAD_HISTORY", String.valueOf(id),
                    AuditDetail.of("errorCount", failureCsv.errorCount()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + failureCsv.filename() + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(failureCsv.content().getBytes(StandardCharsets.UTF_8));
        } catch (BusinessException e) {
            auditLogger.fail("UPLOAD_ERROR_DOWNLOAD", "UPLOAD_HISTORY", String.valueOf(id), e.getMessage());
            throw e;
        }
    }

    @PostMapping("/departments")
    public String uploadDepartments(@RequestParam MultipartFile file, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            uploadValidationService.validateExcelFile(file);
            UploadResult result = uploadService.uploadDepartments(orgId, file);
            ra.addFlashAttribute("uploadResult", result);
            auditLogger.success("DEPT_UPLOAD", "UPLOAD_HISTORY", "-",
                    AuditDetail.of(
                            "fileName", result.getFileName(),
                            "status", result.getStatus(),
                            "totalRows", result.getTotalRows(),
                            "successRows", result.getSuccessRows(),
                            "failRows", result.getFailRows()
                    ));
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
            auditLogger.success("EMP_UPLOAD", "UPLOAD_HISTORY", "-",
                    AuditDetail.of(
                            "fileName", result.getFileName(),
                            "status", result.getStatus(),
                            "totalRows", result.getTotalRows(),
                            "successRows", result.getSuccessRows(),
                            "failRows", result.getFailRows()
                    ));
        } catch (BusinessException e) {
            auditLogger.fail("EMP_UPLOAD", "UPLOAD_HISTORY", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            auditLogger.fail("EMP_UPLOAD", "UPLOAD_HISTORY", "-", e.getMessage());
            ra.addFlashAttribute("errorMessage", "직원 업로드 처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/preview")
    public String previewEmployees(@RequestParam MultipartFile file, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        try {
            uploadValidationService.validateExcelFile(file);
            EmployeeUploadPreview preview = uploadService.previewEmployees(orgId, file);
            ra.addFlashAttribute("employeeUploadPreview", preview);
            if (!preview.isUploadable()) {
                ra.addFlashAttribute("errorMessage", "업로드가 불가능한 파일입니다. 미리보기 결과를 확인해주세요.");
            }
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "직원 업로드 미리보기 처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/employees";
    }

    @GetMapping("/template/departments")
    public ResponseEntity<Resource> downloadDeptTemplate() {
        return downloadTemplate(uploadTemplateService.loadDepartmentTemplate(), "부서_업로드_템플릿.xlsx", "DEPARTMENT");
    }

    @GetMapping("/template/employees")
    public ResponseEntity<Resource> downloadEmpTemplate() {
        Long orgId = SecurityUtils.getCurrentOrgId();
        var org = organizationService.getOrganization(orgId);
        var orgType = org.getOrganizationType();
        var orgProfile = org.getOrganizationProfile();
        String filename = (orgType.name() + "_" + orgProfile.name()).toLowerCase() + "_직원_업로드_템플릿.xlsx";
        return downloadTemplate(uploadTemplateService.loadEmployeeTemplateByType(orgType), filename, "EMPLOYEE");
    }

    @GetMapping("/template/employees/hospital")
    public ResponseEntity<Resource> downloadHospitalEmpTemplate() {
        return downloadTemplate(uploadTemplateService.loadEmployeeHospitalTemplate(),
                "hospital_직원_업로드_템플릿.xlsx", "EMPLOYEE_HOSPITAL");
    }

    @GetMapping("/template/employees/affiliate")
    public ResponseEntity<Resource> downloadAffiliateEmpTemplate() {
        return downloadTemplate(uploadTemplateService.loadEmployeeAffiliateTemplate(),
                "affiliate_직원_업로드_템플릿.xlsx", "EMPLOYEE_AFFILIATE");
    }

    @GetMapping("/template/questions")
    public ResponseEntity<Resource> downloadQuestionTemplate() {
        return downloadTemplate(uploadTemplateService.loadQuestionTemplate(), "평가문항_업로드_템플릿.xlsx", "QUESTION");
    }

    private ResponseEntity<Resource> downloadTemplate(Resource resource, String filename, String templateType) {
        try {
            auditLogger.success("UPLOAD_TEMPLATE_DOWNLOAD", "UPLOAD_TEMPLATE", templateType,
                    AuditDetail.of("filename", filename));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(filename, StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (IOException e) {
            auditLogger.fail("UPLOAD_TEMPLATE_DOWNLOAD", "UPLOAD_TEMPLATE", templateType, e.getMessage());
            throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                    "템플릿 파일을 읽는 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
        } catch (RuntimeException e) {
            auditLogger.fail("UPLOAD_TEMPLATE_DOWNLOAD", "UPLOAD_TEMPLATE", templateType, e.getMessage());
            throw e;
        }
    }

    private String normalizeUploadType(String uploadType) {
        if ("DEPARTMENT".equals(uploadType) || "EMPLOYEE".equals(uploadType) || "QUESTION".equals(uploadType)) {
            return uploadType;
        }
        return null;
    }

    private String normalizeStatus(String status) {
        if ("SUCCESS".equals(status) || "PARTIAL".equals(status) || "FAILED".equals(status)) {
            return status;
        }
        return null;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeSortBy(String sortBy) {
        if ("uploadType".equals(sortBy) || "status".equals(sortBy) || "fileName".equals(sortBy)
                || "successRows".equals(sortBy) || "failRows".equals(sortBy) || "totalRows".equals(sortBy)
                || "createdAt".equals(sortBy)) {
            return sortBy;
        }
        return "createdAt";
    }

    private String normalizeSortDir(String sortDir) {
        if ("asc".equalsIgnoreCase(sortDir)) {
            return "asc";
        }
        return "desc";
    }
}
