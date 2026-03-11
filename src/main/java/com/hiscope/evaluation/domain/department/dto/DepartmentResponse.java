package com.hiscope.evaluation.domain.department.dto;

import com.hiscope.evaluation.domain.department.entity.Department;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentResponse {

    private Long id;
    private Long organizationId;
    private Long parentId;
    private String parentName;
    private String name;
    private String code;
    private boolean active;

    public static DepartmentResponse from(Department dept) {
        return DepartmentResponse.builder()
                .id(dept.getId())
                .organizationId(dept.getOrganizationId())
                .parentId(dept.getParentId())
                .name(dept.getName())
                .code(dept.getCode())
                .active(dept.isActive())
                .build();
    }

    public static DepartmentResponse from(Department dept, String parentName) {
        return DepartmentResponse.builder()
                .id(dept.getId())
                .organizationId(dept.getOrganizationId())
                .parentId(dept.getParentId())
                .parentName(parentName)
                .name(dept.getName())
                .code(dept.getCode())
                .active(dept.isActive())
                .build();
    }
}
