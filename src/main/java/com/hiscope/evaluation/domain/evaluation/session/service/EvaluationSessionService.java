package com.hiscope.evaluation.domain.evaluation.session.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.assignment.service.EvaluationAssignmentService;
import com.hiscope.evaluation.domain.evaluation.session.dto.SessionCreateRequest;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationSessionService {

    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationAssignmentService assignmentService;

    public List<EvaluationSession> findAll(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    public Page<EvaluationSession> findPage(Long orgId, Pageable pageable) {
        return findPage(orgId, null, null, null, pageable);
    }

    public Page<EvaluationSession> findPage(Long orgId,
                                            String keyword,
                                            String status,
                                            Boolean allowResubmit,
                                            Pageable pageable) {
        SecurityUtils.checkOrgAccess(orgId);
        Specification<EvaluationSession> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (allowResubmit != null) {
                predicates.add(cb.equal(root.get("allowResubmit"), allowResubmit));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return sessionRepository.findAll(spec, pageable);
    }

    public SessionCountSummary countSummary(Long orgId) {
        return countSummary(orgId, null, null, null);
    }

    public SessionCountSummary countSummary(Long orgId,
                                            String keyword,
                                            String status,
                                            Boolean allowResubmit) {
        SecurityUtils.checkOrgAccess(orgId);
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(status) && allowResubmit == null) {
            long total = sessionRepository.countByOrganizationId(orgId);
            long pending = sessionRepository.countByOrganizationIdAndStatus(orgId, "PENDING");
            long inProgress = sessionRepository.countByOrganizationIdAndStatus(orgId, "IN_PROGRESS");
            long closed = sessionRepository.countByOrganizationIdAndStatus(orgId, "CLOSED");
            return new SessionCountSummary(total, pending, inProgress, closed);
        }
        long total = sessionRepository.count(buildSpec(orgId, keyword, status, allowResubmit));
        long pending = sessionRepository.count(buildSpec(orgId, keyword, "PENDING", allowResubmit));
        long inProgress = sessionRepository.count(buildSpec(orgId, keyword, "IN_PROGRESS", allowResubmit));
        long closed = sessionRepository.count(buildSpec(orgId, keyword, "CLOSED", allowResubmit));
        return new SessionCountSummary(total, pending, inProgress, closed);
    }

    private Specification<EvaluationSession> buildSpec(Long orgId,
                                                       String keyword,
                                                       String effectiveStatus,
                                                       Boolean allowResubmit) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(effectiveStatus)) {
                predicates.add(cb.equal(root.get("status"), effectiveStatus));
            }
            if (allowResubmit != null) {
                predicates.add(cb.equal(root.get("allowResubmit"), allowResubmit));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public EvaluationSession findById(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        return getByOrgAndId(orgId, id);
    }

    @Transactional
    public EvaluationSession create(Long orgId, SessionCreateRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = EvaluationSession.builder()
                .organizationId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .templateId(request.getTemplateId())
                .allowResubmit(request.isAllowResubmit())
                .createdBy(SecurityUtils.getCurrentUser().getId())
                .status("PENDING")
                .build();
        return sessionRepository.save(session);
    }

    /**
     * PENDING → IN_PROGRESS
     * 활성화된 EvaluationRelationship을 기반으로 Assignment 스냅샷 생성
     */
    @Transactional
    public void start(Long orgId, Long sessionId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        session.start();
        // Assignment 스냅샷 생성
        assignmentService.createAssignmentsForSession(session);
    }

    /** IN_PROGRESS → CLOSED */
    @Transactional
    public void close(Long orgId, Long sessionId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        session.close();
    }

    @Transactional
    public void update(Long orgId, Long sessionId, SessionCreateRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        session.update(request.getName(), request.getDescription(),
                request.getStartDate(), request.getEndDate(), request.isAllowResubmit());
    }

    public EvaluationSession getByOrgAndId(Long orgId, Long id) {
        return sessionRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    public record SessionCountSummary(long total, long pending, long inProgress, long closed) {
    }
}
