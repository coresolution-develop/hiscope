package com.hiscope.evaluation.domain.employee.repository;

import com.hiscope.evaluation.domain.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    List<Employee> findByOrganizationIdOrderByNameAsc(Long organizationId);
    Page<Employee> findByOrganizationIdOrderByNameAsc(Long organizationId, Pageable pageable);

    List<Employee> findByOrganizationIdAndStatusOrderByNameAsc(Long organizationId, String status);

    List<Employee> findByOrganizationIdAndDepartmentIdOrderByNameAsc(Long organizationId, Long departmentId);

    Optional<Employee> findByOrganizationIdAndId(Long organizationId, Long id);

    boolean existsByOrganizationIdAndEmployeeNumber(Long organizationId, String employeeNumber);

    boolean existsByOrganizationIdAndEmployeeNumberAndIdNot(Long organizationId, String employeeNumber, Long id);

    boolean existsByDepartmentId(Long departmentId);

    boolean existsByOrganizationIdAndDepartmentId(Long organizationId, Long departmentId);

    @Query("SELECT e FROM Employee e WHERE e.organizationId = :orgId AND e.departmentId = :deptId AND e.status = 'ACTIVE'")
    List<Employee> findActivByOrgAndDept(@Param("orgId") Long orgId, @Param("deptId") Long deptId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.organizationId = :orgId AND e.status = 'ACTIVE'")
    long countActiveByOrg(@Param("orgId") Long orgId);

    @Query("""
            SELECT e.id
            FROM Employee e
            WHERE e.organizationId = :orgId
              AND (
                LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(e.employeeNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    List<Long> findIdsByOrganizationIdAndKeyword(@Param("orgId") Long orgId, @Param("keyword") String keyword);

    @Query("SELECT DISTINCT e.position FROM Employee e WHERE e.organizationId = :orgId AND e.status = 'ACTIVE' AND e.position IS NOT NULL ORDER BY e.position ASC")
    List<String> findDistinctPositionsByOrganizationId(@Param("orgId") Long orgId);

    @Query("SELECT e FROM Employee e WHERE e.organizationId = :orgId AND e.departmentId IN :deptIds AND e.status = 'ACTIVE' ORDER BY e.name ASC")
    List<Employee> findActiveByOrganizationIdAndDepartmentIdIn(@Param("orgId") Long orgId, @Param("deptIds") java.util.Collection<Long> deptIds);
}
