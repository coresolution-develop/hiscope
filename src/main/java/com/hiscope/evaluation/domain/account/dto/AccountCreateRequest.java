package com.hiscope.evaluation.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountCreateRequest {

    private Long organizationId;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "로그인 ID는 필수입니다.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "로그인 ID는 4~50자의 영문, 숫자, '.', '_', '-'만 가능합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")
    private String password;

    @Size(max = 200)
    private String email;
}
