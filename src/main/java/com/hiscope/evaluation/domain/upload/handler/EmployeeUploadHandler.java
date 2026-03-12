package com.hiscope.evaluation.domain.upload.handler;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.util.ExcelUtils;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import com.hiscope.evaluation.domain.upload.dto.UploadError;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 직원 엑셀 업로드 핸들러
 * 기본 컬럼: 사원번호(0), 이름(1), 부서코드(2), 직위(3), 직책(4), 이메일(5), 로그인ID(6), 상태(7)
 * 확장 컬럼: 병원형 속성 컬럼(8+)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeUploadHandler {

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final EmployeeAttributeRepository employeeAttributeRepository;
    private final EmployeeAttributeValueRepository employeeAttributeValueRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationSettingService organizationSettingService;

    private static final List<String> VALID_STATUSES = List.of("ACTIVE", "INACTIVE", "LEAVE");
    private static final Map<String, AttributeColumnDefinition> PRESET_ATTRIBUTE_COLUMNS = Map.ofEntries(
            Map.entry("경혁팀", new AttributeColumnDefinition("change_innovation_team", true)),
            Map.entry("경혁팀 여부", new AttributeColumnDefinition("change_innovation_team", true)),
            Map.entry("경혁팀장", new AttributeColumnDefinition("change_innovation_team_leader", true)),
            Map.entry("경혁팀장 여부", new AttributeColumnDefinition("change_innovation_team_leader", true)),
            Map.entry("부서장", new AttributeColumnDefinition("department_head", true)),
            Map.entry("부서장 여부", new AttributeColumnDefinition("department_head", true)),
            Map.entry("1인부서", new AttributeColumnDefinition("single_member_department", true)),
            Map.entry("의료리더", new AttributeColumnDefinition("medical_leader", true)),
            Map.entry("기관장", new AttributeColumnDefinition("institution_head", true)),
            Map.entry("소속장", new AttributeColumnDefinition("unit_head", true)),
            Map.entry("평가제외", new AttributeColumnDefinition("evaluation_excluded", true))
    );

    @Transactional
    public UploadResult handle(Long orgId, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        List<UploadError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            AttributeColumnMapping attributeColumnMapping = resolveAttributeColumns(headerRow, errors);

            // 부서 코드 맵
            Map<String, Long> deptCodeToId = departmentRepository.findByOrganizationIdOrderByNameAsc(orgId)
                    .stream().collect(Collectors.toMap(Department::getCode, Department::getId));

            // 기존 사원번호 셋
            Set<String> existingNumbers = employeeRepository.findByOrganizationIdOrderByNameAsc(orgId)
                    .stream().map(Employee::getEmployeeNumber).collect(Collectors.toSet());

            // 기존 로그인 ID 셋 — 현재 기관 직원 계정만 로드 (타 기관 데이터 노출 방지)
            Set<String> existingLoginIds = new HashSet<>();
            userAccountRepository.findByOrganizationId(orgId).stream()
                    .map(UserAccount::getLoginId).forEach(existingLoginIds::add);
            // 관리자 계정은 전체 조회 (loginId 전역 유니크 보장 목적, 계정 수 소량)
            accountRepository.findAll().stream()
                    .map(a -> a.getLoginId()).forEach(existingLoginIds::add);

            Set<String> uploadedNumbers = new HashSet<>();
            Set<String> uploadedLoginIds = new HashSet<>();
            Map<String, EmployeeAttribute> attributeMap = loadOrCreateAttributes(
                    orgId,
                    attributeColumnMapping.columnByIndex().values().stream().map(AttributeColumn::attributeKey).collect(Collectors.toSet())
            );
            List<EmployeeAttributeValue> attributeValuesToSave = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                int maxColumnIndex = Math.max(7, attributeColumnMapping.maxColumnIndex());
                if (ExcelUtils.isRowEmpty(row, maxColumnIndex)) continue;
                totalRows++;
                validateMaxRows(orgId, totalRows);

                String empNum   = ExcelUtils.getCellString(row, 0);
                String name     = ExcelUtils.getCellString(row, 1);
                String deptCode = ExcelUtils.getCellString(row, 2).toUpperCase();
                String position = ExcelUtils.getCellString(row, 3);
                String jobTitle = ExcelUtils.getCellString(row, 4);
                String email    = ExcelUtils.getCellString(row, 5);
                String loginId  = ExcelUtils.getCellString(row, 6);
                String statusRaw = ExcelUtils.getCellString(row, 7).toUpperCase();
                String status   = statusRaw.isBlank() ? "ACTIVE" : statusRaw;

                // --- 검증 ---
                boolean rowError = false;
                if (empNum.isBlank()) {
                    errors.add(new UploadError(i + 1, "사원번호", "필수 항목 누락")); rowError = true;
                }
                if (name.isBlank()) {
                    errors.add(new UploadError(i + 1, "이름", "필수 항목 누락")); rowError = true;
                }
                if (deptCode.isBlank() || !deptCodeToId.containsKey(deptCode)) {
                    errors.add(new UploadError(i + 1, "부서코드", "존재하지 않는 부서 코드: " + deptCode)); rowError = true;
                }
                if (!VALID_STATUSES.contains(status)) {
                    errors.add(new UploadError(i + 1, "상태", "허용값: ACTIVE/INACTIVE/LEAVE")); rowError = true;
                }
                if (loginId.isBlank()) {
                    errors.add(new UploadError(i + 1, "로그인ID", "필수 항목 누락")); rowError = true;
                } else if (!loginId.matches("^[a-zA-Z0-9._-]{4,50}$")) {
                    errors.add(new UploadError(i + 1, "로그인ID", "형식 오류 (4~50자, 영문/숫자/._-)")); rowError = true;
                } else if (existingLoginIds.contains(loginId) || uploadedLoginIds.contains(loginId)) {
                    errors.add(new UploadError(i + 1, "로그인ID", "이미 사용 중인 로그인 ID: " + loginId)); rowError = true;
                }
                if (!empNum.isBlank()) {
                    if (existingNumbers.contains(empNum)) {
                        errors.add(new UploadError(i + 1, "사원번호", "이미 등록된 사원번호: " + empNum)); rowError = true;
                    } else if (uploadedNumbers.contains(empNum)) {
                        errors.add(new UploadError(i + 1, "사원번호", "파일 내 중복 사원번호: " + empNum)); rowError = true;
                    }
                }
                List<ResolvedAttributeValue> resolvedAttributeValues = resolveAttributeValues(
                        row,
                        i + 1,
                        attributeColumnMapping.columnByIndex(),
                        errors
                );
                if (resolvedAttributeValues == null) {
                    rowError = true;
                }
                if (rowError) continue;

                // --- 저장 ---
                Employee emp = Employee.builder()
                        .organizationId(orgId)
                        .departmentId(deptCodeToId.get(deptCode))
                        .name(name).employeeNumber(empNum)
                        .position(position).jobTitle(jobTitle)
                        .email(email).status(status)
                        .build();
                emp = employeeRepository.save(emp);

                UserAccount ua = UserAccount.builder()
                        .employee(emp).organizationId(orgId)
                        .loginId(loginId)
                        .passwordHash(passwordEncoder.encode("password123"))
                        .role("ROLE_USER").build();
                userAccountRepository.save(ua);

                for (ResolvedAttributeValue resolvedValue : resolvedAttributeValues) {
                    EmployeeAttribute attribute = attributeMap.get(resolvedValue.attributeKey());
                    if (attribute == null) {
                        errors.add(new UploadError(i + 1, resolvedValue.columnName(),
                                "속성 매핑 실패: attribute_key=" + resolvedValue.attributeKey() + "를 찾을 수 없습니다."));
                        continue;
                    }
                    attributeValuesToSave.add(EmployeeAttributeValue.builder()
                            .employeeId(emp.getId())
                            .attributeId(attribute.getId())
                            .valueText(resolvedValue.valueText())
                            .build());
                }

                existingNumbers.add(empNum);
                existingLoginIds.add(loginId);
                uploadedNumbers.add(empNum);
                uploadedLoginIds.add(loginId);
                successRows++;
            }

            if (!attributeValuesToSave.isEmpty()) {
                employeeAttributeValueRepository.saveAll(attributeValuesToSave);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR);
        }

        if (totalRows == 0) return UploadResult.failed("EMPLOYEE", fileName, List.of(
                new UploadError(0, "-", "유효한 데이터 행이 없습니다.")));
        if (errors.isEmpty()) return UploadResult.success("EMPLOYEE", fileName, totalRows);
        if (successRows > 0) return UploadResult.partial("EMPLOYEE", fileName, totalRows, successRows, errors);
        return UploadResult.failed("EMPLOYEE", fileName, errors);
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

    private AttributeColumnMapping resolveAttributeColumns(Row headerRow, List<UploadError> errors) {
        if (headerRow == null) {
            return new AttributeColumnMapping(Map.of(), 7);
        }
        Map<Integer, AttributeColumn> result = new LinkedHashMap<>();
        Set<String> usedAttributeKeys = new HashSet<>();
        int maxColumnIndex = Math.max(7, headerRow.getLastCellNum() - 1);
        for (int col = 8; col <= maxColumnIndex; col++) {
            String headerName = ExcelUtils.getCellString(headerRow, col);
            if (headerName.isBlank()) {
                continue;
            }
            AttributeColumn parsed = parseAttributeColumn(headerName.trim());
            if (parsed == null) {
                errors.add(new UploadError(1, headerName, "속성 컬럼 인식 실패. 사전 정의 컬럼 또는 attr:attribute_key 형식을 사용해주세요."));
                continue;
            }
            if (!usedAttributeKeys.add(parsed.attributeKey())) {
                errors.add(new UploadError(1, headerName, "동일한 속성 키가 중복되었습니다: " + parsed.attributeKey()));
                continue;
            }
            result.put(col, parsed);
        }
        return new AttributeColumnMapping(result, maxColumnIndex);
    }

    private AttributeColumn parseAttributeColumn(String headerName) {
        AttributeColumnDefinition preset = PRESET_ATTRIBUTE_COLUMNS.get(headerName);
        if (preset != null) {
            return new AttributeColumn(headerName, preset.attributeKey(), preset.booleanLike());
        }
        if (headerName.startsWith("attr:")) {
            String key = headerName.substring(5).trim();
            if (!key.matches("^[a-zA-Z0-9_-]{2,100}$")) {
                return null;
            }
            return new AttributeColumn(headerName, key, false);
        }
        if (headerName.startsWith("속성:")) {
            String key = headerName.substring(3).trim();
            if (!key.matches("^[a-zA-Z0-9_-]{2,100}$")) {
                return null;
            }
            return new AttributeColumn(headerName, key, false);
        }
        return null;
    }

    private List<ResolvedAttributeValue> resolveAttributeValues(Row row,
                                                                int rowNumber,
                                                                Map<Integer, AttributeColumn> columnMap,
                                                                List<UploadError> errors) {
        if (columnMap.isEmpty()) {
            return List.of();
        }
        List<ResolvedAttributeValue> resolved = new ArrayList<>();
        boolean hasError = false;
        for (Map.Entry<Integer, AttributeColumn> entry : columnMap.entrySet()) {
            String rawValue = ExcelUtils.getCellString(row, entry.getKey());
            AttributeColumn column = entry.getValue();
            String normalized = normalizeAttributeValue(column, rawValue);
            if (normalized == null) {
                errors.add(new UploadError(rowNumber, column.columnName(),
                        "허용값이 아닙니다. boolean 속성은 Y/N, true/false, 1/0 중 하나를 입력해주세요."));
                hasError = true;
                continue;
            }
            if (normalized.isBlank()) {
                continue;
            }
            resolved.add(new ResolvedAttributeValue(column.columnName(), column.attributeKey(), normalized));
        }
        if (hasError) {
            return null;
        }
        return resolved;
    }

    private String normalizeAttributeValue(AttributeColumn column, String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (!column.booleanLike()) {
            return normalized;
        }
        if (normalized.isBlank()) {
            return "N";
        }
        if ("Y".equalsIgnoreCase(normalized) || "TRUE".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
            return "Y";
        }
        if ("N".equalsIgnoreCase(normalized) || "FALSE".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
            return "N";
        }
        return null;
    }

    private Map<String, EmployeeAttribute> loadOrCreateAttributes(Long orgId, Set<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, EmployeeAttribute> existing = employeeAttributeRepository
                .findByOrganizationIdAndAttributeKeyIn(orgId, keys)
                .stream()
                .collect(Collectors.toMap(EmployeeAttribute::getAttributeKey, a -> a));
        List<EmployeeAttribute> toCreate = keys.stream()
                .filter(key -> !existing.containsKey(key))
                .map(key -> EmployeeAttribute.builder()
                        .organizationId(orgId)
                        .attributeKey(key)
                        .attributeName(resolveAttributeNameByKey(key))
                        .active(true)
                        .build())
                .toList();
        if (!toCreate.isEmpty()) {
            employeeAttributeRepository.saveAll(toCreate).forEach(attr -> existing.put(attr.getAttributeKey(), attr));
        }
        return existing;
    }

    private String resolveAttributeNameByKey(String key) {
        return PRESET_ATTRIBUTE_COLUMNS.entrySet().stream()
                .filter(entry -> entry.getValue().attributeKey().equals(key))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(key);
    }

    private record AttributeColumnDefinition(String attributeKey, boolean booleanLike) {
    }

    private record AttributeColumn(String columnName, String attributeKey, boolean booleanLike) {
    }

    private record AttributeColumnMapping(Map<Integer, AttributeColumn> columnByIndex, int maxColumnIndex) {
    }

    private record ResolvedAttributeValue(String columnName, String attributeKey, String valueText) {
    }
}
