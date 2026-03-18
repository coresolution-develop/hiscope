package com.hiscope.evaluation.domain.organization.service;

import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationProfileBootstrapService {

    private final EmployeeAttributeRepository employeeAttributeRepository;
    private final RelationshipDefinitionSetRepository definitionSetRepository;
    private final RelationshipDefinitionRuleRepository definitionRuleRepository;
    private final RelationshipRuleMatcherRepository matcherRepository;

    private static final Map<String, String> COMMON_ATTRIBUTES = Map.of(
            "institution_head", "기관장",
            "unit_head", "소속장",
            "department_head", "부서장",
            "evaluation_excluded", "평가제외"
    );

    private static final Map<String, String> HOSPITAL_ATTRIBUTES = Map.of(
            "change_innovation_team", "경혁팀",
            "change_innovation_team_leader", "경혁팀장",
            "single_member_department", "1인부서",
            "clinical_team_leader", "진료팀장",
            "medical_leader", "의료리더"
    );

    private static final Map<String, String> AFFILIATE_ATTRIBUTES = Map.of(
            "affiliate_policy_group", "계열사 정책그룹"
    );

    public void bootstrap(Long orgId, OrganizationType organizationType, OrganizationProfile organizationProfile) {
        bootstrapAttributes(orgId, organizationType, organizationProfile);
        bootstrapDefaultRuleSet(orgId, organizationType, organizationProfile);
    }

    private void bootstrapAttributes(Long orgId, OrganizationType organizationType, OrganizationProfile organizationProfile) {
        Map<String, String> merged = new LinkedHashMap<>(COMMON_ATTRIBUTES);
        if (organizationProfile == OrganizationProfile.HOSPITAL_DEFAULT) {
            merged.putAll(HOSPITAL_ATTRIBUTES);
        } else if (organizationType == OrganizationType.AFFILIATE) {
            merged.putAll(AFFILIATE_ATTRIBUTES);
        }
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (employeeAttributeRepository.existsByOrganizationIdAndAttributeKey(orgId, entry.getKey())) {
                continue;
            }
            employeeAttributeRepository.save(EmployeeAttribute.builder()
                    .organizationId(orgId)
                    .attributeKey(entry.getKey())
                    .attributeName(entry.getValue())
                    .active(true)
                    .build());
        }
    }

    private void bootstrapDefaultRuleSet(Long orgId, OrganizationType organizationType, OrganizationProfile organizationProfile) {
        if (definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(orgId).isPresent()) {
            return;
        }
        String setName = switch (organizationProfile) {
            case HOSPITAL_DEFAULT -> "병원 기본 Rule Set v1.1";
            case AFFILIATE_HOSPITAL -> "병원계열 계열사 기본 Rule Set v1.1";
            case AFFILIATE_GENERAL -> "일반 계열사 기본 Rule Set v1.1";
        };
        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(orgId)
                .name(setName)
                .isDefault(true)
                .active(true)
                .createdBy(null)
                .build());
        if (organizationProfile == OrganizationProfile.HOSPITAL_DEFAULT) {
            bootstrapHospitalRules(set.getId());
        } else {
            bootstrapAffiliateRules(set.getId());
        }
    }

    private void bootstrapHospitalRules(Long setId) {
        RelationshipDefinitionRule institutionHeadRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("기관장 전사 평가")
                .relationType(RelationshipRuleType.DOWNWARD)
                .priority(10)
                .active(true)
                .build());
        saveMatchers(institutionHeadRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "institution_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.NOT_IN, "institution_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule unitHeadRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("소속장 하향 평가")
                .relationType(RelationshipRuleType.DOWNWARD)
                .priority(20)
                .active(true)
                .build());
        saveMatchers(unitHeadRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "unit_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.NOT_IN, "unit_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule deptHeadRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("부서장 하향 평가")
                .relationType(RelationshipRuleType.DOWNWARD)
                .priority(30)
                .active(true)
                .build());
        saveMatchers(deptHeadRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "department_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.NOT_IN, "department_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule changeInnovationLeaderRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("경혁팀장-경혁팀 평가")
                .relationType(RelationshipRuleType.CUSTOM)
                .priority(40)
                .active(true)
                .build());
        saveMatchers(changeInnovationLeaderRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "change_innovation_team_leader=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, "change_innovation_team=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule clinicalLeaderRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("진료팀장 하향 평가")
                .relationType(RelationshipRuleType.CUSTOM)
                .priority(50)
                .active(true)
                .build());
        saveMatchers(clinicalLeaderRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "clinical_team_leader=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, "clinical_team_leader=N"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule singleMemberDeptRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("1인부서 보완 평가")
                .relationType(RelationshipRuleType.CROSS_DEPT)
                .priority(60)
                .active(true)
                .build());
        saveMatchers(singleMemberDeptRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "unit_head=Y,department_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, "single_member_department=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule medicalLeaderRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("의료리더 협업 평가")
                .relationType(RelationshipRuleType.PEER)
                .priority(70)
                .active(true)
                .build());
        saveMatchers(medicalLeaderRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "medical_leader=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, "medical_leader=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));
    }

    private void bootstrapAffiliateRules(Long setId) {
        RelationshipDefinitionRule institutionHeadRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("기관장 전사 평가")
                .relationType(RelationshipRuleType.DOWNWARD)
                .priority(10)
                .active(true)
                .build());
        saveMatchers(institutionHeadRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "institution_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.NOT_IN, "institution_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule unitHeadRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("소속장 하향 평가")
                .relationType(RelationshipRuleType.DOWNWARD)
                .priority(20)
                .active(true)
                .build());
        saveMatchers(unitHeadRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "unit_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.NOT_IN, "unit_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));

        RelationshipDefinitionRule deptHeadRule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName("부서장 하향 평가")
                .relationType(RelationshipRuleType.DOWNWARD)
                .priority(30)
                .active(true)
                .build());
        saveMatchers(deptHeadRule.getId(), List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, "department_head=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.NOT_IN, "department_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, "evaluation_excluded=Y")
        ));
    }

    private void saveMatchers(Long ruleId, List<RelationshipRuleMatcher> matchers) {
        matcherRepository.saveAll(matchers.stream()
                .map(m -> RelationshipRuleMatcher.builder()
                        .ruleId(ruleId)
                        .subjectType(m.getSubjectType())
                        .matcherType(m.getMatcherType())
                        .operator(m.getOperator())
                        .valueText(m.getValueText())
                        .build())
                .toList());
    }

    private RelationshipRuleMatcher matcher(RelationshipSubjectType subjectType,
                                            RelationshipMatcherType matcherType,
                                            String valueText) {
        return matcher(subjectType, matcherType, RelationshipRuleOperator.IN, valueText);
    }

    private RelationshipRuleMatcher matcher(RelationshipSubjectType subjectType,
                                            RelationshipMatcherType matcherType,
                                            RelationshipRuleOperator operator,
                                            String valueText) {
        return RelationshipRuleMatcher.builder()
                .subjectType(subjectType)
                .matcherType(matcherType)
                .operator(operator)
                .valueText(valueText)
                .build();
    }
}
