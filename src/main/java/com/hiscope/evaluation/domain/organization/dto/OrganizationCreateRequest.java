package com.hiscope.evaluation.domain.organization.dto;

import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationCreateRequest {

    @NotBlank(message = "기관명은 필수입니다.")
    @Size(max = 200, message = "기관명은 200자 이내여야 합니다.")
    private String name;

    @NotBlank(message = "기관 코드는 필수입니다.")
    @Pattern(regexp = "^[A-Z0-9_]{2,20}$", message = "기관 코드는 2~20자의 영문 대문자, 숫자, 밑줄만 가능합니다.")
    private String code;

    private OrganizationType organizationType = OrganizationType.HOSPITAL;
    private OrganizationProfile organizationProfile;
}
