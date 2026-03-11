package com.hiscope.evaluation.domain.employee.dto;

import com.hiscope.evaluation.domain.employee.entity.Employee;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmployeeResponse {

    private Long id;
    private Long organizationId;
    private Long departmentId;
    private String departmentName;
    private String name;
    private String employeeNumber;
    private String position;
    private String jobTitle;
    private String email;
    private String status;
    private String loginId;

    public static EmployeeResponse from(Employee emp) {
        return EmployeeResponse.builder()
                .id(emp.getId())
                .organizationId(emp.getOrganizationId())
                .departmentId(emp.getDepartmentId())
                .name(emp.getName())
                .employeeNumber(emp.getEmployeeNumber())
                .position(emp.getPosition())
                .jobTitle(emp.getJobTitle())
                .email(emp.getEmail())
                .status(emp.getStatus())
                .build();
    }

    public static EmployeeResponse from(Employee emp, String departmentName, String loginId) {
        return EmployeeResponse.builder()
                .id(emp.getId())
                .organizationId(emp.getOrganizationId())
                .departmentId(emp.getDepartmentId())
                .departmentName(departmentName)
                .name(emp.getName())
                .employeeNumber(emp.getEmployeeNumber())
                .position(emp.getPosition())
                .jobTitle(emp.getJobTitle())
                .email(emp.getEmail())
                .status(emp.getStatus())
                .loginId(loginId)
                .build();
    }
}
