package com.hiscope.evaluation.domain.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminSettingsRequest {

    @Min(value = 100, message = "업로드 최대 행수는 100 이상이어야 합니다.")
    @Max(value = 20000, message = "업로드 최대 행수는 20,000 이하여야 합니다.")
    private Integer uploadMaxRows;

    @Min(value = 1, message = "업로드 최대 파일 크기는 1MB 이상이어야 합니다.")
    @Max(value = 100, message = "업로드 최대 파일 크기는 100MB 이하여야 합니다.")
    private Integer uploadMaxFileSizeMb;

    @jakarta.validation.constraints.Pattern(
            regexp = "^[a-zA-Z0-9, ]+$",
            message = "허용 확장자는 영문/숫자와 쉼표만 사용할 수 있습니다."
    )
    private String uploadAllowedExtensions;

    @Min(value = 1, message = "비밀번호 최소 길이는 1 이상이어야 합니다.")
    @Max(value = 50, message = "비밀번호 최소 길이는 50 이하여야 합니다.")
    private Integer passwordMinLength;

    @Min(value = 1, message = "세션 기본 기간은 1일 이상이어야 합니다.")
    @Max(value = 365, message = "세션 기본 기간은 365일 이하여야 합니다.")
    private Integer sessionDefaultDurationDays;

    private boolean sessionDefaultAllowResubmit;
}
