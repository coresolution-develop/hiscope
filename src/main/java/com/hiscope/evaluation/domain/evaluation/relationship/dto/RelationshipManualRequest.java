package com.hiscope.evaluation.domain.evaluation.relationship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelationshipManualRequest {

    @NotNull(message = "평가자는 필수입니다.")
    private Long evaluatorId;

    @NotNull(message = "피평가자는 필수입니다.")
    private Long evaluateeId;

    private String relationType = "MANUAL";
}
