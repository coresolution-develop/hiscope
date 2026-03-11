package com.hiscope.evaluation.domain.evaluation.template.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionRequest {

    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;

    @NotBlank(message = "문항 내용은 필수입니다.")
    private String content;

    @NotBlank
    @Pattern(regexp = "^(SCALE|DESCRIPTIVE)$", message = "유형은 SCALE 또는 DESCRIPTIVE만 가능합니다.")
    private String questionType;

    private Integer maxScore;

    @Min(value = 0)
    private int sortOrder = 0;
}
