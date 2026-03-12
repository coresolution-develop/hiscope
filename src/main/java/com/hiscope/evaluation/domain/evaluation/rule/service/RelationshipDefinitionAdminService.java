package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.rule.dto.RelationshipDefinitionRuleRequest;
import com.hiscope.evaluation.domain.evaluation.rule.dto.RelationshipDefinitionSetRequest;
import com.hiscope.evaluation.domain.evaluation.rule.dto.RelationshipMatcherRequest;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationshipDefinitionAdminService {

    private final RelationshipDefinitionSetRepository definitionSetRepository;
    private final RelationshipDefinitionRuleRepository definitionRuleRepository;
    private final RelationshipRuleMatcherRepository matcherRepository;

    public List<RelationshipDefinitionSet> findSets(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return definitionSetRepository.findByOrganizationIdOrderByNameAsc(orgId);
    }

    public List<RelationshipDefinitionRule> findRules(Long orgId, Long setId) {
        SecurityUtils.checkOrgAccess(orgId);
        getSet(orgId, setId);
        return definitionRuleRepository.findBySetIdOrderByPriorityAscIdAsc(setId);
    }

    public List<RelationshipRuleMatcher> findMatchers(Long orgId, Long setId, Long ruleId) {
        SecurityUtils.checkOrgAccess(orgId);
        getRule(orgId, setId, ruleId);
        return matcherRepository.findByRuleIdOrderByIdAsc(ruleId);
    }

    @Transactional
    public RelationshipDefinitionSet createSet(Long orgId, RelationshipDefinitionSetRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        if (definitionSetRepository.existsByOrganizationIdAndName(orgId, request.getName().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 기관에 동일한 세트명이 이미 존재합니다.");
        }
        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(orgId)
                .name(request.getName().trim())
                .isDefault(false)
                .active(request.isActive())
                .createdBy(SecurityUtils.getCurrentUser().getId())
                .build());
        if (request.isDefaultSet()) {
            makeDefault(orgId, set.getId());
        }
        return set;
    }

    @Transactional
    public RelationshipDefinitionSet cloneSet(Long orgId, Long sourceSetId, String targetName) {
        SecurityUtils.checkOrgAccess(orgId);
        RelationshipDefinitionSet sourceSet = getSet(orgId, sourceSetId);
        String normalizedTargetName = targetName == null ? "" : targetName.trim();
        if (normalizedTargetName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "복제 세트명은 필수입니다.");
        }
        if (definitionSetRepository.existsByOrganizationIdAndName(orgId, normalizedTargetName)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 기관에 동일한 세트명이 이미 존재합니다.");
        }
        RelationshipDefinitionSet clonedSet = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(orgId)
                .name(normalizedTargetName)
                .isDefault(false)
                .active(true)
                .createdBy(SecurityUtils.getCurrentUser().getId())
                .build());
        List<RelationshipDefinitionRule> sourceRules = definitionRuleRepository.findBySetIdOrderByPriorityAscIdAsc(sourceSet.getId());
        for (RelationshipDefinitionRule sourceRule : sourceRules) {
            RelationshipDefinitionRule clonedRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                    .setId(clonedSet.getId())
                    .ruleName(sourceRule.getRuleName())
                    .relationType(sourceRule.getRelationType())
                    .priority(sourceRule.getPriority())
                    .active(sourceRule.isActive())
                    .build());
            List<RelationshipRuleMatcher> sourceMatchers = matcherRepository.findByRuleIdOrderByIdAsc(sourceRule.getId());
            if (!sourceMatchers.isEmpty()) {
                matcherRepository.saveAll(sourceMatchers.stream()
                        .map(sourceMatcher -> RelationshipRuleMatcher.builder()
                                .ruleId(clonedRule.getId())
                                .subjectType(sourceMatcher.getSubjectType())
                                .matcherType(sourceMatcher.getMatcherType())
                                .operator(sourceMatcher.getOperator())
                                .valueText(sourceMatcher.getValueText())
                                .valueJson(sourceMatcher.getValueJson())
                                .build())
                        .toList());
            }
        }
        return clonedSet;
    }

    @Transactional
    public RelationshipDefinitionSet updateSet(Long orgId, Long setId, RelationshipDefinitionSetRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        RelationshipDefinitionSet set = getSet(orgId, setId);
        String renamed = request.getName().trim();
        if (!set.getName().equals(renamed) && definitionSetRepository.existsByOrganizationIdAndName(orgId, renamed)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 기관에 동일한 세트명이 이미 존재합니다.");
        }
        set.rename(renamed);
        if (request.isActive()) {
            set.activate();
        } else {
            set.deactivate();
        }
        if (request.isDefaultSet()) {
            makeDefault(orgId, setId);
        } else {
            set.unmarkAsDefault();
        }
        return set;
    }

    @Transactional
    public void makeDefault(Long orgId, Long setId) {
        SecurityUtils.checkOrgAccess(orgId);
        RelationshipDefinitionSet set = getSet(orgId, setId);
        if (!set.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비활성 세트는 기본 세트로 지정할 수 없습니다.");
        }
        definitionSetRepository.findByOrganizationIdOrderByNameAsc(orgId)
                .forEach(s -> {
                    if (s.getId().equals(setId)) {
                        s.markAsDefault();
                    } else {
                        s.unmarkAsDefault();
                    }
                });
    }

    @Transactional
    public RelationshipDefinitionRule createRule(Long orgId, Long setId, RelationshipDefinitionRuleRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        getSet(orgId, setId);
        return definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName(request.getRuleName().trim())
                .relationType(request.getRelationType())
                .priority(request.getPriority())
                .active(request.isActive())
                .build());
    }

    @Transactional
    public RelationshipDefinitionRule updateRule(Long orgId, Long setId, Long ruleId, RelationshipDefinitionRuleRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        RelationshipDefinitionRule rule = getRule(orgId, setId, ruleId);
        rule.updateRule(request.getRuleName().trim(), request.getRelationType(), request.getPriority());
        if (request.isActive()) {
            rule.activate();
        } else {
            rule.deactivate();
        }
        return rule;
    }

    @Transactional
    public void deleteRule(Long orgId, Long setId, Long ruleId) {
        SecurityUtils.checkOrgAccess(orgId);
        RelationshipDefinitionRule rule = getRule(orgId, setId, ruleId);
        matcherRepository.findByRuleIdOrderByIdAsc(ruleId).forEach(matcherRepository::delete);
        definitionRuleRepository.delete(rule);
    }

    @Transactional
    public RelationshipRuleMatcher createMatcher(Long orgId, Long setId, Long ruleId, RelationshipMatcherRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        getRule(orgId, setId, ruleId);
        validateMatcherV1(request);
        return matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(ruleId)
                .subjectType(request.getSubjectType())
                .matcherType(request.getMatcherType())
                .operator(request.getOperator())
                .valueText(request.getValueText().trim())
                .build());
    }

    @Transactional
    public RelationshipRuleMatcher updateMatcher(Long orgId, Long setId, Long ruleId, Long matcherId, RelationshipMatcherRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        getRule(orgId, setId, ruleId);
        validateMatcherV1(request);
        RelationshipRuleMatcher matcher = matcherRepository.findByRuleIdAndId(ruleId, matcherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매처를 찾을 수 없습니다."));
        matcher.update(
                request.getSubjectType(),
                request.getMatcherType(),
                request.getOperator(),
                request.getValueText().trim(),
                null
        );
        return matcher;
    }

    @Transactional
    public void deleteMatcher(Long orgId, Long setId, Long ruleId, Long matcherId) {
        SecurityUtils.checkOrgAccess(orgId);
        getRule(orgId, setId, ruleId);
        RelationshipRuleMatcher matcher = matcherRepository.findByRuleIdAndId(ruleId, matcherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매처를 찾을 수 없습니다."));
        matcherRepository.delete(matcher);
    }

    public RelationshipDefinitionSet getSet(Long orgId, Long setId) {
        return definitionSetRepository.findByOrganizationIdAndId(orgId, setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관계 정의 세트를 찾을 수 없습니다."));
    }

    private RelationshipDefinitionRule getRule(Long orgId, Long setId, Long ruleId) {
        getSet(orgId, setId);
        return definitionRuleRepository.findBySetIdAndId(setId, ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관계 정의 룰을 찾을 수 없습니다."));
    }

    private void validateMatcherV1(RelationshipMatcherRequest request) {
        if (request.getMatcherType() != RelationshipMatcherType.EMPLOYEE
                && request.getMatcherType() != RelationshipMatcherType.DEPARTMENT
                && request.getMatcherType() != RelationshipMatcherType.JOB_TITLE
                && request.getMatcherType() != RelationshipMatcherType.ATTRIBUTE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "v1에서는 EMPLOYEE/DEPARTMENT/JOB_TITLE/ATTRIBUTE만 지원합니다.");
        }
        if (request.getOperator() != RelationshipRuleOperator.IN
                && request.getOperator() != RelationshipRuleOperator.NOT_IN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "v1에서는 IN/NOT_IN만 지원합니다.");
        }
    }
}
