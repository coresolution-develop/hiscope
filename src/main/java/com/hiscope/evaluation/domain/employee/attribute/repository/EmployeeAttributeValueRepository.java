package com.hiscope.evaluation.domain.employee.attribute.repository;

import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeAttributeValueRepository extends JpaRepository<EmployeeAttributeValue, Long> {

    List<EmployeeAttributeValue> findByEmployeeIdOrderByAttributeIdAscValueTextAsc(Long employeeId);

    List<EmployeeAttributeValue> findByAttributeIdOrderByEmployeeIdAscValueTextAsc(Long attributeId);

    List<EmployeeAttributeValue> findByEmployeeIdInOrderByEmployeeIdAscAttributeIdAscValueTextAsc(List<Long> employeeIds);

    Optional<EmployeeAttributeValue> findByEmployeeIdAndAttributeId(Long employeeId, Long attributeId);
}
