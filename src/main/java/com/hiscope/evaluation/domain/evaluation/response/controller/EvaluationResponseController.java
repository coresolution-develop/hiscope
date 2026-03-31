package com.hiscope.evaluation.domain.evaluation.response.controller;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.response.dto.EvaluationSubmitRequest;
import com.hiscope.evaluation.domain.evaluation.response.service.EvaluationResponseService;
import com.hiscope.evaluation.domain.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class EvaluationResponseController {

    private final EvaluationResponseService responseService;
    private final EmployeeService employeeService;
    private final AuditLogger auditLogger;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        var assignments = responseService.findMyAssignments(employeeId, orgId);
        long pending = assignments.stream().filter(a -> !a.isSubmitted()).count();
        long submitted = assignments.stream().filter(a -> a.isSubmitted()).count();
        model.addAttribute("assignments", assignments);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("submittedCount", submitted);
        return "user/dashboard";
    }

    @GetMapping("/evaluations")
    public String list(Model model) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        model.addAttribute("evaluationGroups", responseService.findMyEvaluationGroups(employeeId, orgId));
        return "user/evaluations/list";
    }

    @GetMapping("/evaluations/{assignmentId}")
    public String form(@PathVariable Long assignmentId, Model model) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        var assignment = responseService.getAssignment(employeeId, orgId, assignmentId);
        var questions = responseService.getQuestionsForAssignment(orgId, assignmentId);
        var evaluatee = employeeService.findById(orgId, assignment.getEvaluateeId());

        // 기존 응답 조회
        var response = responseService.findResponse(assignmentId);
        Map<Long, Integer> savedScores = new LinkedHashMap<>();
        Map<Long, String> savedTexts = new LinkedHashMap<>();
        if (response != null) {
            var items = responseService.findResponseItems(response.getId());
            items.forEach(item -> {
                if (item.getScoreValue() != null) savedScores.put(item.getQuestionId(), item.getScoreValue());
                if (item.getTextValue() != null) savedTexts.put(item.getQuestionId(), item.getTextValue());
            });
        }

        // 카테고리별 그룹핑
        var groupedQuestions = questions.stream()
                .collect(Collectors.groupingBy(q -> q.getCategory() != null ? q.getCategory() : "기타",
                        LinkedHashMap::new, Collectors.toList()));

        model.addAttribute("assignment", assignment);
        model.addAttribute("evaluatee", evaluatee);
        model.addAttribute("groupedQuestions", groupedQuestions);
        model.addAttribute("savedScores", savedScores);
        model.addAttribute("savedTexts", savedTexts);
        model.addAttribute("response", response);
        model.addAttribute("request", new EvaluationSubmitRequest());
        return "user/evaluations/form";
    }

    @PostMapping("/evaluations/{assignmentId}/save")
    public String save(@PathVariable Long assignmentId,
                       @ModelAttribute EvaluationSubmitRequest request,
                       RedirectAttributes ra) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        request.setFinalSubmit(false);
        try {
            responseService.save(employeeId, orgId, assignmentId, request);
            auditLogger.success("EVAL_RESPONSE_SAVE_DRAFT", "EVALUATION_ASSIGNMENT", String.valueOf(assignmentId),
                    AuditDetail.of("finalSubmit", false));
            ra.addFlashAttribute("successMessage", "임시저장되었습니다.");
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RESPONSE_SAVE_DRAFT", "EVALUATION_ASSIGNMENT", String.valueOf(assignmentId), e.getMessage());
            // 세션 종료, 점수 범위 위반 등 업무 예외 → 에러 페이지 대신 폼으로 돌아가 메시지 표시
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/user/evaluations/" + assignmentId;
    }

    @PostMapping("/evaluations/{assignmentId}/submit")
    public String submit(@PathVariable Long assignmentId,
                         @ModelAttribute EvaluationSubmitRequest request,
                         RedirectAttributes ra) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        request.setFinalSubmit(true);
        try {
            responseService.save(employeeId, orgId, assignmentId, request);
            auditLogger.success("EVAL_RESPONSE_SUBMIT", "EVALUATION_ASSIGNMENT", String.valueOf(assignmentId),
                    AuditDetail.of("finalSubmit", true));
            ra.addFlashAttribute("successMessage", "평가가 제출되었습니다.");
            return "redirect:/user/evaluations/" + assignmentId + "/complete";
        } catch (BusinessException e) {
            auditLogger.fail("EVAL_RESPONSE_SUBMIT", "EVALUATION_ASSIGNMENT", String.valueOf(assignmentId), e.getMessage());
            // 세션 종료, 재제출 불가, 점수 범위 위반 등 → 폼으로 돌아가 메시지 표시
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/user/evaluations/" + assignmentId;
        }
    }

    @GetMapping("/evaluations/{assignmentId}/complete")
    public String complete(@PathVariable Long assignmentId, Model model) {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        Long orgId = SecurityUtils.getCurrentOrgId();
        var assignment = responseService.getAssignment(employeeId, orgId, assignmentId);
        var evaluatee = employeeService.findById(orgId, assignment.getEvaluateeId());
        model.addAttribute("assignment", assignment);
        model.addAttribute("evaluatee", evaluatee);
        return "user/evaluations/complete";
    }
}
