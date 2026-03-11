package com.hiscope.evaluation.common.audit.service;

import com.hiscope.evaluation.common.audit.entity.AuditLog;
import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
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
                                 String outcome,
                                 String actorLoginId,
                                 String requestId,
                                 LocalDate fromDate,
                                 LocalDate toDate,
                                 Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (hasText(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (hasText(outcome)) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (hasText(actorLoginId)) {
                predicates.add(cb.like(cb.lower(root.get("actorLoginId")), "%" + actorLoginId.toLowerCase() + "%"));
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
        return auditLogRepository.findAll(spec, pageable);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
