package com.hiscope.evaluation.domain.evaluation.rule.dto;

import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelationshipDefinitionRuleRequest {

    @NotBlank(message = "룰명은 필수입니다.")
    @Size(max = 200, message = "룰명은 200자 이하여야 합니다.")
    private String ruleName;

    @NotNull(message = "relation_type은 필수입니다.")
    private RelationshipRuleType relationType;

    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다.")
    @Max(value = 10000, message = "우선순위는 10000 이하여야 합니다.")
    private int priority = 100;

    private boolean active = true;
}
