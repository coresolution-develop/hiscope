package com.hiscope.evaluation.domain.evaluation.rule.repository;

import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipDefinitionRuleRepository extends JpaRepository<RelationshipDefinitionRule, Long> {

    List<RelationshipDefinitionRule> findBySetIdOrderByPriorityAscIdAsc(Long setId);

    List<RelationshipDefinitionRule> findBySetIdAndActiveTrueOrderByPriorityAscIdAsc(Long setId);

    Optional<RelationshipDefinitionRule> findBySetIdAndId(Long setId, Long id);
}
