package com.hiscope.evaluation.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class PasswordChangeEnforcementFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/auth/change-password";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.isMustChangePassword()
                && shouldForceChangePassword(request)) {
            response.sendRedirect(CHANGE_PASSWORD_PATH);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldForceChangePassword(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.equals(CHANGE_PASSWORD_PATH)
                && !uri.equals("/logout")
                && !uri.equals("/error")
                && !uri.startsWith("/error/")
                && !uri.startsWith("/css/")
                && !uri.startsWith("/js/")
                && !uri.startsWith("/images/")
                && !uri.startsWith("/webjars/")
                && !uri.startsWith("/actuator/")
                && !uri.equals("/favicon.ico");
    }
}
