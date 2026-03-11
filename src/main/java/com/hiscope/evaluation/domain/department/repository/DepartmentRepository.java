package com.hiscope.evaluation.domain.department.repository;

import com.hiscope.evaluation.domain.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByOrganizationIdAndActiveOrderByNameAsc(Long organizationId, boolean active);

    List<Department> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Department> findByOrganizationIdAndId(Long organizationId, Long id);

    boolean existsByOrganizationIdAndCode(Long organizationId, String code);

    boolean existsByOrganizationIdAndCodeAndIdNot(Long organizationId, String code, Long id);

    /** DepartmentService.getByCode() 전용 — 메모리 필터 대신 DB 쿼리로 교체 */
    Optional<Department> findByOrganizationIdAndCode(Long organizationId, String code);
}
