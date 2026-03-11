package com.hiscope.evaluation.domain.evaluation.assignment.repository;

import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvaluationAssignmentRepository extends JpaRepository<EvaluationAssignment, Long> {

    List<EvaluationAssignment> findBySessionId(Long sessionId);

    List<EvaluationAssignment> findByEvaluatorId(Long evaluatorId);

    List<EvaluationAssignment> findByEvaluatorIdAndSessionId(Long evaluatorId, Long sessionId);

    Optional<EvaluationAssignment> findByOrganizationIdAndId(Long organizationId, Long id);

    @Query("SELECT a FROM EvaluationAssignment a WHERE a.evaluatorId = :evaluatorId AND a.organizationId = :orgId ORDER BY a.createdAt DESC")
    List<EvaluationAssignment> findByEvaluatorAndOrg(@Param("evaluatorId") Long evaluatorId, @Param("orgId") Long orgId);

    @Query("SELECT COUNT(a) FROM EvaluationAssignment a WHERE a.sessionId = :sessionId AND a.status = 'SUBMITTED'")
    long countSubmittedBySession(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(a) FROM EvaluationAssignment a WHERE a.sessionId = :sessionId")
    long countBySession(@Param("sessionId") Long sessionId);

    boolean existsBySessionIdAndEvaluatorIdAndEvaluateeId(Long sessionId, Long evaluatorId, Long evaluateeId);
}
