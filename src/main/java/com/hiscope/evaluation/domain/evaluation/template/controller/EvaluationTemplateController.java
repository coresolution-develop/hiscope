package com.hiscope.evaluation.domain.evaluation.template.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.template.dto.QuestionRequest;
import com.hiscope.evaluation.domain.evaluation.template.dto.TemplateRequest;
import com.hiscope.evaluation.domain.evaluation.template.service.EvaluationTemplateService;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import com.hiscope.evaluation.domain.upload.dto.UploadError;
import com.hiscope.evaluation.domain.upload.handler.QuestionUploadHandler;
import com.hiscope.evaluation.domain.upload.service.UploadHistoryService;
import com.hiscope.evaluation.domain.upload.service.UploadValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/evaluation/templates")
@RequiredArgsConstructor
public class EvaluationTemplateController {

    private final EvaluationTemplateService templateService;
    private final QuestionUploadHandler questionUploadHandler;
    private final UploadHistoryService uploadHistoryService;
    private final UploadValidationService uploadValidationService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String active,
                       Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        String normalizedKeyword = normalizeKeyword(keyword);
        Boolean normalizedActive = normalizeActive(active);
        model.addAttribute("templates", templateService.findAll(orgId, normalizedKeyword, normalizedActive));
        model.addAttribute("request", new TemplateRequest());
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("active", normalizedActive);
        return "admin/evaluation/templates/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("request") TemplateRequest request,
                         BindingResult br, Model model, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (br.hasErrors()) {
            model.addAttribute("templates", templateService.findAll(orgId, null, null));
            return "admin/evaluation/templates/list";
        }
        var template = templateService.createTemplate(orgId, request);
        auditLogger.success("EVAL_TEMPLATE_CREATE", "EVALUATION_TEMPLATE", String.valueOf(template.getId()), template.getName());
        ra.addFlashAttribute("successMessage", "템플릿이 생성되었습니다.");
        return "redirect:/admin/evaluation/templates";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute TemplateRequest request,
                         BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) { ra.addFlashAttribute("errorMessage", "입력값을 확인해주세요."); return "redirect:/admin/evaluation/templates"; }
        Long orgId = SecurityUtils.getCurrentOrgId();
        templateService.updateTemplate(orgId, id, request);
        auditLogger.success("EVAL_TEMPLATE_UPDATE", "EVALUATION_TEMPLATE", String.valueOf(id), request.getName());
        ra.addFlashAttribute("successMessage", "템플릿이 수정되었습니다.");
        return "redirect:/admin/evaluation/templates";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        templateService.deleteTemplate(SecurityUtils.getCurrentOrgId(), id);
        auditLogger.success("EVAL_TEMPLATE_DELETE", "EVALUATION_TEMPLATE", String.valueOf(id), "deactivated");
        ra.addFlashAttribute("successMessage", "템플릿이 삭제되었습니다.");
        return "redirect:/admin/evaluation/templates";
    }

    @GetMapping("/{id}/questions")
    public String questions(@PathVariable Long id, Model model) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        model.addAttribute("template", templateService.findById(orgId, id));
        model.addAttribute("questions", templateService.findQuestions(orgId, id));
        model.addAttribute("request", new QuestionRequest());
        return "admin/evaluation/templates/questions";
    }

    @PostMapping("/{id}/questions")
    public String addQuestion(@PathVariable Long id,
                              @Valid @ModelAttribute("request") QuestionRequest request,
                              BindingResult br, Model model, RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        if (br.hasErrors()) {
            model.addAttribute("template", templateService.findById(orgId, id));
            model.addAttribute("questions", templateService.findQuestions(orgId, id));
            return "admin/evaluation/templates/questions";
        }
        var question = templateService.addQuestion(orgId, id, request);
        auditLogger.success("EVAL_QUESTION_ADD", "EVALUATION_QUESTION", String.valueOf(question.getId()), "templateId=" + id);
        ra.addFlashAttribute("successMessage", "문항이 추가되었습니다.");
        return "redirect:/admin/evaluation/templates/" + id + "/questions";
    }

    @PostMapping("/{id}/questions/{qId}/update")
    public String updateQuestion(@PathVariable Long id, @PathVariable Long qId,
                                 @Valid @ModelAttribute QuestionRequest request,
                                 BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) { ra.addFlashAttribute("errorMessage", "입력값을 확인해주세요."); return "redirect:/admin/evaluation/templates/" + id + "/questions"; }
        templateService.updateQuestion(SecurityUtils.getCurrentOrgId(), qId, request);
        auditLogger.success("EVAL_QUESTION_UPDATE", "EVALUATION_QUESTION", String.valueOf(qId), "templateId=" + id);
        ra.addFlashAttribute("successMessage", "문항이 수정되었습니다.");
        return "redirect:/admin/evaluation/templates/" + id + "/questions";
    }

    @PostMapping("/{id}/questions/{qId}/delete")
    public String deleteQuestion(@PathVariable Long id, @PathVariable Long qId, RedirectAttributes ra) {
        templateService.deleteQuestion(SecurityUtils.getCurrentOrgId(), qId);
        auditLogger.success("EVAL_QUESTION_DELETE", "EVALUATION_QUESTION", String.valueOf(qId), "templateId=" + id);
        ra.addFlashAttribute("successMessage", "문항이 삭제되었습니다.");
        return "redirect:/admin/evaluation/templates/" + id + "/questions";
    }

    @PostMapping("/{id}/questions/upload")
    public String uploadQuestions(@PathVariable Long id,
                                  @RequestParam MultipartFile file,
                                  RedirectAttributes ra) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        Long uploadedBy = SecurityUtils.getCurrentUser().getId();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx";
        try {
            uploadValidationService.validateExcelFile(file);
            UploadResult result = questionUploadHandler.handle(orgId, id, file);
            uploadHistoryService.record(orgId, result, uploadedBy);
            auditLogger.success("EVAL_QUESTION_UPLOAD", "EVALUATION_TEMPLATE", String.valueOf(id),
                    "status=" + result.getStatus());
            ra.addFlashAttribute("uploadResult", result);
        } catch (BusinessException e) {
            uploadHistoryService.record(
                    orgId,
                    UploadResult.failed("QUESTION", fileName, List.of(new UploadError(0, "-", e.getMessage()))),
                    uploadedBy
            );
            auditLogger.fail("EVAL_QUESTION_UPLOAD", "EVALUATION_TEMPLATE", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            uploadHistoryService.record(
                    orgId,
                    UploadResult.failed("QUESTION", fileName, List.of(new UploadError(0, "-", "시스템 오류로 업로드 처리에 실패했습니다."))),
                    uploadedBy
            );
            auditLogger.fail("EVAL_QUESTION_UPLOAD", "EVALUATION_TEMPLATE", String.valueOf(id), e.getMessage());
            ra.addFlashAttribute("errorMessage", "문항 업로드 처리 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/evaluation/templates/" + id + "/questions";
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
