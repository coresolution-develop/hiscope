package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.rule.dto.SimpleRelationshipPreviewItem;
import com.hiscope.evaluation.domain.evaluation.rule.dto.SimpleRelationshipWizardRequest;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimpleRelationshipWizardService {

    private static final int PREVIEW_MAX = 200;
    private static final String WIZARD_SET_PREFIX = "[간편설정] 세션#";

    private final EvaluationSessionRepository sessionRepository;
    private final RelationshipDefinitionSetRepository definitionSetRepository;
    private final RelationshipDefinitionRuleRepository definitionRuleRepository;
    private final RelationshipRuleMatcherRepository matcherRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper;

    public List<String> getAvailablePositions(Long orgId) {
        return employeeRepository.findDistinctPositionsByOrganizationId(orgId);
    }

    public Map<String, Object> preview(Long sessionId, Long orgId, SimpleRelationshipWizardRequest req) {
        List<Employee> activeEmployees = employeeRepository.findByOrganizationIdAndStatusOrderByNameAsc(orgId, "ACTIVE");

        Set<Long> deptIds = activeEmployees.stream()
                .map(Employee::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of() :
                departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(d -> d.getId(), d -> d.getName()));

        Map<String, List<Employee>> byPosition = activeEmployees.stream()
                .filter(e -> e.getPosition() != null)
                .collect(Collectors.groupingBy(Employee::getPosition));

        List<SimpleRelationshipPreviewItem> items = new ArrayList<>();
        Set<String> dedupKeys = new HashSet<>();

        if (req.upwardEnabled()) {
            addPairs(items, dedupKeys, "UPWARD", "상향 평가",
                    resolveByPositions(byPosition, req.upwardEvaluatorPositions()),
                    resolveByPositions(byPosition, req.upwardEvaluateePositions()),
                    deptNameMap);
        }
        if (req.peerEnabled()) {
            List<Employee> peers = resolveByPositions(byPosition, req.peerPositions());
            addPairs(items, dedupKeys, "PEER", "동료 평가", peers, peers, deptNameMap);
        }
        if (req.downwardEnabled()) {
            addPairs(items, dedupKeys, "DOWNWARD", "하향 평가",
                    resolveByPositions(byPosition, req.downwardEvaluatorPositions()),
                    resolveByPositions(byPosition, req.downwardEvaluateePositions()),
                    deptNameMap);
        }

        int total = items.size();
        List<SimpleRelationshipPreviewItem> limited = items.size() > PREVIEW_MAX ? items.subList(0, PREVIEW_MAX) : items;
        return Map.of("items", limited, "totalCount", total);
    }

    @Transactional
    public void apply(Long sessionId, Long orgId, Long accountId, SimpleRelationshipWizardRequest req) {
        EvaluationSession session = sessionRepository.findByOrganizationIdAndId(orgId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.isPending()) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED, "대기 상태의 세션에만 간편 설정을 적용할 수 있습니다.");
        }

        String wizardSetName = WIZARD_SET_PREFIX + sessionId;
        RelationshipDefinitionSet set = definitionSetRepository.findByOrganizationIdAndName(orgId, wizardSetName)
                .orElseGet(() -> definitionSetRepository.save(
                        RelationshipDefinitionSet.builder()
                                .organizationId(orgId)
                                .name(wizardSetName)
                                .isDefault(false)
                                .active(true)
                                .createdBy(accountId)
                                .build()));

        // 기존 룰/매처 전부 삭제 후 재생성 (overwrite-on-rerun)
        List<RelationshipDefinitionRule> existingRules = definitionRuleRepository.findBySetIdOrderByPriorityAscIdAsc(set.getId());
        if (!existingRules.isEmpty()) {
            List<Long> ruleIds = existingRules.stream().map(RelationshipDefinitionRule::getId).toList();
            List<RelationshipRuleMatcher> existingMatchers = matcherRepository.findByRuleIdInOrderByRuleIdAscIdAsc(ruleIds);
            matcherRepository.deleteAll(existingMatchers);
            definitionRuleRepository.deleteAll(existingRules);
        }

        int priority = 10;
        if (req.upwardEnabled() && notEmpty(req.upwardEvaluatorPositions()) && notEmpty(req.upwardEvaluateePositions())) {
            createRule(set.getId(), "상향평가", RelationshipRuleType.UPWARD, priority,
                    req.upwardEvaluatorPositions(), req.upwardEvaluateePositions());
            priority += 10;
        }
        if (req.peerEnabled() && notEmpty(req.peerPositions())) {
            createRule(set.getId(), "동료평가", RelationshipRuleType.PEER, priority,
                    req.peerPositions(), req.peerPositions());
            priority += 10;
        }
        if (req.downwardEnabled() && notEmpty(req.downwardEvaluatorPositions()) && notEmpty(req.downwardEvaluateePositions())) {
            createRule(set.getId(), "하향평가", RelationshipRuleType.DOWNWARD, priority,
                    req.downwardEvaluatorPositions(), req.downwardEvaluateePositions());
        }

        session.configureRelationshipDefinition(RelationshipGenerationMode.RULE_BASED, set.getId());
        log.info("간편 설정 적용 완료: sessionId={}, setId={}", sessionId, set.getId());
    }

    private void createRule(Long setId, String ruleName, RelationshipRuleType ruleType,
                            int priority, List<String> evaluatorPositions, List<String> evaluateePositions) {
        RelationshipDefinitionRule rule = definitionRuleRepository.save(
                RelationshipDefinitionRule.builder()
                        .setId(setId)
                        .ruleName(ruleName)
                        .relationType(ruleType)
                        .priority(priority)
                        .active(true)
                        .build());

        matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(rule.getId())
                .subjectType(RelationshipSubjectType.EVALUATOR)
                .matcherType(RelationshipMatcherType.POSITION)
                .operator(RelationshipRuleOperator.IN)
                .valueJson(toJson(evaluatorPositions))
                .build());

        matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(rule.getId())
                .subjectType(RelationshipSubjectType.EVALUATEE)
                .matcherType(RelationshipMatcherType.POSITION)
                .operator(RelationshipRuleOperator.IN)
                .valueJson(toJson(evaluateePositions))
                .build());
    }

    private void addPairs(List<SimpleRelationshipPreviewItem> items,
                          Set<String> dedupKeys,
                          String typeCode, String typeLabel,
                          List<Employee> evaluators,
                          List<Employee> evaluatees,
                          Map<Long, String> deptNameMap) {
        for (Employee evaluator : evaluators) {
            for (Employee evaluatee : evaluatees) {
                if (evaluator.getId().equals(evaluatee.getId())) continue;
                String key = evaluator.getId() + "_" + evaluatee.getId();
                if (!dedupKeys.add(key)) continue;
                items.add(new SimpleRelationshipPreviewItem(
                        typeCode,
                        evaluator.getName(),
                        deptNameMap.getOrDefault(evaluator.getDepartmentId(), "-"),
                        evaluator.getPosition(),
                        evaluatee.getName(),
                        deptNameMap.getOrDefault(evaluatee.getDepartmentId(), "-"),
                        evaluatee.getPosition()
                ));
            }
        }
    }

    private List<Employee> resolveByPositions(Map<String, List<Employee>> byPosition, List<String> positions) {
        if (positions == null || positions.isEmpty()) return List.of();
        return positions.stream()
                .flatMap(p -> byPosition.getOrDefault(p, List.of()).stream())
                .distinct()
                .toList();
    }

    private boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "직위 목록 직렬화 오류");
        }
    }
}
