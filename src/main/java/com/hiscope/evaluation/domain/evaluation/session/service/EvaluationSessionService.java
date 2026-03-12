package com.hiscope.evaluation.domain.evaluation.session.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.assignment.service.EvaluationAssignmentService;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.session.dto.SessionCreateRequest;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.service.EvaluationTemplateService;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationSessionService {

    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationAssignmentService assignmentService;
    private final EvaluationTemplateService templateService;
    private final EvaluationRelationshipRepository relationshipRepository;
    private final EvaluationAssignmentRepository assignmentRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationResponseItemRepository responseItemRepository;

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
        templateService.getByOrgAndId(orgId, request.getTemplateId());
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
        templateService.getByOrgAndId(orgId, request.getTemplateId());
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        session.update(request.getName(), request.getDescription(),
                request.getStartDate(), request.getEndDate(), request.getTemplateId(), request.isAllowResubmit());
    }

    @Transactional
    public void delete(Long orgId, Long sessionId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = getByOrgAndId(orgId, sessionId);
        if (!session.isPending()) {
            throw new BusinessException(ErrorCode.SESSION_STATUS_INVALID, "대기 상태 세션만 삭제할 수 있습니다.");
        }

        List<Long> assignmentIds = assignmentRepository.findBySessionId(sessionId).stream()
                .map(com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment::getId)
                .toList();
        if (!assignmentIds.isEmpty()) {
            List<Long> responseIds = responseRepository.findByAssignmentIdIn(assignmentIds).stream()
                    .map(com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse::getId)
                    .toList();
            if (!responseIds.isEmpty()) {
                responseItemRepository.deleteByResponseIdIn(responseIds);
            }
            responseRepository.deleteAllById(responseIds);
            assignmentRepository.deleteBySessionId(sessionId);
        }
        relationshipRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
    }

    @Transactional
    public SessionCloneResult cloneSession(Long orgId,
                                           Long sourceSessionId,
                                           boolean copyRelationships,
                                           String cloneName,
                                           LocalDate cloneStartDate,
                                           LocalDate cloneEndDate) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession source = getByOrgAndId(orgId, sourceSessionId);
        templateService.getByOrgAndId(orgId, source.getTemplateId());
        String effectiveName = resolveCloneName(orgId, source.getName(), cloneName);
        LocalDate effectiveStartDate = cloneStartDate != null ? cloneStartDate : source.getStartDate();
        LocalDate effectiveEndDate = cloneEndDate != null ? cloneEndDate : source.getEndDate();
        validateDateRange(effectiveStartDate, effectiveEndDate);

        EvaluationSession cloned = sessionRepository.save(EvaluationSession.builder()
                .organizationId(orgId)
                .name(effectiveName)
                .description(source.getDescription())
                .startDate(effectiveStartDate)
                .endDate(effectiveEndDate)
                .templateId(source.getTemplateId())
                .allowResubmit(source.isAllowResubmit())
                .createdBy(SecurityUtils.getCurrentUser().getId())
                .status("PENDING")
                .build());

        int copiedRelationshipCount = 0;
        if (copyRelationships) {
            var sourceRelationships = relationshipRepository.findBySessionIdAndActiveOrderByRelationTypeAsc(sourceSessionId, true);
            if (!sourceRelationships.isEmpty()) {
                var clonedRelationships = sourceRelationships.stream()
                        .map(rel -> com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship.builder()
                                .sessionId(cloned.getId())
                                .organizationId(orgId)
                                .evaluatorId(rel.getEvaluatorId())
                                .evaluateeId(rel.getEvaluateeId())
                                .relationType(rel.getRelationType())
                                .source(rel.getSource())
                                .active(true)
                                .build())
                        .toList();
                relationshipRepository.saveAll(clonedRelationships);
                copiedRelationshipCount = clonedRelationships.size();
            }
        }
        return new SessionCloneResult(cloned, copiedRelationshipCount);
    }

    private String resolveCloneName(Long orgId, String sourceName, String cloneName) {
        if (!StringUtils.hasText(cloneName)) {
            return resolveAutoCloneName(orgId, sourceName);
        }
        String normalized = cloneName.trim();
        if (normalized.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "복제 세션명은 200자 이하여야 합니다.");
        }
        if (sessionRepository.existsByOrganizationIdAndName(orgId, normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동일한 세션명이 이미 존재합니다. 다른 복제 세션명을 입력해주세요.");
        }
        return normalized;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "복제 세션의 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private String buildCloneName(String sourceName) {
        String suffix = " (복제 " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ")";
        String base = sourceName == null ? "세션" : sourceName.trim();
        int maxBaseLen = Math.max(1, 200 - suffix.length());
        if (base.length() > maxBaseLen) {
            base = base.substring(0, maxBaseLen);
        }
        return base + suffix;
    }

    private String resolveAutoCloneName(Long orgId, String sourceName) {
        String baseName = buildCloneName(sourceName);
        if (!sessionRepository.existsByOrganizationIdAndName(orgId, baseName)) {
            return baseName;
        }
        for (int sequence = 2; sequence <= 999; sequence++) {
            String candidate = appendSequence(baseName, sequence);
            if (!sessionRepository.existsByOrganizationIdAndName(orgId, candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "복제 세션명을 자동 생성할 수 없습니다. 복제 세션명을 직접 입력해주세요.");
    }

    private String appendSequence(String baseName, int sequence) {
        String suffix = " #" + sequence;
        if (baseName.length() + suffix.length() <= 200) {
            return baseName + suffix;
        }
        int cutLength = Math.max(1, 200 - suffix.length());
        return baseName.substring(0, cutLength) + suffix;
    }

    public EvaluationSession getByOrgAndId(Long orgId, Long id) {
        return sessionRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    public record SessionCountSummary(long total, long pending, long inProgress, long closed) {
    }

    public record SessionCloneResult(EvaluationSession session, int copiedRelationshipCount) {
    }
}
