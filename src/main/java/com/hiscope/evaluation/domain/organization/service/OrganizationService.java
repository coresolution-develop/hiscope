package com.hiscope.evaluation.domain.organization.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.organization.dto.OrganizationCreateRequest;
import com.hiscope.evaluation.domain.organization.dto.OrganizationResponse;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
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
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public List<OrganizationResponse> findAll() {
        return search(null, null, Pageable.unpaged()).getContent();
    }

    public List<OrganizationResponse> findAll(String keyword, String status) {
        return search(keyword, status, Pageable.unpaged()).getContent();
    }

    public Page<OrganizationResponse> search(String keyword, String status, Pageable pageable) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Specification<Organization> spec = Specification.where(null);
        if (StringUtils.hasText(normalizedKeyword)) {
            String likeKeyword = "%" + normalizedKeyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), likeKeyword),
                    cb.like(cb.lower(root.get("code")), likeKeyword)
            ));
        }
        if (StringUtils.hasText(normalizedStatus)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), normalizedStatus));
        }
        return organizationRepository.findAll(spec, pageable)
                .map(OrganizationResponse::from);
    }

    public OrganizationResponse findById(Long id) {
        return OrganizationResponse.from(getOrganization(id));
    }

    @Transactional
    public OrganizationResponse create(OrganizationCreateRequest request) {
        if (organizationRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ErrorCode.ORGANIZATION_CODE_DUPLICATE);
        }
        Organization org = Organization.builder()
                .name(request.getName())
                .code(request.getCode())
                .status("ACTIVE")
                .build();
        return OrganizationResponse.from(organizationRepository.save(org));
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        Organization org = getOrganization(id);
        org.update(org.getName(), status);
    }

    @Transactional
    public void update(Long id, String name, String status) {
        Organization org = getOrganization(id);
        org.update(name, status);
    }

    public Organization getOrganization(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeStatus(String status) {
        if ("ACTIVE".equals(status) || "INACTIVE".equals(status)) {
            return status;
        }
        return null;
    }
}
