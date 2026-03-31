package com.hiscope.evaluation.domain.evaluation.relationship.repository;

import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvaluationRelationshipRepository extends JpaRepository<EvaluationRelationship, Long>, JpaSpecificationExecutor<EvaluationRelationship> {

    List<EvaluationRelationship> findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(Long sessionId);
    Page<EvaluationRelationship> findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(Long sessionId, Pageable pageable);
    long countBySessionId(Long sessionId);
    long countBySessionIdAndActive(Long sessionId, boolean active);
    long countBySessionIdAndSource(Long sessionId, String source);

    List<EvaluationRelationship> findBySessionIdAndActiveOrderByRelationTypeAsc(Long sessionId, boolean active);

    Optional<EvaluationRelationship> findBySessionIdAndEvaluatorIdAndEvaluateeId(
            Long sessionId, Long evaluatorId, Long evaluateeId);

    boolean existsBySessionIdAndEvaluatorIdAndEvaluateeId(
            Long sessionId, Long evaluatorId, Long evaluateeId);

    @Query("SELECT r FROM EvaluationRelationship r WHERE r.sessionId = :sessionId AND r.active = true")
    List<EvaluationRelationship> findActiveBySession(@Param("sessionId") Long sessionId);

    @Query("SELECT r.relationType, COUNT(r) FROM EvaluationRelationship r WHERE r.sessionId = :sessionId GROUP BY r.relationType")
    List<Object[]> countByRelationType(@Param("sessionId") Long sessionId);

    void deleteBySessionId(Long sessionId);

    @Modifying
    @Query("DELETE FROM EvaluationRelationship r WHERE r.sessionId = :sessionId AND r.source = :source")
    void deleteBySessionIdAndSource(@Param("sessionId") Long sessionId, @Param("source") String source);

    Optional<EvaluationRelationship> findByOrganizationIdAndId(Long organizationId, Long id);
}
