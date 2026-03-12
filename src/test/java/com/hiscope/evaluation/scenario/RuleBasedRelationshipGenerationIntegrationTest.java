package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionRelationshipOverride;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.SessionRelationshipOverrideAction;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionGeneratedRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionRelationshipOverrideRepository;
import com.hiscope.evaluation.domain.evaluation.rule.service.RelationshipGenerationService;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
import com.hiscope.evaluation.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class RuleBasedRelationshipGenerationIntegrationTest {

    @Autowired
    private RelationshipGenerationService relationshipGenerationService;

    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;

    @Autowired
    private RelationshipDefinitionRuleRepository definitionRuleRepository;

    @Autowired
    private RelationshipRuleMatcherRepository matcherRepository;

    @Autowired
    private SessionGeneratedRelationshipRepository generatedRelationshipRepository;

    @Autowired
    private SessionRelationshipOverrideRepository overrideRepository;

    @Autowired
    private EmployeeAttributeRepository employeeAttributeRepository;

    @Autowired
    private EmployeeAttributeValueRepository employeeAttributeValueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EvaluationSessionRepository sessionRepository;

    @Autowired
    private EvaluationSessionService sessionService;

    @Autowired
    private EvaluationRelationshipRepository relationshipRepository;

    @Autowired
    private EvaluationAssignmentRepository assignmentRepository;

    @BeforeEach
    void setupAuthContext() {
        CustomUserDetails principal = CustomUserDetails.builder()
                .id(2L)
                .loginId("admin")
                .password("noop")
                .organizationId(1L)
                .employeeId(null)
                .role("ROLE_ORG_ADMIN")
                .name("기관관리자")
                .mustChangePassword(false)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 부서_matcher_기반_관계_생성_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "부서매처세트");
        createRuleWithMatchers(set.getId(), "dept-rule", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.DEPARTMENT, RelationshipRuleOperator.IN, "3"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.DEPARTMENT, RelationshipRuleOperator.IN, "4")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "부서매처세션");

        var summary = relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        assertThat(summary.generatedRelationshipCount()).isEqualTo(6L);
        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId()))
                .hasSize(6);
    }

    @Test
    void 직원_attribute_matcher_기반_관계_생성_테스트() {
        EmployeeAttribute attr = employeeAttributeRepository.save(EmployeeAttribute.builder()
                .organizationId(1L)
                .attributeKey("track")
                .attributeName("트랙")
                .active(true)
                .build());
        employeeAttributeValueRepository.save(EmployeeAttributeValue.builder().employeeId(1L).attributeId(attr.getId()).valueText("A").build());
        employeeAttributeValueRepository.save(EmployeeAttributeValue.builder().employeeId(2L).attributeId(attr.getId()).valueText("A").build());
        employeeAttributeValueRepository.save(EmployeeAttributeValue.builder().employeeId(4L).attributeId(attr.getId()).valueText("B").build());

        RelationshipDefinitionSet set = createDefinitionSet(1L, "속성매처세트");
        createRuleWithMatchers(set.getId(), "attr-rule", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.IN, "track=A"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.IN, "track=B")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "속성매처세션");

        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId()))
                .extracting("evaluatorId", "evaluateeId")
                .containsExactlyInAnyOrder(
                        tuple(1L, 4L),
                        tuple(2L, 4L)
                );
    }

    @Test
    void 독립속성_matcher_독립동작_테스트() {
        EmployeeAttribute changeTeam = getOrCreateAttribute(1L, "change_innovation_team", "경혁팀");
        EmployeeAttribute changeTeamLeader = getOrCreateAttribute(1L, "change_innovation_team_leader", "경혁팀장");
        EmployeeAttribute departmentHead = getOrCreateAttribute(1L, "department_head", "부서장");

        upsertEmployeeAttributeValue(1L, changeTeam.getId(), "Y");
        upsertEmployeeAttributeValue(1L, changeTeamLeader.getId(), "Y");
        upsertEmployeeAttributeValue(1L, departmentHead.getId(), "N");

        upsertEmployeeAttributeValue(2L, changeTeam.getId(), "Y");
        upsertEmployeeAttributeValue(2L, changeTeamLeader.getId(), "Y");
        upsertEmployeeAttributeValue(2L, departmentHead.getId(), "Y");

        upsertEmployeeAttributeValue(3L, changeTeam.getId(), "Y");
        upsertEmployeeAttributeValue(3L, changeTeamLeader.getId(), "N");
        upsertEmployeeAttributeValue(3L, departmentHead.getId(), "Y");

        RelationshipDefinitionSet set = createDefinitionSet(1L, "독립속성매처세트");
        createRuleWithMatchers(set.getId(), "independent-attr-rule", RelationshipRuleType.CUSTOM, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1,2,3"),
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.IN, "change_innovation_team_leader=Y"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1,2,3"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.IN, "department_head=Y"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1,2,3"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.ATTRIBUTE, RelationshipRuleOperator.IN, "change_innovation_team_leader=N")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "독립속성매처세션");

        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId()))
                .extracting("evaluatorId", "evaluateeId")
                .containsExactly(tuple(1L, 2L));
    }

    @Test
    void jobTitle_matcher_기반_관계_생성_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "직책매처세트");
        createRuleWithMatchers(set.getId(), "title-rule", RelationshipRuleType.DOWNWARD, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.JOB_TITLE, RelationshipRuleOperator.IN, "팀장,실장,부문장"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.JOB_TITLE, RelationshipRuleOperator.IN, "팀원")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "직책매처세션");

        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        var generated = generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId());
        assertThat(generated).isNotEmpty();
        Set<Long> evaluatorIds = generated.stream().map(r -> r.getEvaluatorId()).collect(Collectors.toSet());
        List<Employee> evaluators = evaluatorIds.stream()
                .map(id -> employeeRepository.findByOrganizationIdAndId(1L, id).orElseThrow())
                .toList();
        assertThat(evaluators).allMatch(Employee::isTeamLeader);
    }

    @Test
    void exclude_matcher_적용_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "exclude세트");
        createRuleWithMatchers(set.getId(), "exclude-rule", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.DEPARTMENT, RelationshipRuleOperator.IN, "3"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.DEPARTMENT, RelationshipRuleOperator.IN, "4"),
                matcher(RelationshipSubjectType.EXCLUDE, RelationshipMatcherType.DEPARTMENT, RelationshipRuleOperator.IN, "4")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "exclude세션");

        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId()))
                .isEmpty();
    }

    @Test
    void 자기평가_제거_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "self세트");
        createRuleWithMatchers(set.getId(), "self-rule", RelationshipRuleType.PEER, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1,2"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1,2")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "self세션");

        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId()))
                .extracting("evaluatorId", "evaluateeId")
                .containsExactlyInAnyOrder(tuple(1L, 2L), tuple(2L, 1L));
    }

    @Test
    void 중복관계_제거_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "dedup세트");
        RelationshipDefinitionRule high = createRuleWithMatchers(set.getId(), "high", RelationshipRuleType.UPWARD, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "2")
        ));
        createRuleWithMatchers(set.getId(), "low", RelationshipRuleType.CUSTOM, 20, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "2")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "dedup세션");

        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());

        var generated = generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId());
        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getSourceRuleId()).isEqualTo(high.getId());
    }

    @Test
    void override_ADD_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "override-add");
        createRuleWithMatchers(set.getId(), "empty", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "override-add-session");
        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());
        overrideRepository.save(SessionRelationshipOverride.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(1L)
                .evaluateeId(2L)
                .action(SessionRelationshipOverrideAction.ADD)
                .reason("수동 추가")
                .createdBy(2L)
                .build());

        var finalRelationships = relationshipGenerationService.resolveFinalRelationships(1L, session.getId());

        assertThat(finalRelationships)
                .extracting(RelationshipGenerationService.FinalRelationship::evaluatorId,
                        RelationshipGenerationService.FinalRelationship::evaluateeId,
                        RelationshipGenerationService.FinalRelationship::overriddenByAdmin)
                .contains(tuple(1L, 2L, true));
    }

    @Test
    void override_REMOVE_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "override-remove");
        createRuleWithMatchers(set.getId(), "remove-rule", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "2")
        ));
        EvaluationSession session = createRuleBasedSession(1L, set.getId(), "override-remove-session");
        relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());
        overrideRepository.save(SessionRelationshipOverride.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(1L)
                .evaluateeId(2L)
                .action(SessionRelationshipOverrideAction.REMOVE)
                .reason("수동 제외")
                .createdBy(2L)
                .build());

        var finalRelationships = relationshipGenerationService.resolveFinalRelationships(1L, session.getId());

        assertThat(finalRelationships).isEmpty();
    }

    @Test
    void LEGACY_vs_RULE_BASED_세션_분기_테스트() {
        EvaluationSession legacySession = createLegacySession(1L, "legacy-session");
        relationshipRepository.save(com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship.builder()
                .sessionId(legacySession.getId())
                .organizationId(1L)
                .evaluatorId(1L)
                .evaluateeId(2L)
                .relationType("MANUAL")
                .source("ADMIN_ADDED")
                .active(true)
                .build());

        RelationshipDefinitionSet set = createDefinitionSet(1L, "rule-session-set");
        createRuleWithMatchers(set.getId(), "branch-rule", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "3")
        ));
        EvaluationSession ruleSession = createRuleBasedSession(1L, set.getId(), "rule-session");

        sessionService.start(1L, legacySession.getId());
        sessionService.start(1L, ruleSession.getId());

        assertThat(assignmentRepository.findBySessionId(legacySession.getId())).hasSize(1);
        assertThat(assignmentRepository.findBySessionId(ruleSession.getId())).hasSize(1);
        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(legacySession.getId())).isEmpty();
        assertThat(generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(ruleSession.getId())).hasSize(1);
    }

    @Test
    void 최종관계_legacy_테이블_미러링_테스트() {
        RelationshipDefinitionSet set = createDefinitionSet(1L, "mirror-set");
        createRuleWithMatchers(set.getId(), "mirror-rule", RelationshipRuleType.CROSS_DEPT, 10, List.of(
                matcher(RelationshipSubjectType.EVALUATOR, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "1"),
                matcher(RelationshipSubjectType.EVALUATEE, RelationshipMatcherType.EMPLOYEE, RelationshipRuleOperator.IN, "3")
        ));
        EvaluationSession ruleSession = createRuleBasedSession(1L, set.getId(), "mirror-session");
        overrideRepository.save(SessionRelationshipOverride.builder()
                .sessionId(ruleSession.getId())
                .organizationId(1L)
                .evaluatorId(2L)
                .evaluateeId(4L)
                .action(SessionRelationshipOverrideAction.ADD)
                .reason("수동 추가")
                .createdBy(2L)
                .build());

        sessionService.start(1L, ruleSession.getId());

        assertThat(relationshipRepository.findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(ruleSession.getId()))
                .extracting("evaluatorId", "evaluateeId")
                .containsExactlyInAnyOrder(
                        tuple(1L, 3L),
                        tuple(2L, 4L)
                );
    }

    private RelationshipDefinitionSet createDefinitionSet(Long orgId, String name) {
        return definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(orgId)
                .name(name + "-" + System.nanoTime())
                .isDefault(false)
                .active(true)
                .createdBy(2L)
                .build());
    }

    private RelationshipDefinitionRule createRuleWithMatchers(Long setId,
                                                              String ruleName,
                                                              RelationshipRuleType relationType,
                                                              int priority,
                                                              List<RelationshipRuleMatcher> matchers) {
        RelationshipDefinitionRule rule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(setId)
                .ruleName(ruleName + "-" + System.nanoTime())
                .relationType(relationType)
                .priority(priority)
                .active(true)
                .build());
        matcherRepository.saveAll(matchers.stream()
                .map(m -> RelationshipRuleMatcher.builder()
                        .ruleId(rule.getId())
                        .subjectType(m.getSubjectType())
                        .matcherType(m.getMatcherType())
                        .operator(m.getOperator())
                        .valueText(m.getValueText())
                        .valueJson(m.getValueJson())
                        .build())
                .toList());
        return rule;
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

    private EvaluationSession createRuleBasedSession(Long orgId, Long definitionSetId, String name) {
        return sessionRepository.save(EvaluationSession.builder()
                .organizationId(orgId)
                .name(name + "-" + System.nanoTime())
                .description("rule based")
                .templateId(1L)
                .allowResubmit(false)
                .createdBy(2L)
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(definitionSetId)
                .status("PENDING")
                .build());
    }

    private EvaluationSession createLegacySession(Long orgId, String name) {
        return sessionRepository.save(EvaluationSession.builder()
                .organizationId(orgId)
                .name(name + "-" + System.nanoTime())
                .description("legacy")
                .templateId(1L)
                .allowResubmit(false)
                .createdBy(2L)
                .relationshipGenerationMode(RelationshipGenerationMode.LEGACY)
                .relationshipDefinitionSetId(null)
                .status("PENDING")
                .build());
    }

    private EmployeeAttribute getOrCreateAttribute(Long orgId, String key, String name) {
        return employeeAttributeRepository.findByOrganizationIdAndAttributeKey(orgId, key)
                .orElseGet(() -> employeeAttributeRepository.save(EmployeeAttribute.builder()
                        .organizationId(orgId)
                        .attributeKey(key)
                        .attributeName(name)
                        .active(true)
                        .build()));
    }

    private void upsertEmployeeAttributeValue(Long employeeId, Long attributeId, String value) {
        EmployeeAttributeValue saved = employeeAttributeValueRepository.findByEmployeeIdAndAttributeId(employeeId, attributeId)
                .map(existing -> {
                    existing.updateValue(value);
                    return existing;
                })
                .orElseGet(() -> EmployeeAttributeValue.builder()
                        .employeeId(employeeId)
                        .attributeId(attributeId)
                        .valueText(value)
                        .build());
        employeeAttributeValueRepository.save(saved);
    }
}
