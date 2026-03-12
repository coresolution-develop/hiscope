package com.hiscope.evaluation.domain.upload.handler;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.util.ExcelUtils;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import com.hiscope.evaluation.domain.upload.dto.UploadError;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 부서 엑셀 업로드 핸들러
 * 컬럼: 부서코드(0), 부서명(1), 상위부서코드(2, 선택)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentUploadHandler {

    private final DepartmentRepository departmentRepository;
    private final OrganizationSettingService organizationSettingService;

    @Transactional
    public UploadResult handle(Long orgId, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        List<UploadError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            // 기존 부서 코드 맵
            Map<String, Department> existingByCode = new HashMap<>();
            departmentRepository.findByOrganizationIdOrderByNameAsc(orgId)
                    .forEach(d -> existingByCode.put(d.getCode(), d));

            // 이번 업로드에서 추가된 코드 (중복 체크용)
            Set<String> uploadedCodes = new HashSet<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelUtils.isRowEmpty(row, 2)) continue;
                totalRows++;
                validateMaxRows(orgId, totalRows);

                String code = ExcelUtils.getCellString(row, 0).toUpperCase();
                String name = ExcelUtils.getCellString(row, 1);
                String parentCode = ExcelUtils.getCellString(row, 2).toUpperCase();

                // --- 검증 ---
                if (code.isBlank()) {
                    errors.add(new UploadError(i + 1, "부서코드", "필수 항목이 누락되었습니다."));
                    continue;
                }
                if (!code.matches("^[A-Z0-9_]{2,20}$")) {
                    errors.add(new UploadError(i + 1, "부서코드", "코드는 2~20자의 영문 대문자, 숫자, 밑줄만 가능합니다."));
                    continue;
                }
                if (name.isBlank()) {
                    errors.add(new UploadError(i + 1, "부서명", "필수 항목이 누락되었습니다."));
                    continue;
                }
                if (uploadedCodes.contains(code)) {
                    errors.add(new UploadError(i + 1, "부서코드", "같은 파일 내 중복 코드: " + code));
                    continue;
                }
                Long parentId = null;
                if (!parentCode.isBlank()) {
                    Department parent = existingByCode.get(parentCode);
                    if (parent == null) {
                        errors.add(new UploadError(i + 1, "상위부서코드", "존재하지 않는 부서 코드: " + parentCode));
                        continue;
                    }
                    parentId = parent.getId();
                }

                // --- 저장 (upsert) ---
                if (existingByCode.containsKey(code)) {
                    Department dept = existingByCode.get(code);
                    dept.update(name, parentId, true);
                } else {
                    Department dept = Department.builder()
                            .organizationId(orgId).parentId(parentId)
                            .name(name).code(code).active(true).build();
                    Department saved = departmentRepository.save(dept);
                    existingByCode.put(code, saved);
                }
                uploadedCodes.add(code);
                successRows++;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR);
        }

        if (totalRows == 0) return UploadResult.failed("DEPARTMENT", fileName, List.of(
                new UploadError(0, "-", "유효한 데이터 행이 없습니다.")));
        if (errors.isEmpty()) return UploadResult.success("DEPARTMENT", fileName, totalRows);
        if (successRows > 0) return UploadResult.partial("DEPARTMENT", fileName, totalRows, successRows, errors);
        return UploadResult.failed("DEPARTMENT", fileName, errors);
    }

    private void validateMaxRows(Long orgId, int totalRows) {
        int maxRows = organizationSettingService.resolveUploadMaxRows(orgId);
        if (totalRows > maxRows) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "업로드 가능한 최대 행 수(" + maxRows + "행)를 초과했습니다."
            );
        }
    }
}
