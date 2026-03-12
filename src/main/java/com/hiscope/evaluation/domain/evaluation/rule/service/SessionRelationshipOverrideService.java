package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionRelationshipOverride;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.enums.SessionRelationshipOverrideAction;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionRelationshipOverrideRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionRelationshipOverrideService {

    private final SessionRelationshipOverrideRepository overrideRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final RelationshipGenerationService relationshipGenerationService;
    private final LegacyRelationshipMirrorAdapter legacyRelationshipMirrorAdapter;

    @Transactional
    public void addOverride(Long orgId, Long sessionId, Long evaluatorId, Long evaluateeId, SessionRelationshipOverrideAction action, String reason) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = getRuleBasedPendingSession(orgId, sessionId);
        validateEmployeePair(orgId, evaluatorId, evaluateeId);

        overrideRepository.save(SessionRelationshipOverride.builder()
                .sessionId(session.getId())
                .organizationId(orgId)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .action(action)
                .reason(reason)
                .createdBy(SecurityUtils.getCurrentUser().getId())
                .build());

        refreshMirroredRelationships(orgId, sessionId);
    }

    @Transactional
    public void refreshMirroredRelationships(Long orgId, Long sessionId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        if (session.getRelationshipGenerationMode() != RelationshipGenerationMode.RULE_BASED) {
            return;
        }
        var finalRelationships = relationshipGenerationService.resolveFinalRelationships(orgId, sessionId);
        legacyRelationshipMirrorAdapter.mirror(orgId, sessionId, finalRelationships);
    }

    private void validateEmployeePair(Long orgId, Long evaluatorId, Long evaluateeId) {
        if (evaluatorId == null || evaluateeId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "평가자와 피평가자를 모두 선택해주세요.");
        }
        if (evaluatorId.equals(evaluateeId)) {
            throw new BusinessException(ErrorCode.SELF_EVALUATION_NOT_ALLOWED);
        }
        employeeRepository.findByOrganizationIdAndId(orgId, evaluatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND, "평가자를 찾을 수 없습니다."));
        employeeRepository.findByOrganizationIdAndId(orgId, evaluateeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND, "피평가자를 찾을 수 없습니다."));
    }

    private EvaluationSession getRuleBasedPendingSession(Long orgId, Long sessionId) {
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        if (session.getRelationshipGenerationMode() != RelationshipGenerationMode.RULE_BASED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "RULE_BASED 세션에서만 override를 사용할 수 있습니다.");
        }
        if (!session.isPending()) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED, "평가가 시작된 후에는 override를 변경할 수 없습니다.");
        }
        return session;
    }

    private EvaluationSession getByOrgAndId(Long orgId, Long sessionId) {
        return sessionRepository.findByOrganizationIdAndId(orgId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
