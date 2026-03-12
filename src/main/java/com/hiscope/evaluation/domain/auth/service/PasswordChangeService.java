package com.hiscope.evaluation.domain.auth.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.CustomUserDetails;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private static final int SUPER_ADMIN_MIN_PASSWORD_LENGTH = 8;

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationSettingService organizationSettingService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(CustomUserDetails currentUser, String newPassword, String confirmPassword) {
        validateNewPassword(currentUser, newPassword, confirmPassword);

        if (currentUser.isUser()) {
            var userAccount = userAccountRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            userAccount.updatePassword(passwordEncoder.encode(newPassword));
            userAccount.clearPasswordChangeRequired();
            return;
        }

        var account = accountRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.updatePassword(passwordEncoder.encode(newPassword));
        account.clearPasswordChangeRequired();
    }

    private void validateNewPassword(CustomUserDetails currentUser, String newPassword, String confirmPassword) {
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        int minLength = resolveMinLength(currentUser);
        if (newPassword.length() < minLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비밀번호는 최소 " + minLength + "자 이상이어야 합니다.");
        }
        if (newPassword.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 50자 이하여야 합니다.");
        }
    }

    public int resolveMinLength(CustomUserDetails currentUser) {
        if (currentUser.getOrganizationId() == null) {
            return SUPER_ADMIN_MIN_PASSWORD_LENGTH;
        }
        return organizationSettingService.resolvePasswordMinLength(currentUser.getOrganizationId());
    }
}
