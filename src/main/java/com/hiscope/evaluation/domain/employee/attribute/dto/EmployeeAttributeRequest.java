package com.hiscope.evaluation.domain.employee.attribute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeAttributeRequest {

    @NotBlank(message = "속성 키는 필수입니다.")
    @Size(max = 100, message = "속성 키는 100자 이하여야 합니다.")
    @Pattern(regexp = "[a-zA-Z0-9_\\-]+", message = "속성 키는 영문/숫자/_/-만 사용할 수 있습니다.")
    private String attributeKey;

    @NotBlank(message = "속성명은 필수입니다.")
    @Size(max = 200, message = "속성명은 200자 이하여야 합니다.")
    private String attributeName;

    private boolean active = true;
}
