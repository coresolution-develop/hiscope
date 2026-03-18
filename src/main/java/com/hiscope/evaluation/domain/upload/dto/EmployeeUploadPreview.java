package com.hiscope.evaluation.domain.upload.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EmployeeUploadPreview {

    private final String fileName;
    private final String detectedFileType;
    private final String importProfile;
    private final List<String> mappedColumns;
    private final List<String> missingRequiredColumns;
    private final List<String> plannedTransformations;
    private final List<String> departmentMatchingResults;
    private final boolean uploadable;
}
