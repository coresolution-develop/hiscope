package com.hiscope.evaluation.config;

import com.hiscope.evaluation.common.audit.AuditLogger;
import com.hiscope.evaluation.common.audit.AuditDetail;
import com.hiscope.evaluation.common.security.CustomUserDetailsService;
import com.hiscope.evaluation.common.security.PasswordChangeEnforcementFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final AuditLogger auditLogger;
    private final PasswordChangeEnforcementFilter passwordChangeEnforcementFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/login", "/login-error").permitAll()
                .requestMatchers("/auth/change-password").authenticated()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ORG_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/user/**").hasRole("USER")
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    var userDetails = (com.hiscope.evaluation.common.security.CustomUserDetails) authentication.getPrincipal();
                    if (userDetails.isMustChangePassword()) {
                        response.sendRedirect("/auth/change-password");
                    } else if (userDetails.isSuperAdmin()) {
                        response.sendRedirect("/super-admin/dashboard");
                    } else if (userDetails.isOrgAdmin()) {
                        response.sendRedirect("/admin/dashboard");
                    } else {
                        response.sendRedirect("/user/dashboard");
                    }
                })
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessHandler((request, response, authentication) -> {
                    auditLogger.success("AUTH_LOGOUT", "AUTH",
                            authentication != null ? authentication.getName() : "anonymous",
                            AuditDetail.of("result", "success"));
                    response.sendRedirect("/login?logout=true");
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .invalidSessionUrl("/login?expired=true")
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .xssProtection(xss -> xss.disable())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; " +
                        "style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; " +
                        "img-src 'self' data:; " +
                        "font-src 'self' https://cdn.jsdelivr.net; " +
                        "object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
            )
            .userDetailsService(userDetailsService)
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String message = "이 요청을 수행할 권한이 없습니다.";
                    String code = "FORBIDDEN";
                    if (accessDeniedException instanceof MissingCsrfTokenException
                            || accessDeniedException instanceof InvalidCsrfTokenException) {
                        message = "요청 보안 토큰이 유효하지 않습니다. 페이지를 새로고침한 뒤 다시 시도해주세요.";
                        code = "CSRF_TOKEN_INVALID";
                    }
                    auditLogger.fail("AUTH_ACCESS_DENIED", "AUTH",
                            request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                            accessDeniedException.getMessage());
                    request.setAttribute("errorCode", code);
                    request.setAttribute("errorMessage", message);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    request.getRequestDispatcher("/error/403").forward(request, response);
                })
            );

        http.addFilterAfter(passwordChangeEnforcementFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
