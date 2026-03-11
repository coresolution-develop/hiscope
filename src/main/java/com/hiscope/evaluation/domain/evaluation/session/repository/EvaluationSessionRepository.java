package com.hiscope.evaluation.domain.evaluation.session.repository;

import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EvaluationSessionRepository extends JpaRepository<EvaluationSession, Long>, JpaSpecificationExecutor<EvaluationSession> {

    List<EvaluationSession> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Page<EvaluationSession> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);
    long countByOrganizationId(Long organizationId);
    long countByOrganizationIdAndStatus(Long organizationId, String status);

    Optional<EvaluationSession> findByOrganizationIdAndId(Long organizationId, Long id);

    List<EvaluationSession> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, String status);
}
