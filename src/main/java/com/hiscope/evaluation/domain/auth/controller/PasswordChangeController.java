package com.hiscope.evaluation.domain.auth.controller;

import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.CustomUserDetails;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.auth.dto.PasswordChangeRequest;
import com.hiscope.evaluation.domain.auth.service.PasswordChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth/change-password")
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;
    private final AuditLogger auditLogger;

    @GetMapping
    public String form(Model model) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        if (!model.containsAttribute("request")) {
            model.addAttribute("request", new PasswordChangeRequest());
        }
        model.addAttribute("passwordMinLength", passwordChangeService.resolveMinLength(currentUser));
        model.addAttribute("mustChangePassword", currentUser.isMustChangePassword());
        return "auth/change-password";
    }

    @PostMapping
    public String changePassword(@Valid @ModelAttribute("request") PasswordChangeRequest request,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes ra) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        if (bindingResult.hasErrors()) {
            model.addAttribute("passwordMinLength", passwordChangeService.resolveMinLength(currentUser));
            model.addAttribute("mustChangePassword", currentUser.isMustChangePassword());
            return "auth/change-password";
        }
        try {
            passwordChangeService.changePassword(currentUser, request.getNewPassword(), request.getConfirmPassword());
            refreshAuthenticationMustChangeFlag();
            auditLogger.success("PASSWORD_CHANGE", "AUTH", currentUser.getLoginId(),
                    AuditDetail.of("forced", currentUser.isMustChangePassword()));
            ra.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
            return "redirect:" + resolvePostChangeRedirect(currentUser);
        } catch (BusinessException e) {
            auditLogger.fail("PASSWORD_CHANGE", "AUTH", currentUser.getLoginId(), e.getMessage());
            model.addAttribute("passwordMinLength", passwordChangeService.resolveMinLength(currentUser));
            model.addAttribute("mustChangePassword", currentUser.isMustChangePassword());
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/change-password";
        }
    }

    private void refreshAuthenticationMustChangeFlag() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails currentUser)) {
            return;
        }
        CustomUserDetails updatedUser = currentUser.toBuilder()
                .mustChangePassword(false)
                .build();
        UsernamePasswordAuthenticationToken updatedAuthentication =
                new UsernamePasswordAuthenticationToken(
                        updatedUser,
                        authentication.getCredentials(),
                        updatedUser.getAuthorities()
                );
        updatedAuthentication.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(updatedAuthentication);
    }

    private String resolvePostChangeRedirect(CustomUserDetails user) {
        if (user.isSuperAdmin()) {
            return "/super-admin/dashboard";
        }
        if (user.isOrgAdmin()) {
            return "/admin/dashboard";
        }
        return "/user/dashboard";
    }
}
