package com.hiscope.evaluation.domain.account.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.account.dto.AccountCreateRequest;
import com.hiscope.evaluation.domain.account.dto.AccountResponse;
import com.hiscope.evaluation.domain.account.dto.AccountUpdateRequest;
import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final String ORG_ADMIN_ROLE = "ROLE_ORG_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<AccountResponse> findOrgAdminsByOrgId(Long orgId) {
        return accountRepository.findByOrganizationIdAndRoleOrderByCreatedAtDesc(orgId, ORG_ADMIN_ROLE)
                .stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse createOrgAdmin(AccountCreateRequest request) {
        validateLoginIdUnique(request.getLoginId());

        Account account = Account.builder()
                .organizationId(request.getOrganizationId())
                .loginId(request.getLoginId())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .email(request.getEmail())
                .role(ORG_ADMIN_ROLE)
                .status(STATUS_ACTIVE)
                .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse updateOrgAdmin(Long orgId, Long id, AccountUpdateRequest request) {
        Account account = getOrgAdmin(orgId, id);
        account.updateProfile(request.getName(), request.getEmail());
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse updateOrgAdminStatus(Long orgId, Long id, String status) {
        Account account = getOrgAdmin(orgId, id);
        if (!STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상태는 ACTIVE 또는 INACTIVE만 가능합니다.");
        }
        account.updateStatus(status);
        return AccountResponse.from(account);
    }

    @Transactional
    public String resetOrgAdminPassword(Long orgId, Long id) {
        Account account = getOrgAdmin(orgId, id);
        String temporaryPassword = generateTemporaryPassword();
        account.updatePassword(passwordEncoder.encode(temporaryPassword));
        return temporaryPassword;
    }

    private void validateLoginIdUnique(String loginId) {
        if (accountRepository.existsByLoginId(loginId)
                || userAccountRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATE);
        }
    }

    private Account getOrgAdmin(Long orgId, Long id) {
        Account account = accountRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!ORG_ADMIN_ROLE.equals(account.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "기관 관리자 계정만 관리할 수 있습니다.");
        }
        return account;
    }

    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(TEMP_PASSWORD_CHARS.length());
            builder.append(TEMP_PASSWORD_CHARS.charAt(index));
        }
        return builder.toString();
    }
}
