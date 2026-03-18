package com.hiscope.evaluation.domain.organization.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.organization.dto.OrganizationCreateRequest;
import com.hiscope.evaluation.domain.organization.dto.OrganizationResponse;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
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
    private final OrganizationProfileBootstrapService organizationProfileBootstrapService;

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
        OrganizationType organizationType = request.getOrganizationType() == null
                ? OrganizationType.HOSPITAL
                : request.getOrganizationType();
        OrganizationProfile organizationProfile = resolveOrganizationProfile(organizationType, request.getOrganizationProfile());
        Organization org = Organization.builder()
                .name(request.getName())
                .code(request.getCode())
                .status("ACTIVE")
                .organizationType(organizationType)
                .organizationProfile(organizationProfile)
                .build();
        Organization saved = organizationRepository.save(org);
        organizationProfileBootstrapService.bootstrap(saved.getId(), saved.getOrganizationType(), saved.getOrganizationProfile());
        return OrganizationResponse.from(saved);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        Organization org = getOrganization(id);
        org.update(org.getName(), status, org.getOrganizationType(), org.getOrganizationProfile());
    }

    @Transactional
    public void update(Long id, String name, String status, OrganizationType organizationType, OrganizationProfile organizationProfile) {
        Organization org = getOrganization(id);
        OrganizationType effectiveType = organizationType == null ? org.getOrganizationType() : organizationType;
        OrganizationProfile effectiveProfile = resolveOrganizationProfile(effectiveType, organizationProfile);
        org.update(name, status, effectiveType, effectiveProfile);
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

    private OrganizationProfile resolveOrganizationProfile(OrganizationType organizationType,
                                                           OrganizationProfile requestedProfile) {
        OrganizationProfile defaultProfile = organizationType == OrganizationType.HOSPITAL
                ? OrganizationProfile.HOSPITAL_DEFAULT
                : OrganizationProfile.AFFILIATE_GENERAL;
        OrganizationProfile profile = requestedProfile == null ? defaultProfile : requestedProfile;
        boolean valid = switch (organizationType) {
            case HOSPITAL -> profile == OrganizationProfile.HOSPITAL_DEFAULT;
            case AFFILIATE -> profile == OrganizationProfile.AFFILIATE_HOSPITAL
                    || profile == OrganizationProfile.AFFILIATE_GENERAL;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조직 유형과 운영 프로파일 조합이 올바르지 않습니다.");
        }
        return profile;
    }
}
