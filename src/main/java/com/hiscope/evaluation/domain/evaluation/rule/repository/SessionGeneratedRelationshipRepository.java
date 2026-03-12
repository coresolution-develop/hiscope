package com.hiscope.evaluation.domain.evaluation.rule.repository;

import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionGeneratedRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionGeneratedRelationshipRepository extends JpaRepository<SessionGeneratedRelationship, Long> {

    List<SessionGeneratedRelationship> findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(Long sessionId);

    List<SessionGeneratedRelationship> findBySessionIdAndActiveTrueOrderByEvaluatorIdAscEvaluateeIdAsc(Long sessionId);

    Optional<SessionGeneratedRelationship> findBySessionIdAndEvaluatorIdAndEvaluateeId(Long sessionId, Long evaluatorId, Long evaluateeId);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
