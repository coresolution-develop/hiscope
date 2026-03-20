package com.hiscope.evaluation.common.security;

import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        String orgCode = resolveOrgCodeFromRequest();
        if (orgCode != null && !orgCode.isBlank()) {
            Long orgId = organizationRepository.findByCode(orgCode.trim().toUpperCase())
                    .orElseThrow(() -> new UsernameNotFoundException("기관 코드를 찾을 수 없습니다."))
                    .getId();
            return accountRepository.findByOrganizationIdAndLoginIdAndStatus(orgId, loginId, "ACTIVE")
                    .map(this::toAdminUserDetails)
                    .or(() -> userAccountRepository.findByOrganizationIdAndLoginIdAndEmployeeActive(orgId, loginId)
                            .map(this::toEmployeeUserDetails))
                    .orElseThrow(() -> new UsernameNotFoundException("해당 기관에서 사용자를 찾을 수 없습니다."));
        }

        // orgCode 미입력 시: 슈퍼 관리자 먼저 허용, 그 외에는 유일한 계정일 때만 허용
        var superAdmins = accountRepository.findAllByLoginIdAndStatus(loginId, "ACTIVE").stream()
                .filter(a -> a.getOrganizationId() == null)
                .toList();
        if (superAdmins.size() > 1) {
            throw new UsernameNotFoundException("중복 슈퍼관리자 로그인ID입니다. 관리자에게 문의해주세요.");
        }
        if (superAdmins.size() == 1) {
            return toAdminUserDetails(superAdmins.get(0));
        }

        var adminMatches = accountRepository.findAllByLoginIdAndStatus(loginId, "ACTIVE").stream()
                .filter(a -> a.getOrganizationId() != null)
                .toList();
        var userMatches = userAccountRepository.findAllByLoginIdAndEmployeeActive(loginId);
        int totalMatches = adminMatches.size() + userMatches.size();
        if (totalMatches == 1) {
            if (!adminMatches.isEmpty()) {
                return toAdminUserDetails(adminMatches.get(0));
            }
            return toEmployeeUserDetails(userMatches.get(0));
        }
        if (totalMatches > 1) {
            throw new UsernameNotFoundException("중복 로그인ID입니다. 기관 코드를 입력해주세요.");
        }
        throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + loginId);
    }

    private CustomUserDetails toAdminUserDetails(com.hiscope.evaluation.domain.account.entity.Account account) {
        return CustomUserDetails.builder()
                .id(account.getId())
                .loginId(account.getLoginId())
                .password(account.getPasswordHash())
                .organizationId(account.getOrganizationId())
                .employeeId(null)
                .role(account.getRole())
                .name(account.getName())
                .mustChangePassword(account.isMustChangePassword())
                .build();
    }

    private CustomUserDetails toEmployeeUserDetails(com.hiscope.evaluation.domain.employee.entity.UserAccount ua) {
        return CustomUserDetails.builder()
                .id(ua.getId())
                .loginId(ua.getLoginId())
                .password(ua.getPasswordHash())
                .organizationId(ua.getOrganizationId())
                .employeeId(ua.getEmployee().getId())
                .role(ua.getRole())
                .name(ua.getEmployee().getName())
                .mustChangePassword(ua.isMustChangePassword())
                .build();
    }

    private String resolveOrgCodeFromRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        String orgCode = servletAttrs.getRequest().getParameter("orgCode");
        if (orgCode == null || orgCode.isBlank()) {
            return null;
        }
        return orgCode;
    }
}
