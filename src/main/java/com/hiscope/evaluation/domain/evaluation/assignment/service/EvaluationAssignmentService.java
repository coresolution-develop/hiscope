package com.hiscope.evaluation.domain.evaluation.assignment.service;

import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationAssignmentService {

    private final EvaluationAssignmentRepository assignmentRepository;
    private final EvaluationRelationshipRepository relationshipRepository;
    private final OrganizationRepository organizationRepository;
    private final EmployeeAttributeRepository employeeAttributeRepository;
    private final EmployeeAttributeValueRepository employeeAttributeValueRepository;
    private final QuestionGroupResolver questionGroupResolver;

    /**
     * 세션 시작 시 활성화된 평가 관계를 기반으로 Assignment 스냅샷 생성.
     * 이후 Relationship 변경은 Assignment에 영향 없음.
     */
    @Transactional
    public void createAssignmentsForSession(EvaluationSession session) {
        List<EvaluationRelationship> activeRelationships =
                relationshipRepository.findActiveBySession(session.getId());

        Map<Long, EmployeeRoleFlags> roleFlagsByEmployeeId = resolveRoleFlags(session.getOrganizationId(), activeRelationships);
        OrganizationProfile organizationProfile = organizationRepository.findById(session.getOrganizationId())
                .map(org -> org.getOrganizationProfile())
                .orElse(OrganizationProfile.HOSPITAL_DEFAULT);
        boolean ruleBased = session.getRelationshipGenerationMode() == RelationshipGenerationMode.RULE_BASED;

        List<EvaluationAssignment> assignments = activeRelationships.stream()
                .filter(r -> !assignmentRepository.existsBySessionIdAndEvaluatorIdAndEvaluateeId(
                        session.getId(), r.getEvaluatorId(), r.getEvaluateeId()))
                .map(r -> {
                    String resolvedQuestionGroupCode = null;
                    if (ruleBased) {
                        EmployeeRoleFlags evaluatorFlags = roleFlagsByEmployeeId.getOrDefault(r.getEvaluatorId(), EmployeeRoleFlags.EMPTY);
                        EmployeeRoleFlags evaluateeFlags = roleFlagsByEmployeeId.getOrDefault(r.getEvaluateeId(), EmployeeRoleFlags.EMPTY);
                        resolvedQuestionGroupCode = questionGroupResolver.resolveForRuleBased(
                                organizationProfile,
                                r.getRelationType(),
                                evaluatorFlags.departmentHead(),
                                evaluateeFlags.departmentHead(),
                                evaluateeFlags.leader()
                        );
                    }
                    r.applyResolvedQuestionGroupCode(resolvedQuestionGroupCode);
                    return EvaluationAssignment.builder()
                            .sessionId(session.getId())
                            .organizationId(session.getOrganizationId())
                            .relationshipId(r.getId())
                            .evaluatorId(r.getEvaluatorId())
                            .evaluateeId(r.getEvaluateeId())
                            .status("PENDING")
                            .resolvedQuestionGroupCode(resolvedQuestionGroupCode)
                            .build();
                })
                .toList();

        assignmentRepository.saveAll(assignments);
        log.info("Created {} assignments for session {}", assignments.size(), session.getId());
    }

    private Map<Long, EmployeeRoleFlags> resolveRoleFlags(Long orgId, List<EvaluationRelationship> relationships) {
        if (relationships.isEmpty()) {
            return Map.of();
        }
        Set<Long> employeeIds = new HashSet<>();
        for (EvaluationRelationship rel : relationships) {
            employeeIds.add(rel.getEvaluatorId());
            employeeIds.add(rel.getEvaluateeId());
        }
        if (employeeIds.isEmpty()) {
            return Map.of();
        }

        Set<String> leaderKeys = Set.of("institution_head", "unit_head", "department_head");
        Map<Long, String> attributeKeyById = employeeAttributeRepository
                .findByOrganizationIdAndAttributeKeyIn(orgId, leaderKeys)
                .stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a.getAttributeKey()));
        if (attributeKeyById.isEmpty()) {
            return Map.of();
        }

        Map<Long, EmployeeRoleFlagsBuilder> builders = new HashMap<>();
        List<Long> employeeIdList = new ArrayList<>(employeeIds);
        employeeAttributeValueRepository.findByEmployeeIdInOrderByEmployeeIdAscAttributeIdAscValueTextAsc(employeeIdList)
                .forEach(value -> {
                    String key = attributeKeyById.get(value.getAttributeId());
                    if (key == null || !"Y".equalsIgnoreCase(value.getValueText())) {
                        return;
                    }
                    EmployeeRoleFlagsBuilder builder = builders.computeIfAbsent(value.getEmployeeId(), k -> new EmployeeRoleFlagsBuilder());
                    if ("department_head".equals(key)) {
                        builder.departmentHead = true;
                    }
                    if ("institution_head".equals(key) || "unit_head".equals(key) || "department_head".equals(key)) {
                        builder.leader = true;
                    }
                });

        Map<Long, EmployeeRoleFlags> result = new HashMap<>();
        for (Map.Entry<Long, EmployeeRoleFlagsBuilder> entry : builders.entrySet()) {
            result.put(entry.getKey(), new EmployeeRoleFlags(entry.getValue().departmentHead, entry.getValue().leader));
        }
        return result;
    }

    private record EmployeeRoleFlags(boolean departmentHead, boolean leader) {
        private static final EmployeeRoleFlags EMPTY = new EmployeeRoleFlags(false, false);
    }

    private static class EmployeeRoleFlagsBuilder {
        private boolean departmentHead;
        private boolean leader;
    }
}
