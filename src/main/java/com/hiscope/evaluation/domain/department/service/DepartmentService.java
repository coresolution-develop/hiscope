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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        return findAll(orgId, null, null);
    }

    public List<DepartmentResponse> findAll(Long orgId, String keyword, Boolean active) {
        SecurityUtils.checkOrgAccess(orgId);
        List<Department> depts = departmentRepository.findByOrganizationIdOrderByNameAsc(orgId);
        Map<Long, String> nameMap = depts.stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return depts.stream()
                .filter(d -> {
                    if (active == null) {
                        return true;
                    }
                    return d.isActive() == active;
                })
                .filter(d -> {
                    if (!StringUtils.hasText(keyword)) {
                        return true;
                    }
                    String normalized = keyword.trim().toLowerCase();
                    return d.getName().toLowerCase().contains(normalized)
                            || d.getCode().toLowerCase().contains(normalized);
                })
                .map(d -> DepartmentResponse.from(d,
                        d.getParentId() != null ? nameMap.get(d.getParentId()) : null))
                .toList();
    }

    public Page<DepartmentResponse> searchPage(Long orgId, String keyword, Boolean active, Pageable pageable) {
        SecurityUtils.checkOrgAccess(orgId);
        String normalizedKeyword = normalizeKeyword(keyword);
        Specification<Department> spec = Specification.where((root, query, cb) -> cb.equal(root.get("organizationId"), orgId));
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (StringUtils.hasText(normalizedKeyword)) {
            String likeKeyword = "%" + normalizedKeyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), likeKeyword),
                    cb.like(cb.lower(root.get("code")), likeKeyword)
            ));
        }
        Map<Long, String> nameMap = departmentRepository.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return departmentRepository.findAll(spec, pageable)
                .map(d -> DepartmentResponse.from(d, d.getParentId() != null ? nameMap.get(d.getParentId()) : null));
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
                .parentId(resolveParentId(orgId, request.getParentId(), null))
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
        dept.update(request.getName(), resolveParentId(orgId, request.getParentId(), id), request.isActive());
        return DepartmentResponse.from(dept);
    }

    @Transactional
    public void delete(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        Department dept = getByOrgAndId(orgId, id);
        if (departmentRepository.existsByOrganizationIdAndParentId(orgId, id)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_CHILDREN,
                    "하위 부서가 존재합니다. 하위 부서를 먼저 정리한 후 비활성화해주세요.");
        }
        if (employeeRepository.existsByOrganizationIdAndDepartmentId(orgId, id)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_EMPLOYEES);
        }
        dept.deactivate();
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

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private Long resolveParentId(Long orgId, Long parentId, Long selfId) {
        if (parentId == null) {
            return null;
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신을 상위 부서로 지정할 수 없습니다.");
        }
        Department parent = getByOrgAndId(orgId, parentId);
        if (!parent.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비활성 부서는 상위 부서로 지정할 수 없습니다.");
        }
        return parent.getId();
    }
}
