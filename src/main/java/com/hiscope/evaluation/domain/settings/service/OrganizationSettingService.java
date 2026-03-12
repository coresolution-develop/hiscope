package com.hiscope.evaluation.domain.settings.service;

import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.config.properties.UploadPolicyProperties;
import com.hiscope.evaluation.domain.settings.dto.AdminSettingsRequest;
import com.hiscope.evaluation.domain.settings.dto.AdminSettingsView;
import com.hiscope.evaluation.domain.settings.entity.OrganizationSetting;
import com.hiscope.evaluation.domain.settings.repository.OrganizationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationSettingService {

    public static final String KEY_UPLOAD_MAX_ROWS = "UPLOAD_MAX_ROWS";
    public static final String KEY_UPLOAD_MAX_FILE_SIZE_BYTES = "UPLOAD_MAX_FILE_SIZE_BYTES";
    public static final String KEY_UPLOAD_ALLOWED_EXTENSIONS = "UPLOAD_ALLOWED_EXTENSIONS";
    public static final String KEY_PASSWORD_MIN_LENGTH = "PASSWORD_MIN_LENGTH";
    public static final String KEY_SESSION_DEFAULT_DURATION_DAYS = "SESSION_DEFAULT_DURATION_DAYS";
    public static final String KEY_SESSION_DEFAULT_ALLOW_RESUBMIT = "SESSION_DEFAULT_ALLOW_RESUBMIT";

    private static final int DEFAULT_SESSION_DURATION_DAYS = 14;
    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;

    private final OrganizationSettingRepository organizationSettingRepository;
    private final UploadPolicyProperties uploadPolicyProperties;

    public AdminSettingsView getAdminSettings(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        Map<String, String> settings = loadSettings(orgId);
        return AdminSettingsView.builder()
                .uploadMaxRows(parseInt(settings.get(KEY_UPLOAD_MAX_ROWS), uploadPolicyProperties.getMaxRows()))
                .uploadMaxFileSizeMb((int) Math.max(1L, parseLong(settings.get(KEY_UPLOAD_MAX_FILE_SIZE_BYTES), uploadPolicyProperties.getMaxFileSizeBytes()) / (1024L * 1024L)))
                .uploadAllowedExtensions(String.join(",", parseExtensions(settings.get(KEY_UPLOAD_ALLOWED_EXTENSIONS)).stream().toList()))
                .passwordMinLength(parseInt(settings.get(KEY_PASSWORD_MIN_LENGTH), DEFAULT_PASSWORD_MIN_LENGTH))
                .sessionDefaultDurationDays(parseInt(settings.get(KEY_SESSION_DEFAULT_DURATION_DAYS), DEFAULT_SESSION_DURATION_DAYS))
                .sessionDefaultAllowResubmit(parseBoolean(settings.get(KEY_SESSION_DEFAULT_ALLOW_RESUBMIT), false))
                .build();
    }

    @Transactional
    public void updateAdminSettings(Long orgId, AdminSettingsRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        int uploadMaxRows = request.getUploadMaxRows() != null
                ? request.getUploadMaxRows()
                : resolveUploadMaxRows(orgId);
        int uploadMaxFileSizeMb = request.getUploadMaxFileSizeMb() != null
                ? request.getUploadMaxFileSizeMb()
                : resolveUploadMaxFileSizeMb(orgId);
        String uploadAllowedExtensions = StringUtils.hasText(request.getUploadAllowedExtensions())
                ? normalizeExtensions(request.getUploadAllowedExtensions())
                : String.join(",", resolveUploadAllowedExtensions(orgId));
        int passwordMinLength = request.getPasswordMinLength() != null
                ? request.getPasswordMinLength()
                : resolvePasswordMinLength(orgId);
        int sessionDefaultDurationDays = request.getSessionDefaultDurationDays() != null
                ? request.getSessionDefaultDurationDays()
                : resolveSessionDefaultDurationDays(orgId);

        upsert(orgId, KEY_UPLOAD_MAX_ROWS, String.valueOf(uploadMaxRows));
        upsert(orgId, KEY_UPLOAD_MAX_FILE_SIZE_BYTES, String.valueOf(uploadMaxFileSizeMb * 1024L * 1024L));
        upsert(orgId, KEY_UPLOAD_ALLOWED_EXTENSIONS, uploadAllowedExtensions);
        upsert(orgId, KEY_PASSWORD_MIN_LENGTH, String.valueOf(passwordMinLength));
        upsert(orgId, KEY_SESSION_DEFAULT_DURATION_DAYS, String.valueOf(sessionDefaultDurationDays));
        upsert(orgId, KEY_SESSION_DEFAULT_ALLOW_RESUBMIT, String.valueOf(request.isSessionDefaultAllowResubmit()));
    }

    public int resolveUploadMaxRows(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, KEY_UPLOAD_MAX_ROWS)
                .map(OrganizationSetting::getSettingValue)
                .map(value -> parseInt(value, uploadPolicyProperties.getMaxRows()))
                .orElse(uploadPolicyProperties.getMaxRows());
    }

    public long resolveUploadMaxFileSizeBytes(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, KEY_UPLOAD_MAX_FILE_SIZE_BYTES)
                .map(OrganizationSetting::getSettingValue)
                .map(value -> parseLong(value, uploadPolicyProperties.getMaxFileSizeBytes()))
                .orElse(uploadPolicyProperties.getMaxFileSizeBytes());
    }

    public int resolveUploadMaxFileSizeMb(Long orgId) {
        return (int) Math.max(1L, resolveUploadMaxFileSizeBytes(orgId) / (1024L * 1024L));
    }

    public Set<String> resolveUploadAllowedExtensions(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, KEY_UPLOAD_ALLOWED_EXTENSIONS)
                .map(OrganizationSetting::getSettingValue)
                .map(this::parseExtensions)
                .orElseGet(() -> uploadPolicyProperties.getAllowedExtensions().stream()
                        .map(ext -> ext == null ? "" : ext.trim().toLowerCase())
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    public int resolvePasswordMinLength(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, KEY_PASSWORD_MIN_LENGTH)
                .map(OrganizationSetting::getSettingValue)
                .map(value -> parseInt(value, DEFAULT_PASSWORD_MIN_LENGTH))
                .orElse(DEFAULT_PASSWORD_MIN_LENGTH);
    }

    public int resolveSessionDefaultDurationDays(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, KEY_SESSION_DEFAULT_DURATION_DAYS)
                .map(OrganizationSetting::getSettingValue)
                .map(value -> parseInt(value, DEFAULT_SESSION_DURATION_DAYS))
                .orElse(DEFAULT_SESSION_DURATION_DAYS);
    }

    public boolean resolveSessionDefaultAllowResubmit(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, KEY_SESSION_DEFAULT_ALLOW_RESUBMIT)
                .map(OrganizationSetting::getSettingValue)
                .map(value -> parseBoolean(value, false))
                .orElse(false);
    }

    private Map<String, String> loadSettings(Long orgId) {
        return organizationSettingRepository.findByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(OrganizationSetting::getSettingKey, OrganizationSetting::getSettingValue, (left, right) -> right));
    }

    private void upsert(Long orgId, String key, String value) {
        organizationSettingRepository.findByOrganizationIdAndSettingKey(orgId, key)
                .ifPresentOrElse(
                        setting -> setting.updateValue(value),
                        () -> organizationSettingRepository.save(OrganizationSetting.builder()
                                .organizationId(orgId)
                                .settingKey(key)
                                .settingValue(value)
                                .build())
                );
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Set<String> parseExtensions(String value) {
        Set<String> supported = uploadPolicyProperties.getAllowedExtensions().stream()
                .map(ext -> ext == null ? "" : ext.trim().toLowerCase())
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!StringUtils.hasText(value)) {
            return supported;
        }
        Set<String> parsed = List.of(value.split(",")).stream()
                .map(ext -> ext == null ? "" : ext.trim().toLowerCase())
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        parsed.removeIf(ext -> !supported.contains(ext));
        return parsed.isEmpty() ? supported : parsed;
    }

    private String normalizeExtensions(String value) {
        return parseExtensions(value).stream().collect(Collectors.joining(","));
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }
}
