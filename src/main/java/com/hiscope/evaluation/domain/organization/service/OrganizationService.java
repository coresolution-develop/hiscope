package com.hiscope.evaluation.domain.organization.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.organization.dto.OrganizationCreateRequest;
import com.hiscope.evaluation.domain.organization.dto.OrganizationResponse;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
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
        return findAll(null, null);
    }

    public List<OrganizationResponse> findAll(String keyword, String status) {
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(status)) {
            return organizationRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(OrganizationResponse::from)
                    .toList();
        }
        return organizationRepository.findAllByOrderByCreatedAtDesc()
                .stream().filter(org -> {
                    boolean statusMatch = !StringUtils.hasText(status) || status.equals(org.getStatus());
                    boolean keywordMatch = !StringUtils.hasText(keyword)
                            || org.getName().toLowerCase().contains(keyword.trim().toLowerCase())
                            || org.getCode().toLowerCase().contains(keyword.trim().toLowerCase());
                    return statusMatch && keywordMatch;
                })
                .map(OrganizationResponse::from)
                .toList();
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
}
