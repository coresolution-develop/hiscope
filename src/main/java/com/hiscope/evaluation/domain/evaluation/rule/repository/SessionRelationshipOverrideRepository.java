package com.hiscope.evaluation.domain.evaluation.rule.repository;

import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionRelationshipOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRelationshipOverrideRepository extends JpaRepository<SessionRelationshipOverride, Long> {

    List<SessionRelationshipOverride> findBySessionIdOrderByIdAsc(Long sessionId);

    Optional<SessionRelationshipOverride> findTopBySessionIdAndEvaluatorIdAndEvaluateeIdOrderByIdDesc(
            Long sessionId,
            Long evaluatorId,
            Long evaluateeId
    );

    void deleteBySessionId(Long sessionId);
}
