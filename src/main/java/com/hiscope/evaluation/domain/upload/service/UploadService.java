package com.hiscope.evaluation.domain.upload.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.common.util.CsvUtils;
import com.hiscope.evaluation.domain.upload.dto.UploadError;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import com.hiscope.evaluation.domain.upload.entity.UploadHistory;
import com.hiscope.evaluation.domain.upload.handler.DepartmentUploadHandler;
import com.hiscope.evaluation.domain.upload.handler.EmployeeUploadHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 엑셀 업로드 조율 서비스.
 *
 * 이력 저장은 UploadHistoryService(별도 빈)에 위임한다.
 * - Propagation.REQUIRES_NEW 적용을 위해 self-call 대신 별도 빈 주입 방식 사용
 * - 핸들러(handle)가 BusinessException을 던져도 catch 블록에서 FAILED 이력을 기록하고 예외를 재던진다.
 *   덕분에 파일 파싱 실패 시도도 업로드 이력에 남아 운영자가 추적 가능하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadService {

    private final UploadHistoryService uploadHistoryService;
    private final DepartmentUploadHandler departmentHandler;
    private final EmployeeUploadHandler employeeHandler;

    public List<UploadHistory> findHistory(Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return uploadHistoryService.findByOrg(orgId);
    }

    public List<UploadHistory> findRecentHistory(Long orgId, int limit) {
        SecurityUtils.checkOrgAccess(orgId);
        return uploadHistoryService.findRecentByOrg(orgId, limit);
    }

    public List<UploadHistory> findRecentHistory(Long orgId,
                                                 int limit,
                                                 String uploadType,
                                                 String status,
                                                 LocalDate dateFrom,
                                                 LocalDate dateTo,
                                                 String keyword,
                                                 String sortBy,
                                                 String sortDir) {
        SecurityUtils.checkOrgAccess(orgId);
        Comparator<UploadHistory> comparator = buildComparator(sortBy, sortDir);
        return uploadHistoryService.findRecentByOrg(orgId, limit).stream()
                .filter(h -> !StringUtils.hasText(uploadType) || uploadType.equals(h.getUploadType()))
                .filter(h -> !StringUtils.hasText(status) || status.equals(h.getStatus()))
                .filter(h -> dateFrom == null || !h.getCreatedAt().toLocalDate().isBefore(dateFrom))
                .filter(h -> dateTo == null || !h.getCreatedAt().toLocalDate().isAfter(dateTo))
                .filter(h -> {
                    if (!StringUtils.hasText(keyword)) {
                        return true;
                    }
                    return h.getFileName().toLowerCase().contains(keyword.trim().toLowerCase());
                })
                .sorted(comparator)
                .toList();
    }

    public UploadFailureCsv buildFailureCsv(Long orgId, Long historyId) {
        SecurityUtils.checkOrgAccess(orgId);
        UploadHistory history = uploadHistoryService.getByOrgAndId(orgId, historyId);
        List<UploadError> errors = uploadHistoryService.parseErrors(history);
        StringBuilder csv = new StringBuilder();
        csv.append("row_number,column,message\n");
        for (UploadError error : errors) {
            csv.append(error.getRowNumber())
                    .append(',')
                    .append(CsvUtils.escape(error.getColumn()))
                    .append(',')
                    .append(CsvUtils.escape(error.getMessage()))
                    .append('\n');
        }
        String filename = "upload_errors_" + history.getId() + ".csv";
        return new UploadFailureCsv(filename, csv.toString(), errors.size());
    }

    /**
     * 부서 엑셀 업로드.
     *
     * 정상 처리 흐름: handler → REQUIRES_NEW로 이력 저장 → 결과 반환
     * 파싱 실패 흐름: BusinessException catch → FAILED 이력 저장 → 예외 재던짐
     *   (FAILED 이력은 REQUIRES_NEW로 독립 커밋 → 메인 트랜잭션 롤백과 무관)
     */
    @Transactional
    public UploadResult uploadDepartments(Long orgId, MultipartFile file) {
        SecurityUtils.checkOrgAccess(orgId);
        validateFileType(file);
        Long uploadedBy = SecurityUtils.getCurrentUser().getId();
        String fileName = resolveFileName(file);
        try {
            UploadResult result = departmentHandler.handle(orgId, file);
            uploadHistoryService.record(orgId, result, uploadedBy);
            return result;
        } catch (BusinessException e) {
            log.error("부서 업로드 실패 org={}, file={}: {}", orgId, file.getOriginalFilename(), e.getMessage());
            UploadResult failResult = UploadResult.failed("DEPARTMENT", fileName,
                    List.of(new UploadError(0, "-", e.getMessage())));
            uploadHistoryService.record(orgId, failResult, uploadedBy);
            throw e;
        } catch (Exception e) {
            log.error("부서 업로드 처리 중 시스템 오류 org={}, file={}", orgId, fileName, e);
            UploadResult failResult = UploadResult.failed("DEPARTMENT", fileName,
                    List.of(new UploadError(0, "-", "시스템 오류로 업로드 처리에 실패했습니다.")));
            uploadHistoryService.record(orgId, failResult, uploadedBy);
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR, "엑셀 처리 중 시스템 오류가 발생했습니다.");
        }
    }

    /**
     * 직원 엑셀 업로드.
     *
     * 정상 처리 흐름: handler → REQUIRES_NEW로 이력 저장 → 결과 반환
     * 파싱 실패 흐름: BusinessException catch → FAILED 이력 저장 → 예외 재던짐
     */
    @Transactional
    public UploadResult uploadEmployees(Long orgId, MultipartFile file) {
        SecurityUtils.checkOrgAccess(orgId);
        validateFileType(file);
        Long uploadedBy = SecurityUtils.getCurrentUser().getId();
        String fileName = resolveFileName(file);
        try {
            UploadResult result = employeeHandler.handle(orgId, file);
            uploadHistoryService.record(orgId, result, uploadedBy);
            return result;
        } catch (BusinessException e) {
            log.error("직원 업로드 실패 org={}, file={}: {}", orgId, file.getOriginalFilename(), e.getMessage());
            UploadResult failResult = UploadResult.failed("EMPLOYEE", fileName,
                    List.of(new UploadError(0, "-", e.getMessage())));
            uploadHistoryService.record(orgId, failResult, uploadedBy);
            throw e;
        } catch (Exception e) {
            log.error("직원 업로드 처리 중 시스템 오류 org={}, file={}", orgId, fileName, e);
            UploadResult failResult = UploadResult.failed("EMPLOYEE", fileName,
                    List.of(new UploadError(0, "-", "시스템 오류로 업로드 처리에 실패했습니다.")));
            uploadHistoryService.record(orgId, failResult, uploadedBy);
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR, "엑셀 처리 중 시스템 오류가 발생했습니다.");
        }
    }

    /** .xlsx 확장자 검증 — 다른 형식 업로드 시도 조기 차단 */
    private void validateFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.EXCEL_INVALID_FORMAT);
        }
    }

    private String resolveFileName(MultipartFile file) {
        return file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx";
    }

    private Comparator<UploadHistory> buildComparator(String sortBy, String sortDir) {
        Comparator<UploadHistory> comparator = switch (sortBy) {
            case "uploadType" -> Comparator.comparing(UploadHistory::getUploadType, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(UploadHistory::getStatus, String.CASE_INSENSITIVE_ORDER);
            case "fileName" -> Comparator.comparing(UploadHistory::getFileName, String.CASE_INSENSITIVE_ORDER);
            case "successRows" -> Comparator.comparingInt(UploadHistory::getSuccessRows);
            case "failRows" -> Comparator.comparingInt(UploadHistory::getFailRows);
            case "totalRows" -> Comparator.comparingInt(UploadHistory::getTotalRows);
            case "createdAt" -> Comparator.comparing(UploadHistory::getCreatedAt);
            default -> Comparator.comparing(UploadHistory::getCreatedAt);
        };
        if (!"asc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    public record UploadFailureCsv(String filename, String content, int errorCount) {
    }
}
