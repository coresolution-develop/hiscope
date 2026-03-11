package com.hiscope.evaluation.domain.upload.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.upload.dto.UploadError;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import com.hiscope.evaluation.domain.upload.entity.UploadHistory;
import com.hiscope.evaluation.domain.upload.handler.DepartmentUploadHandler;
import com.hiscope.evaluation.domain.upload.handler.EmployeeUploadHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
        try {
            UploadResult result = departmentHandler.handle(orgId, file);
            uploadHistoryService.record(orgId, result, uploadedBy);
            return result;
        } catch (BusinessException e) {
            log.error("부서 업로드 실패 org={}, file={}: {}", orgId, file.getOriginalFilename(), e.getMessage());
            UploadResult failResult = UploadResult.failed("DEPARTMENT", file.getOriginalFilename(),
                    List.of(new UploadError(0, "-", e.getMessage())));
            uploadHistoryService.record(orgId, failResult, uploadedBy);
            throw e;
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
        try {
            UploadResult result = employeeHandler.handle(orgId, file);
            uploadHistoryService.record(orgId, result, uploadedBy);
            return result;
        } catch (BusinessException e) {
            log.error("직원 업로드 실패 org={}, file={}: {}", orgId, file.getOriginalFilename(), e.getMessage());
            UploadResult failResult = UploadResult.failed("EMPLOYEE", file.getOriginalFilename(),
                    List.of(new UploadError(0, "-", e.getMessage())));
            uploadHistoryService.record(orgId, failResult, uploadedBy);
            throw e;
        }
    }

    /** .xlsx 확장자 검증 — 다른 형식 업로드 시도 조기 차단 */
    private void validateFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.EXCEL_INVALID_FORMAT);
        }
    }
}
