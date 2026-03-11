package com.hiscope.evaluation.domain.department.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.dto.DepartmentRequest;
import com.hiscope.evaluation.domain.department.dto.DepartmentResponse;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public List<DepartmentResponse> findAll(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        List<Department> depts = departmentRepository.findByOrganizationIdOrderByNameAsc(orgId);
        Map<Long, String> nameMap = depts.stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return depts.stream()
                .map(d -> DepartmentResponse.from(d,
                        d.getParentId() != null ? nameMap.get(d.getParentId()) : null))
                .toList();
    }

    public List<DepartmentResponse> findActive(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return departmentRepository.findByOrganizationIdAndActiveOrderByNameAsc(orgId, true)
                .stream().map(DepartmentResponse::from).toList();
    }

    public DepartmentResponse findById(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        Department dept = getByOrgAndId(orgId, id);
        return DepartmentResponse.from(dept);
    }

    @Transactional
    public DepartmentResponse create(Long orgId, DepartmentRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        if (departmentRepository.existsByOrganizationIdAndCode(orgId, request.getCode())) {
            throw new BusinessException(ErrorCode.DEPARTMENT_CODE_DUPLICATE);
        }
        Department dept = Department.builder()
                .organizationId(orgId)
                .parentId(request.getParentId())
                .name(request.getName())
                .code(request.getCode())
                .active(true)
                .build();
        return DepartmentResponse.from(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentResponse update(Long orgId, Long id, DepartmentRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        Department dept = getByOrgAndId(orgId, id);
        if (departmentRepository.existsByOrganizationIdAndCodeAndIdNot(orgId, request.getCode(), id)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_CODE_DUPLICATE);
        }
        dept.update(request.getName(), request.getParentId(), request.isActive());
        return DepartmentResponse.from(dept);
    }

    @Transactional
    public void delete(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        Department dept = getByOrgAndId(orgId, id);
        if (employeeRepository.existsByDepartmentId(id)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_EMPLOYEES);
        }
        departmentRepository.delete(dept);
    }

    public Department getByOrgAndId(Long orgId, Long id) {
        return departmentRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    public Department getByCode(Long orgId, String code) {
        // 전체 부서 로드 후 메모리 필터 → DB 레벨 정확한 쿼리로 교체
        // orElse(null) → orElseThrow() : 다른 getBy* 메서드와 일관성 유지 및 NPE 방지
        return departmentRepository.findByOrganizationIdAndCode(orgId, code)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }
}
