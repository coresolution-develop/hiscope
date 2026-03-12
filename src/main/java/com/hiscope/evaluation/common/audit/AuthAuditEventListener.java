package com.hiscope.evaluation.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthAuditEventListener {

    private final AuditLogger auditLogger;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        auditLogger.success(
                "AUTH_LOGIN",
                "AUTH",
                event.getAuthentication().getName(),
                AuditDetail.of("result", "success")
        );
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String loginId = event.getAuthentication() != null ? event.getAuthentication().getName() : "unknown";
        String message = event.getException() != null ? event.getException().getMessage() : "인증 실패";
        auditLogger.fail("AUTH_LOGIN", "AUTH", loginId, message);
    }
}
