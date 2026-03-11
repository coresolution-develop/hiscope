package com.hiscope.evaluation.common.security;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {}

    public static CustomUserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "로그인이 필요합니다.");
        }
        return (CustomUserDetails) auth.getPrincipal();
    }

    public static Long getCurrentOrgId() {
        return getCurrentUser().getOrganizationId();
    }

    public static Long getCurrentEmployeeId() {
        return getCurrentUser().getEmployeeId();
    }

    /**
     * 기관 관리자의 경우 자신의 기관 데이터만 접근 가능.
     * 슈퍼 관리자는 모든 기관 접근 허용.
     */
    public static void checkOrgAccess(Long targetOrgId) {
        CustomUserDetails user = getCurrentUser();
        if (user.isSuperAdmin()) return;
        if (!targetOrgId.equals(user.getOrganizationId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "다른 기관의 데이터에 접근할 수 없습니다.");
        }
    }

    /**
     * 현재 사용자의 orgId를 반환. 슈퍼 관리자는 파라미터의 orgId를 그대로 사용.
     */
    public static Long resolveOrgId(Long requestedOrgId) {
        CustomUserDetails user = getCurrentUser();
        if (user.isSuperAdmin()) {
            return requestedOrgId;
        }
        return user.getOrganizationId();
    }
}
