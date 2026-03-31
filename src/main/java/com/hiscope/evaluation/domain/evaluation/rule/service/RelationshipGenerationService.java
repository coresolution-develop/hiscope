package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionGeneratedRelationship;
import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionRelationshipOverride;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.SessionRelationshipOverrideAction;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionGeneratedRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionRelationshipOverrideRepository;
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
public class RelationshipGenerationService {

    private final RelationshipDefinitionSetRepository definitionSetRepository;
    private final RelationshipDefinitionRuleRepository definitionRuleRepository;
    private final RelationshipRuleMatcherRepository matcherRepository;
    private final SessionGeneratedRelationshipRepository generatedRelationshipRepository;
    private final SessionRelationshipOverrideRepository overrideRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAttributeRepository employeeAttributeRepository;
    private final EmployeeAttributeValueRepository employeeAttributeValueRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public GenerationSummary generateForSession(Long orgId, Long sessionId, Long definitionSetId) {
        RelationshipDefinitionSet set = definitionSetRepository.findByOrganizationIdAndId(orgId, definitionSetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관계 정의 세트를 찾을 수 없습니다."));
        if (!set.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비활성화된 관계 정의 세트는 사용할 수 없습니다.");
        }

        List<RelationshipDefinitionRule> activeRules =
                definitionRuleRepository.findBySetIdAndActiveTrueOrderByPriorityAscIdAsc(definitionSetId);
        List<Long> ruleIds = activeRules.stream().map(RelationshipDefinitionRule::getId).toList();
        Map<Long, List<RelationshipRuleMatcher>> matchersByRuleId = ruleIds.isEmpty()
                ? Map.of()
                : matcherRepository.findByRuleIdInOrderByRuleIdAscIdAsc(ruleIds)
                .stream()
                .collect(Collectors.groupingBy(RelationshipRuleMatcher::getRuleId, LinkedHashMap::new, Collectors.toList()));

        List<Employee> activeEmployees = employeeRepository.findByOrganizationIdAndStatusOrderByNameAsc(orgId, "ACTIVE");
        Set<Long> allActiveEmployeeIds = activeEmployees.stream().map(Employee::getId).collect(Collectors.toSet());
        Map<Long, Employee> employeeMap = activeEmployees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        Map<Long, Set<String>> employeeAttributeValues = buildEmployeeAttributeValueMap(activeEmployees, orgId);

        long matcherCount = matchersByRuleId.values().stream().mapToLong(List::size).sum();

        List<SessionGeneratedRelationship> generated = new ArrayList<>();
        Set<String> dedupKeys = new HashSet<>();
        long excludedCount = 0L;
        long selfExcludedCount = 0L;
        long duplicateRemovedCount = 0L;
        Map<Long, Long> generatedCountByRule = new LinkedHashMap<>();

        generatedRelationshipRepository.deleteBySessionId(sessionId);
        generatedRelationshipRepository.flush();

        for (RelationshipDefinitionRule rule : activeRules) {
            List<RelationshipRuleMatcher> matchers = matchersByRuleId.getOrDefault(rule.getId(), List.of());
            long ruleGeneratedCount = 0L;

            Set<Long> evaluatorIds = resolveSubjectCandidates(
                    allActiveEmployeeIds, employeeMap, employeeAttributeValues, matchers, RelationshipSubjectType.EVALUATOR
            );
            Set<Long> evaluateeIds = resolveSubjectCandidates(
                    allActiveEmployeeIds, employeeMap, employeeAttributeValues, matchers, RelationshipSubjectType.EVALUATEE
            );
            Set<Long> excludeIds = resolveSubjectCandidates(
                    allActiveEmployeeIds, employeeMap, employeeAttributeValues, matchers, RelationshipSubjectType.EXCLUDE
            );

            for (Long evaluatorId : evaluatorIds) {
                for (Long evaluateeId : evaluateeIds) {
                    if (evaluatorId.equals(evaluateeId)) {
                        selfExcludedCount++;
                        continue;
                    }
                    if (excludeIds.contains(evaluatorId) || excludeIds.contains(evaluateeId)) {
                        excludedCount++;
                        continue;
                    }
                    String key = evaluatorId + "_" + evaluateeId;
                    if (!dedupKeys.add(key)) {
                        duplicateRemovedCount++;
                        continue;
                    }
                    generated.add(SessionGeneratedRelationship.builder()
                            .sessionId(sessionId)
                            .organizationId(orgId)
                            .evaluatorId(evaluatorId)
                            .evaluateeId(evaluateeId)
                            .relationType(rule.getRelationType())
                            .sourceRuleId(rule.getId())
                            .active(true)
                            .build());
                    ruleGeneratedCount++;
                }
            }
            generatedCountByRule.put(rule.getId(), ruleGeneratedCount);
        }

        generatedRelationshipRepository.saveAll(generated);

        long finalCount = resolveFinalRelationships(orgId, sessionId).size();
        log.info("Generated {} rule-based relationships for session {}, finalWithOverrides={}",
                generated.size(), sessionId, finalCount);

        return new GenerationSummary(
                sessionId,
                definitionSetId,
                (long) activeRules.size(),
                matcherCount,
                (long) generated.size(),
                finalCount,
                excludedCount,
                selfExcludedCount,
                duplicateRemovedCount,
                generatedCountByRule
        );
    }

    public List<FinalRelationship> resolveFinalRelationships(Long orgId, Long sessionId) {
        Set<Long> activeEmployees = employeeRepository.findByOrganizationIdAndStatusOrderByNameAsc(orgId, "ACTIVE")
                .stream().map(Employee::getId).collect(Collectors.toSet());

        Map<String, FinalRelationship> finalMap = new LinkedHashMap<>();
        for (SessionGeneratedRelationship generated : generatedRelationshipRepository.findBySessionIdAndActiveTrueOrderByEvaluatorIdAscEvaluateeIdAsc(sessionId)) {
            String key = generated.getEvaluatorId() + "_" + generated.getEvaluateeId();
            finalMap.put(key, new FinalRelationship(
                    generated.getEvaluatorId(),
                    generated.getEvaluateeId(),
                    generated.getRelationType(),
                    generated.getSourceRuleId(),
                    false
            ));
        }

        for (SessionRelationshipOverride override : overrideRepository.findBySessionIdOrderByIdAsc(sessionId)) {
            if (!activeEmployees.contains(override.getEvaluatorId()) || !activeEmployees.contains(override.getEvaluateeId())) {
                continue;
            }
            if (override.getEvaluatorId().equals(override.getEvaluateeId())) {
                continue;
            }
            String key = override.getEvaluatorId() + "_" + override.getEvaluateeId();
            if (override.getAction() == SessionRelationshipOverrideAction.REMOVE) {
                finalMap.remove(key);
            } else if (override.getAction() == SessionRelationshipOverrideAction.ADD) {
                finalMap.put(key, new FinalRelationship(
                        override.getEvaluatorId(),
                        override.getEvaluateeId(),
                        RelationshipRuleType.CUSTOM,
                        null,
                        true
                ));
            }
        }
        return new ArrayList<>(finalMap.values());
    }

    private Set<Long> resolveSubjectCandidates(Set<Long> allActiveEmployeeIds,
                                               Map<Long, Employee> employeeMap,
                                               Map<Long, Set<String>> employeeAttributeValues,
                                               List<RelationshipRuleMatcher> matchers,
                                               RelationshipSubjectType subjectType) {
        List<RelationshipRuleMatcher> targetMatchers = matchers.stream()
                .filter(m -> m.getSubjectType() == subjectType)
                .toList();
        if (targetMatchers.isEmpty()) {
            if (subjectType == RelationshipSubjectType.EXCLUDE) {
                return new HashSet<>();
            }
            return new HashSet<>(allActiveEmployeeIds);
        }
        Set<Long> candidates = new HashSet<>(allActiveEmployeeIds);
        for (RelationshipRuleMatcher matcher : targetMatchers) {
            Set<Long> matched = matchByMatcher(allActiveEmployeeIds, employeeMap, employeeAttributeValues, matcher);
            if (matcher.getOperator() == RelationshipRuleOperator.NOT_IN) {
                Set<Long> included = new HashSet<>(allActiveEmployeeIds);
                included.removeAll(matched);
                matched = included;
            }
            candidates.retainAll(matched);
        }
        return candidates;
    }

    private Set<Long> matchByMatcher(Set<Long> allActiveEmployeeIds,
                                     Map<Long, Employee> employeeMap,
                                     Map<Long, Set<String>> employeeAttributeValues,
                                     RelationshipRuleMatcher matcher) {
        List<String> values = extractMatcherValues(matcher);
        if (values.isEmpty()) {
            return Collections.emptySet();
        }
        return switch (matcher.getMatcherType()) {
            case EMPLOYEE -> values.stream()
                    .map(this::parseLongSafe)
                    .flatMap(Optional::stream)
                    .filter(allActiveEmployeeIds::contains)
                    .collect(Collectors.toSet());
            case DEPARTMENT -> {
                Set<Long> departmentIds = values.stream()
                        .map(this::parseLongSafe)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet());
                yield employeeMap.values().stream()
                        .filter(e -> e.getDepartmentId() != null && departmentIds.contains(e.getDepartmentId()))
                        .map(Employee::getId)
                        .collect(Collectors.toSet());
            }
            case JOB_TITLE -> {
                Set<String> titles = values.stream().map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toSet());
                yield employeeMap.values().stream()
                        .filter(e -> e.getJobTitle() != null && titles.contains(e.getJobTitle()))
                        .map(Employee::getId)
                        .collect(Collectors.toSet());
            }
            case POSITION -> {
                Set<String> positions = values.stream().map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toSet());
                yield employeeMap.values().stream()
                        .filter(e -> e.getPosition() != null && positions.contains(e.getPosition()))
                        .map(Employee::getId)
                        .collect(Collectors.toSet());
            }
            case ATTRIBUTE -> matchByAttribute(employeeAttributeValues, values);
        };
    }

    private Set<Long> matchByAttribute(Map<Long, Set<String>> employeeAttributeValues, List<String> values) {
        Set<AttributeCondition> conditions = values.stream()
                .map(this::parseAttributeCondition)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (conditions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> matched = new HashSet<>();
        for (Map.Entry<Long, Set<String>> entry : employeeAttributeValues.entrySet()) {
            for (AttributeCondition condition : conditions) {
                if (condition.value() == null) {
                    if (entry.getValue().stream().anyMatch(v -> v.startsWith(condition.key() + "="))) {
                        matched.add(entry.getKey());
                        break;
                    }
                } else if (entry.getValue().contains(condition.key() + "=" + condition.value())) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }
        return matched;
    }

    private AttributeCondition parseAttributeCondition(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        String delimiter = normalized.contains("=") ? "=" : (normalized.contains(":") ? ":" : null);
        if (delimiter == null) {
            return new AttributeCondition(normalized, null);
        }
        String[] parts = normalized.split(delimiter, 2);
        String key = parts[0].trim();
        String attrValue = parts.length > 1 ? parts[1].trim() : null;
        if (key.isBlank()) {
            return null;
        }
        if (attrValue != null && attrValue.isBlank()) {
            attrValue = null;
        }
        return new AttributeCondition(key, attrValue);
    }

    private Map<Long, Set<String>> buildEmployeeAttributeValueMap(List<Employee> activeEmployees, Long orgId) {
        Set<Long> employeeIds = activeEmployees.stream().map(Employee::getId).collect(Collectors.toSet());
        if (employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> attributeKeyMap = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(orgId).stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a.getAttributeKey()));
        List<EmployeeAttributeValue> values =
                employeeAttributeValueRepository.findByEmployeeIdInOrderByEmployeeIdAscAttributeIdAscValueTextAsc(new ArrayList<>(employeeIds));
        Map<Long, Set<String>> result = new HashMap<>();
        for (EmployeeAttributeValue value : values) {
            String key = attributeKeyMap.get(value.getAttributeId());
            if (key == null) {
                continue;
            }
            result.computeIfAbsent(value.getEmployeeId(), k -> new HashSet<>())
                    .add(key + "=" + value.getValueText());
        }
        return result;
    }

    private List<String> extractMatcherValues(RelationshipRuleMatcher matcher) {
        List<String> values = new ArrayList<>();
        if (matcher.getValueJson() != null && !matcher.getValueJson().isBlank()) {
            try {
                values.addAll(objectMapper.readValue(matcher.getValueJson(), new TypeReference<List<String>>() {
                }));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "Matcher value_json 형식이 올바르지 않습니다.");
            }
        }
        if (matcher.getValueText() != null && !matcher.getValueText().isBlank()) {
            values.addAll(Arrays.stream(matcher.getValueText().split(","))
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .toList());
        }
        return values;
    }

    private Optional<Long> parseLongSafe(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private record AttributeCondition(String key, String value) {
    }

    public record FinalRelationship(
            Long evaluatorId,
            Long evaluateeId,
            RelationshipRuleType relationType,
            Long sourceRuleId,
            boolean overriddenByAdmin
    ) {
    }

    public record GenerationSummary(
            Long sessionId,
            Long definitionSetId,
            Long activeRuleCount,
            Long matcherCount,
            Long generatedRelationshipCount,
            Long finalRelationshipCount,
            Long excludedCount,
            Long selfExcludedCount,
            Long duplicateRemovedCount,
            Map<Long, Long> generatedCountByRule
    ) {
    }
}
