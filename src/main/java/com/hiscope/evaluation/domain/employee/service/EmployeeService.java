package com.hiscope.evaluation.domain.employee.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.dto.EmployeeRequest;
import com.hiscope.evaluation.domain.employee.dto.EmployeeResponse;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationSettingService organizationSettingService;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";

    public List<EmployeeResponse> findAll(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        List<Employee> employees = employeeRepository.findByOrganizationIdOrderByNameAsc(orgId);
        Map<Long, String> deptNames = getDepartmentNames(orgId);
        Map<Long, String> loginIds = getLoginIdsByEmployee(orgId);

        return employees.stream()
                .map(e -> EmployeeResponse.from(e,
                        e.getDepartmentId() != null ? deptNames.get(e.getDepartmentId()) : null,
                        loginIds.get(e.getId())))
                .toList();
    }

    public Page<EmployeeResponse> findPage(Long orgId, Pageable pageable) {
        return findPage(orgId, null, null, null, pageable);
    }

    public Page<EmployeeResponse> findPage(Long orgId,
                                           String keyword,
                                           String status,
                                           Long departmentId,
                                           Pageable pageable) {
        SecurityUtils.checkOrgAccess(orgId);
        Specification<Employee> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (StringUtils.hasText(keyword)) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likeKeyword),
                        cb.like(cb.lower(cb.coalesce(root.get("employeeNumber"), "")), likeKeyword),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), likeKeyword)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<Employee> employeePage = employeeRepository.findAll(spec, pageable);
        Map<Long, String> deptNames = getDepartmentNames(orgId);
        Map<Long, String> loginIds = getLoginIdsByEmployee(employeePage.getContent());

        List<EmployeeResponse> content = employeePage.getContent().stream()
                .map(e -> EmployeeResponse.from(
                        e,
                        e.getDepartmentId() != null ? deptNames.get(e.getDepartmentId()) : null,
                        loginIds.get(e.getId())))
                .toList();
        return new PageImpl<>(content, pageable, employeePage.getTotalElements());
    }

    public EmployeeResponse findById(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        Employee emp = getByOrgAndId(orgId, id);
        String loginId = userAccountRepository.findByEmployeeId(id)
                .map(ua -> ua.getLoginId()).orElse(null);
        return EmployeeResponse.from(emp, null, loginId);
    }

    @Transactional
    public EmployeeResponse create(Long orgId, EmployeeRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        if (employeeRepository.existsByOrganizationIdAndEmployeeNumber(orgId, request.getEmployeeNumber())) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NUMBER_DUPLICATE);
        }
        validateLoginIdUnique(orgId, request.getLoginId());
        int minPasswordLength = organizationSettingService.resolvePasswordMinLength(orgId);

        Employee emp = Employee.builder()
                .organizationId(orgId)
                .departmentId(request.getDepartmentId())
                .name(request.getName())
                .employeeNumber(request.getEmployeeNumber())
                .position(request.getPosition())
                .jobTitle(request.getJobTitle())
                .email(request.getEmail())
                .status(request.getStatus())
                .build();
        emp = employeeRepository.save(emp);

        String initPassword = (request.getPassword() == null || request.getPassword().isBlank())
                ? generateInitialPassword(minPasswordLength)
                : request.getPassword();
        validatePasswordPolicy(initPassword, minPasswordLength);
        UserAccount ua = UserAccount.builder()
                .employee(emp)
                .organizationId(orgId)
                .loginId(request.getLoginId())
                .passwordHash(passwordEncoder.encode(initPassword))
                .role("ROLE_USER")
                .build();
        userAccountRepository.save(ua);
        return EmployeeResponse.from(emp, null, request.getLoginId());
    }

    @Transactional
    public EmployeeResponse update(Long orgId, Long id, EmployeeRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        Employee emp = getByOrgAndId(orgId, id);
        int minPasswordLength = organizationSettingService.resolvePasswordMinLength(orgId);
        if (employeeRepository.existsByOrganizationIdAndEmployeeNumberAndIdNot(orgId, request.getEmployeeNumber(), id)) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NUMBER_DUPLICATE);
        }
        emp.update(request.getDepartmentId(), request.getName(), request.getPosition(),
                request.getJobTitle(), request.getEmail(), request.getStatus());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            validatePasswordPolicy(request.getPassword(), minPasswordLength);
            userAccountRepository.findByEmployeeId(id)
                    .ifPresent(ua -> ua.updatePassword(passwordEncoder.encode(request.getPassword())));
        }
        return EmployeeResponse.from(emp, null, request.getLoginId());
    }

    @Transactional
    public void deactivate(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        Employee emp = getByOrgAndId(orgId, id);
        emp.update(emp.getDepartmentId(), emp.getName(), emp.getPosition(),
                emp.getJobTitle(), emp.getEmail(), "INACTIVE");
    }

    @Transactional
    public void activate(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        Employee emp = getByOrgAndId(orgId, id);
        emp.update(emp.getDepartmentId(), emp.getName(), emp.getPosition(),
                emp.getJobTitle(), emp.getEmail(), "ACTIVE");
    }

    public Employee getByOrgAndId(Long orgId, Long id) {
        return employeeRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    private void validateLoginIdUnique(Long orgId, String loginId) {
        if (accountRepository.existsByOrganizationIdAndLoginId(orgId, loginId)
                || userAccountRepository.existsByOrganizationIdAndLoginId(orgId, loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATE);
        }
    }

    private Map<Long, String> getDepartmentNames(Long orgId) {
        return departmentRepository.findByOrganizationIdOrderByNameAsc(orgId)
                .stream()
                .collect(Collectors.toMap(d -> d.getId(), d -> d.getName()));
    }

    private Map<Long, String> getLoginIdsByEmployee(Long orgId) {
        // org-scoped JOIN FETCH:
        // (1) 타 기관 UserAccount 로딩 제거
        // (2) N+1 쿼리 제거
        return userAccountRepository.findByOrganizationIdWithEmployee(orgId).stream()
                .collect(Collectors.toMap(ua -> ua.getEmployee().getId(), UserAccount::getLoginId));
    }

    private Map<Long, String> getLoginIdsByEmployee(List<Employee> employees) {
        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findByEmployeeIdIn(employeeIds).stream()
                .collect(Collectors.toMap(ua -> ua.getEmployee().getId(), UserAccount::getLoginId));
    }

    private void validatePasswordPolicy(String password, int minLength) {
        if (password == null || password.length() < minLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비밀번호는 최소 " + minLength + "자 이상이어야 합니다.");
        }
        if (password.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 50자 이하여야 합니다.");
        }
    }

    private String generateInitialPassword(int minLength) {
        int length = Math.max(minLength, 12);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(PASSWORD_CHARS.length());
            builder.append(PASSWORD_CHARS.charAt(index));
        }
        return builder.toString();
    }
}
