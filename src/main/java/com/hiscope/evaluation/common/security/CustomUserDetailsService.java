package com.hiscope.evaluation.common.security;

import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        // 1) 관리자 계정 (슈퍼관리자 / 기관관리자) 먼저 조회
        return accountRepository.findByLoginIdAndStatus(loginId, "ACTIVE")
                .map(account -> CustomUserDetails.builder()
                        .id(account.getId())
                        .loginId(account.getLoginId())
                        .password(account.getPasswordHash())
                        .organizationId(account.getOrganizationId())
                        .employeeId(null)
                        .role(account.getRole())
                        .name(account.getName())
                        .build())
                // 2) 없으면 직원 계정 조회
                // findByLoginIdAndEmployeeActive: JOIN FETCH로 lazy loading 제거 +
                // Employee.status = 'ACTIVE' 조건으로 비활성/휴직 직원 로그인 차단
                .or(() -> userAccountRepository.findByLoginIdAndEmployeeActive(loginId)
                        .map(ua -> CustomUserDetails.builder()
                                .id(ua.getId())
                                .loginId(ua.getLoginId())
                                .password(ua.getPasswordHash())
                                .organizationId(ua.getOrganizationId())
                                .employeeId(ua.getEmployee().getId())
                                .role(ua.getRole())
                                .name(ua.getEmployee().getName())
                                .build()))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + loginId));
    }
}
