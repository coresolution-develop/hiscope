package com.hiscope.evaluation.domain.upload.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UploadValidationService {

    private final OrganizationSettingService organizationSettingService;

    public void validateExcelFile(MultipartFile file) {
        Long orgId = SecurityUtils.getCurrentOrgId();
        long maxFileSizeBytes = organizationSettingService.resolveUploadMaxFileSizeBytes(orgId);
        var allowedExtensions = organizationSettingService.resolveUploadAllowedExtensions(orgId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드 파일이 비어 있습니다.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "파일 크기 제한(" + (maxFileSizeBytes / (1024 * 1024)) + "MB)을 초과했습니다.");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.contains(".")) {
            throw new BusinessException(ErrorCode.EXCEL_INVALID_FORMAT);
        }

        String extension = originalFileName.substring(originalFileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.EXCEL_INVALID_FORMAT);
        }
    }
}
