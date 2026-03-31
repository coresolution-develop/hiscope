package com.hiscope.evaluation.domain.evaluation.session.dto;

import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class SessionCreateRequest {

    @NotBlank(message = "세션명은 필수입니다.")
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "평가 시작일은 필수입니다.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "평가 종료일은 필수입니다.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "평가 템플릿은 필수입니다.")
    private Long templateId;

    @NotNull(message = "관계 생성 방식은 필수입니다.")
    private RelationshipGenerationMode relationshipGenerationMode = RelationshipGenerationMode.LEGACY;

    private Long relationshipDefinitionSetId;

    private Boolean allowResubmit = false;
}
