package com.hiscope.evaluation.domain.evaluation.assignment.repository;

import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvaluationAssignmentRepository extends JpaRepository<EvaluationAssignment, Long> {

    List<EvaluationAssignment> findBySessionId(Long sessionId);

    List<EvaluationAssignment> findByOrganizationIdAndSessionId(Long organizationId, Long sessionId);

    List<EvaluationAssignment> findByEvaluatorId(Long evaluatorId);

    List<EvaluationAssignment> findByEvaluatorIdAndSessionId(Long evaluatorId, Long sessionId);

    Optional<EvaluationAssignment> findByOrganizationIdAndId(Long organizationId, Long id);

    @Query("SELECT a FROM EvaluationAssignment a WHERE a.evaluatorId = :evaluatorId AND a.organizationId = :orgId ORDER BY a.createdAt DESC")
    List<EvaluationAssignment> findByEvaluatorAndOrg(@Param("evaluatorId") Long evaluatorId, @Param("orgId") Long orgId);

    @Query("SELECT COUNT(a) FROM EvaluationAssignment a WHERE a.sessionId = :sessionId AND a.status = 'SUBMITTED'")
    long countSubmittedBySession(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(a) FROM EvaluationAssignment a WHERE a.sessionId = :sessionId")
    long countBySession(@Param("sessionId") Long sessionId);

    @Query("""
            SELECT COUNT(a)
            FROM EvaluationAssignment a
            JOIN EvaluationSession s ON s.id = a.sessionId
            WHERE a.organizationId = :orgId
              AND s.status = :sessionStatus
            """)
    long countByOrganizationIdAndSessionStatus(@Param("orgId") Long orgId,
                                               @Param("sessionStatus") String sessionStatus);

    @Query("""
            SELECT COUNT(a)
            FROM EvaluationAssignment a
            JOIN EvaluationSession s ON s.id = a.sessionId
            WHERE a.organizationId = :orgId
              AND a.status = 'SUBMITTED'
              AND s.status = :sessionStatus
            """)
    long countSubmittedByOrganizationIdAndSessionStatus(@Param("orgId") Long orgId,
                                                        @Param("sessionStatus") String sessionStatus);

    boolean existsBySessionIdAndEvaluatorIdAndEvaluateeId(Long sessionId, Long evaluatorId, Long evaluateeId);

    void deleteBySessionId(Long sessionId);
}
