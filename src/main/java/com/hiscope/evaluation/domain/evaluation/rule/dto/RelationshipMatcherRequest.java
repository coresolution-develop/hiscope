package com.hiscope.evaluation.domain.evaluation.rule.dto;

import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelationshipMatcherRequest {

    @NotNull(message = "subject_type은 필수입니다.")
    private RelationshipSubjectType subjectType;

    @NotNull(message = "matcher_type은 필수입니다.")
    private RelationshipMatcherType matcherType;

    @NotNull(message = "operator는 필수입니다.")
    private RelationshipRuleOperator operator;

    @NotBlank(message = "매칭 값은 필수입니다.")
    @Size(max = 300, message = "매칭 값은 300자 이하여야 합니다.")
    private String valueText;
}
