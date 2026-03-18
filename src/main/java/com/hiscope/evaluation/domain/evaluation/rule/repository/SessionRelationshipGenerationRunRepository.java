package com.hiscope.evaluation.domain.evaluation.rule.repository;

import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionRelationshipGenerationRun;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRelationshipGenerationRunRepository extends JpaRepository<SessionRelationshipGenerationRun, Long> {

    List<SessionRelationshipGenerationRun> findBySessionIdOrderByExecutedAtDesc(Long sessionId, Pageable pageable);

    boolean existsBySessionIdAndGenerationModeAndStatus(Long sessionId,
                                                        RelationshipGenerationMode generationMode,
                                                        String status);
}
