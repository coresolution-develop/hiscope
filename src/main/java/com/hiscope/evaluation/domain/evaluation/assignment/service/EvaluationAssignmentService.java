package com.hiscope.evaluation.domain.evaluation.assignment.service;

import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationAssignmentService {

    private final EvaluationAssignmentRepository assignmentRepository;
    private final EvaluationRelationshipRepository relationshipRepository;

    /**
     * 세션 시작 시 활성화된 평가 관계를 기반으로 Assignment 스냅샷 생성.
     * 이후 Relationship 변경은 Assignment에 영향 없음.
     */
    @Transactional
    public void createAssignmentsForSession(EvaluationSession session) {
        List<EvaluationRelationship> activeRelationships =
                relationshipRepository.findActiveBySession(session.getId());

        List<EvaluationAssignment> assignments = activeRelationships.stream()
                .filter(r -> !assignmentRepository.existsBySessionIdAndEvaluatorIdAndEvaluateeId(
                        session.getId(), r.getEvaluatorId(), r.getEvaluateeId()))
                .map(r -> EvaluationAssignment.builder()
                        .sessionId(session.getId())
                        .organizationId(session.getOrganizationId())
                        .relationshipId(r.getId())
                        .evaluatorId(r.getEvaluatorId())
                        .evaluateeId(r.getEvaluateeId())
                        .status("PENDING")
                        .build())
                .toList();

        assignmentRepository.saveAll(assignments);
        log.info("Created {} assignments for session {}", assignments.size(), session.getId());
    }
}
