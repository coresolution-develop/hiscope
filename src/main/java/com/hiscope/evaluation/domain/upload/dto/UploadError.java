package com.hiscope.evaluation.domain.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UploadError {
    private final int rowNumber;
    private final String column;
    private final String message;

    public String toDisplayString() {
        return String.format("%d행 [%s]: %s", rowNumber, column, message);
    }
}
