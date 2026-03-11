package com.hiscope.evaluation.config;

import com.hiscope.evaluation.config.properties.SuperAdminBootstrapProperties;
import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슈퍼 관리자 초기 계정 생성 (Flyway seed가 없을 경우 보조)
 * Flyway V2__seed_data.sql에서 이미 생성하므로 중복 생성 방지 로직 포함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SuperAdminBootstrapProperties bootstrapProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapProperties.isEnabled()) {
            log.info("=== 초기 슈퍼 관리자 자동 생성 비활성화 ===");
            return;
        }

        String loginId = bootstrapProperties.getLoginId();
        if (!accountRepository.existsByLoginId(loginId)) {
            Account superAdmin = Account.builder()
                    .loginId(loginId)
                    .passwordHash(passwordEncoder.encode(bootstrapProperties.getPassword()))
                    .name(bootstrapProperties.getName())
                    .email(bootstrapProperties.getEmail())
                    .role("ROLE_SUPER_ADMIN")
                    .status("ACTIVE")
                    .build();
            accountRepository.save(superAdmin);
            log.warn("=== 초기 슈퍼 관리자 계정 생성 완료 loginId={} ===", loginId);
        } else {
            log.info("=== 슈퍼 관리자 계정 확인 완료 loginId={} ===", loginId);
        }
    }
}
