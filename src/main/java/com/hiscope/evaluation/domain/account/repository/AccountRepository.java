package com.hiscope.evaluation.domain.account.repository;

import com.hiscope.evaluation.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByLoginId(String loginId);

    Optional<Account> findByLoginIdAndStatus(String loginId, String status);

    boolean existsByLoginId(String loginId);

    List<Account> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<Account> findByOrganizationIdAndRoleOrderByCreatedAtDesc(Long organizationId, String role);

    Optional<Account> findByOrganizationIdAndId(Long organizationId, Long id);
}
