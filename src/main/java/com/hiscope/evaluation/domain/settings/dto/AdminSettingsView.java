package com.hiscope.evaluation.domain.settings.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminSettingsView {

    private int uploadMaxRows;
    private int uploadMaxFileSizeMb;
    private String uploadAllowedExtensions;
    private int passwordMinLength;
    private int sessionDefaultDurationDays;
    private boolean sessionDefaultAllowResubmit;
}
