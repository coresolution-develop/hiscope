package com.hiscope.evaluation.common.audit.service;

import com.hiscope.evaluation.common.audit.entity.AuditLog;
import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLog> search(Long organizationId,
                                 String action,
                                 java.util.List<String> actionIn,
                                 String outcome,
                                 String actorLoginId,
                                 String actorRole,
                                 String ipAddress,
                                 String targetType,
                                 String keyword,
                                 String requestId,
                                 LocalDate fromDate,
                                 LocalDate toDate,
                                 Pageable pageable) {
        return auditLogRepository.findAll(buildSpec(
                organizationId,
                action,
                actionIn,
                outcome,
                actorLoginId,
                actorRole,
                ipAddress,
                targetType,
                keyword,
                requestId,
                fromDate,
                toDate
        ), pageable);
    }

    public List<AuditLog> searchForExport(Long organizationId,
                                          String action,
                                          java.util.List<String> actionIn,
                                          String outcome,
                                          String actorLoginId,
                                          String actorRole,
                                          String ipAddress,
                                          String targetType,
                                          String keyword,
                                          String requestId,
                                          LocalDate fromDate,
                                          LocalDate toDate,
                                          String sortBy,
                                          String sortDir,
                                          int limit) {
        int safeLimit = Math.max(100, Math.min(limit, 10000));
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(0, safeLimit, Sort.by(direction, sortBy));
        return auditLogRepository.findAll(buildSpec(
                organizationId,
                action,
                actionIn,
                outcome,
                actorLoginId,
                actorRole,
                ipAddress,
                targetType,
                keyword,
                requestId,
                fromDate,
                toDate
        ), pageable).getContent();
    }

    private Specification<AuditLog> buildSpec(Long organizationId,
                                              String action,
                                              java.util.List<String> actionIn,
                                              String outcome,
                                              String actorLoginId,
                                              String actorRole,
                                              String ipAddress,
                                              String targetType,
                                              String keyword,
                                              String requestId,
                                              LocalDate fromDate,
                                              LocalDate toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (hasText(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            } else if (actionIn != null && !actionIn.isEmpty()) {
                jakarta.persistence.criteria.CriteriaBuilder.In<String> inClause = cb.in(root.get("action"));
                for (String groupedAction : actionIn) {
                    if (hasText(groupedAction)) {
                        inClause.value(groupedAction);
                    }
                }
                predicates.add(inClause);
            }
            if (hasText(outcome)) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (hasText(actorLoginId)) {
                predicates.add(cb.like(cb.lower(root.get("actorLoginId")), "%" + actorLoginId.toLowerCase() + "%"));
            }
            if (hasText(actorRole)) {
                predicates.add(cb.equal(root.get("actorRole"), actorRole.trim()));
            }
            if (hasText(ipAddress)) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("ipAddress"), "")), "%" + ipAddress.trim().toLowerCase() + "%"));
            }
            if (hasText(targetType)) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (hasText(keyword)) {
                String normalized = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("action"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("actorLoginId"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("actorRole"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("ipAddress"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("targetType"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("targetId"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("detail"), "")), normalized),
                        cb.like(cb.lower(cb.coalesce(root.get("requestId"), "")), normalized)
                ));
            }
            if (hasText(requestId)) {
                predicates.add(cb.equal(root.get("requestId"), requestId));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                LocalDateTime toInclusive = toDate.plusDays(1).atStartOfDay().minusNanos(1);
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toInclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
