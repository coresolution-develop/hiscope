package com.hiscope.evaluation.domain.evaluation.rule.repository;

import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipRuleMatcherRepository extends JpaRepository<RelationshipRuleMatcher, Long> {

    List<RelationshipRuleMatcher> findByRuleIdOrderByIdAsc(Long ruleId);

    List<RelationshipRuleMatcher> findByRuleIdAndSubjectTypeOrderByIdAsc(Long ruleId, RelationshipSubjectType subjectType);

    List<RelationshipRuleMatcher> findByRuleIdInOrderByRuleIdAscIdAsc(List<Long> ruleIds);

    Optional<RelationshipRuleMatcher> findByRuleIdAndId(Long ruleId, Long id);
}
