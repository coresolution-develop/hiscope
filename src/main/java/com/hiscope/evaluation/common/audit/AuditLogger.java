package com.hiscope.evaluation.common.audit;

import com.hiscope.evaluation.common.audit.entity.AuditLog;
import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import com.hiscope.evaluation.common.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void success(String action, String targetType, String targetId, String detail) {
        record(action, targetType, targetId, "SUCCESS", detail);
    }

    @Transactional
    public void fail(String action, String targetType, String targetId, String detail) {
        record(action, targetType, targetId, "FAIL", detail);
    }

    private void record(String action, String targetType, String targetId, String outcome, String detail) {
        ActorContext actor = resolveActor();
        RequestMeta requestMeta = resolveRequestMeta();

        auditLogRepository.save(AuditLog.builder()
                .actorId(actor.actorId())
                .actorLoginId(actor.loginId())
                .actorRole(actor.role())
                .organizationId(actor.organizationId())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .outcome(outcome)
                .detail(detail)
                .ipAddress(requestMeta.ipAddress())
                .userAgent(requestMeta.userAgent())
                .requestId(MDC.get("requestId"))
                .build());

        AUDIT.info("action={}, outcome={}, actor={}, role={}, org={}, targetType={}, targetId={}, detail={}",
                action, outcome, actor.loginId(), actor.role(), actor.organizationId(), targetType, targetId, detail);
    }

    private ActorContext resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            return new ActorContext(null, "anonymous", "ANONYMOUS", null);
        }
        return new ActorContext(user.getId(), user.getLoginId(), user.getRole(), user.getOrganizationId());
    }

    private RequestMeta resolveRequestMeta() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return new RequestMeta("-", "-");
        }
        HttpServletRequest req = attrs.getRequest();
        return new RequestMeta(req.getRemoteAddr(), req.getHeader("User-Agent"));
    }

    private record ActorContext(Long actorId, String loginId, String role, Long organizationId) {
    }

    private record RequestMeta(String ipAddress, String userAgent) {
    }
}
