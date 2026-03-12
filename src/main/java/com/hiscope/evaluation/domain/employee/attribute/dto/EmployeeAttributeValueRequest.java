package com.hiscope.evaluation.domain.employee.attribute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeAttributeValueRequest {

    @NotNull(message = "직원을 선택해주세요.")
    private Long employeeId;

    @NotNull(message = "속성을 선택해주세요.")
    private Long attributeId;

    @NotBlank(message = "속성 값은 필수입니다.")
    @Size(max = 300, message = "속성 값은 300자 이하여야 합니다.")
    private String valueText;
}
