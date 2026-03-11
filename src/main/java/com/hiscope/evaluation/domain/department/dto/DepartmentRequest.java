package com.hiscope.evaluation.domain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentRequest {

    @NotBlank(message = "부서명은 필수입니다.")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "부서 코드는 필수입니다.")
    @Pattern(regexp = "^[A-Z0-9_]{2,20}$", message = "부서 코드는 2~20자의 영문 대문자, 숫자, 밑줄만 가능합니다.")
    private String code;

    private Long parentId;
    private boolean active = true;
}
