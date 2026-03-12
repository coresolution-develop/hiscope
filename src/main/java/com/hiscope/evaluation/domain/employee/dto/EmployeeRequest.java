package com.hiscope.evaluation.domain.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeRequest {

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "사원번호는 필수입니다.")
    @Size(max = 50)
    private String employeeNumber;

    @NotNull(message = "부서는 필수입니다.")
    private Long departmentId;

    @Size(max = 50)
    private String position;

    @Size(max = 50)
    private String jobTitle;

    @Size(max = 200)
    private String email;

    @NotBlank(message = "로그인 ID는 필수입니다.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$")
    private String loginId;

    @Pattern(regexp = "^$|^.{1,50}$", message = "비밀번호는 50자 이하여야 합니다.")
    private String password; // null이면 비밀번호 유지 (수정 시)

    private String status = "ACTIVE";
}
