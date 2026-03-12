package com.hiscope.evaluation.domain.employee.attribute.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.employee.attribute.dto.EmployeeAttributeRequest;
import com.hiscope.evaluation.domain.employee.attribute.dto.EmployeeAttributeValueRequest;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeAttributeAdminService {

    private final EmployeeAttributeRepository attributeRepository;
    private final EmployeeAttributeValueRepository attributeValueRepository;
    private final EmployeeRepository employeeRepository;

    public List<EmployeeAttribute> findAttributes(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return attributeRepository.findByOrganizationIdOrderByAttributeNameAsc(orgId);
    }

    public List<EmployeeAttributeValue> findEmployeeAttributeValues(Long employeeId) {
        return attributeValueRepository.findByEmployeeIdOrderByAttributeIdAscValueTextAsc(employeeId);
    }

    @Transactional
    public EmployeeAttribute createAttribute(Long orgId, EmployeeAttributeRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        String key = request.getAttributeKey().trim();
        if (attributeRepository.existsByOrganizationIdAndAttributeKey(orgId, key)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 기관에 동일한 속성 키가 이미 존재합니다.");
        }
        return attributeRepository.save(EmployeeAttribute.builder()
                .organizationId(orgId)
                .attributeKey(key)
                .attributeName(request.getAttributeName().trim())
                .active(request.isActive())
                .build());
    }

    @Transactional
    public EmployeeAttribute updateAttribute(Long orgId, Long attributeId, EmployeeAttributeRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        EmployeeAttribute attribute = attributeRepository.findByOrganizationIdAndId(orgId, attributeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원 속성을 찾을 수 없습니다."));
        String key = request.getAttributeKey().trim();
        if (!attribute.getAttributeKey().equals(key)
                && attributeRepository.existsByOrganizationIdAndAttributeKey(orgId, key)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 기관에 동일한 속성 키가 이미 존재합니다.");
        }

        if (!attribute.getAttributeKey().equals(key)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "v1에서는 속성 키 변경을 지원하지 않습니다.");
        }

        attribute.rename(request.getAttributeName().trim());
        if (request.isActive()) {
            attribute.activate();
        } else {
            attribute.deactivate();
        }
        return attribute;
    }

    @Transactional
    public EmployeeAttributeValue upsertAttributeValue(Long orgId, EmployeeAttributeValueRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        employeeRepository.findByOrganizationIdAndId(orgId, request.getEmployeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
        attributeRepository.findByOrganizationIdAndId(orgId, request.getAttributeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원 속성을 찾을 수 없습니다."));

        String normalized = request.getValueText().trim();
        var existing = attributeValueRepository.findByEmployeeIdAndAttributeId(request.getEmployeeId(), request.getAttributeId());
        if (existing.isPresent()) {
            EmployeeAttributeValue value = existing.get();
            value.updateValue(normalized);
            return value;
        }
        return attributeValueRepository.save(EmployeeAttributeValue.builder()
                .employeeId(request.getEmployeeId())
                .attributeId(request.getAttributeId())
                .valueText(normalized)
                .build());
    }

    @Transactional
    public void deleteAttributeValue(Long orgId, Long valueId) {
        SecurityUtils.checkOrgAccess(orgId);
        EmployeeAttributeValue value = attributeValueRepository.findById(valueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원 속성 값을 찾을 수 없습니다."));
        employeeRepository.findByOrganizationIdAndId(orgId, value.getEmployeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "다른 기관의 데이터에는 접근할 수 없습니다."));
        attributeValueRepository.delete(value);
    }
}
