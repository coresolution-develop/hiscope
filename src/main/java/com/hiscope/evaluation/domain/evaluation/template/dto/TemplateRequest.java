package com.hiscope.evaluation.domain.evaluation.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TemplateRequest {

    @NotBlank(message = "템플릿명은 필수입니다.")
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;
}
