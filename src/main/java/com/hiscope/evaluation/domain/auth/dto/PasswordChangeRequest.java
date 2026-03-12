package com.hiscope.evaluation.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeRequest {

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(max = 50, message = "비밀번호는 50자 이하여야 합니다.")
    private String newPassword;

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    @Size(max = 50, message = "비밀번호 확인은 50자 이하여야 합니다.")
    private String confirmPassword;
}
