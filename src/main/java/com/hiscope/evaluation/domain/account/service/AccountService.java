package com.hiscope.evaluation.domain.account.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.account.dto.AccountCreateRequest;
import com.hiscope.evaluation.domain.account.dto.AccountResponse;
import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public List<AccountResponse> findOrgAdminsByOrgId(Long orgId) {
        return accountRepository.findByOrganizationIdAndRoleOrderByCreatedAtDesc(orgId, "ROLE_ORG_ADMIN")
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
                .role("ROLE_ORG_ADMIN")
                .status("ACTIVE")
                .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.updateStatus(status);
    }

    private void validateLoginIdUnique(String loginId) {
        if (accountRepository.existsByLoginId(loginId)
                || userAccountRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATE);
        }
    }
}
