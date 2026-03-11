package com.hiscope.evaluation.domain.upload.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UploadResult {

    private final String uploadType;
    private final String fileName;
    private final int totalRows;
    private final int successRows;
    private final int failRows;
    private final String status; // SUCCESS, PARTIAL, FAILED
    private final List<UploadError> errors;

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public static UploadResult success(String type, String fileName, int total) {
        return UploadResult.builder()
                .uploadType(type).fileName(fileName)
                .totalRows(total).successRows(total).failRows(0)
                .status("SUCCESS").errors(List.of()).build();
    }

    public static UploadResult partial(String type, String fileName, int total,
                                       int success, List<UploadError> errors) {
        return UploadResult.builder()
                .uploadType(type).fileName(fileName)
                .totalRows(total).successRows(success).failRows(errors.size())
                .status("PARTIAL").errors(errors).build();
    }

    public static UploadResult failed(String type, String fileName, List<UploadError> errors) {
        return UploadResult.builder()
                .uploadType(type).fileName(fileName)
                .totalRows(errors.size()).successRows(0).failRows(errors.size())
                .status("FAILED").errors(errors).build();
    }
}
