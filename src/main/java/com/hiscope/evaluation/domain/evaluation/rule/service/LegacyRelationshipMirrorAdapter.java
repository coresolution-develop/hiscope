package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LegacyRelationshipMirrorAdapter {

    private final EvaluationRelationshipRepository relationshipRepository;

    @Transactional
    public void mirror(Long orgId, Long sessionId, List<RelationshipGenerationService.FinalRelationship> finalRelationships) {
        relationshipRepository.deleteBySessionId(sessionId);
        relationshipRepository.flush();
        if (finalRelationships.isEmpty()) {
            return;
        }
        List<EvaluationRelationship> toSave = finalRelationships.stream()
                .map(rel -> EvaluationRelationship.builder()
                        .sessionId(sessionId)
                        .organizationId(orgId)
                        .evaluatorId(rel.evaluatorId())
                        .evaluateeId(rel.evaluateeId())
                        .relationType(toLegacyRelationType(rel.relationType().name()))
                        .source(rel.overriddenByAdmin() ? "ADMIN_ADDED" : "AUTO_GENERATED")
                        .active(true)
                        .build())
                .toList();
        relationshipRepository.saveAll(toSave);
    }

    private String toLegacyRelationType(String relationType) {
        if ("CUSTOM".equals(relationType)) {
            return "MANUAL";
        }
        return relationType;
    }
}
