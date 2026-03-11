package com.hiscope.evaluation.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUpdateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100)
    private String name;

    @Size(max = 200)
    private String email;
}
