package com.hiscope.evaluation.common.audit.repository;

import com.hiscope.evaluation.common.audit.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByOrganizationIdOrderByOccurredAtDesc(Long organizationId, Pageable pageable);

    Optional<AuditLog> findTopByOrganizationIdAndOutcomeOrderByOccurredAtDesc(Long organizationId, String outcome);

    long countByOrganizationIdAndOutcomeAndOccurredAtGreaterThanEqual(Long organizationId, String outcome, LocalDateTime occurredAt);
}
