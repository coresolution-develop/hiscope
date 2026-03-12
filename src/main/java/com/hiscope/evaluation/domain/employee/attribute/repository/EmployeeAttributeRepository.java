package com.hiscope.evaluation.domain.employee.attribute.repository;

import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeAttributeRepository extends JpaRepository<EmployeeAttribute, Long> {

    List<EmployeeAttribute> findByOrganizationIdOrderByAttributeNameAsc(Long organizationId);

    Optional<EmployeeAttribute> findByOrganizationIdAndId(Long organizationId, Long id);

    Optional<EmployeeAttribute> findByOrganizationIdAndAttributeKey(Long organizationId, String attributeKey);

    List<EmployeeAttribute> findByOrganizationIdAndAttributeKeyIn(Long organizationId, Collection<String> attributeKeys);

    boolean existsByOrganizationIdAndAttributeKey(Long organizationId, String attributeKey);
}
