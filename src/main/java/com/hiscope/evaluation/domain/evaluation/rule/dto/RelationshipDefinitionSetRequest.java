package com.hiscope.evaluation.domain.evaluation.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelationshipDefinitionSetRequest {

    @NotBlank(message = "세트명은 필수입니다.")
    @Size(max = 200, message = "세트명은 200자 이하여야 합니다.")
    private String name;

    private boolean active = true;

    private boolean defaultSet = false;
}
