package com.hiscope.evaluation.domain.organization.dto;

import com.hiscope.evaluation.domain.organization.entity.Organization;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class OrganizationResponse {

    private Long id;
    private String name;
    private String code;
    private String status;
    private LocalDateTime createdAt;

    public static OrganizationResponse from(Organization org) {
        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .code(org.getCode())
                .status(org.getStatus())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
