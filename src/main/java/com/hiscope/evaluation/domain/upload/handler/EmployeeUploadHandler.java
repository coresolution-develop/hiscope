package com.hiscope.evaluation.domain.upload.handler;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.util.ExcelUtils;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import com.hiscope.evaluation.domain.upload.dto.EmployeeUploadPreview;
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
 * 직원 업로드:
 * - 표준 템플릿 업로드 지원
 * - 운영 파일(작년 실사용 명부 형식) 자동 인식 업로드 지원
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
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationSettingService organizationSettingService;

    private static final List<String> VALID_STATUSES = List.of("ACTIVE", "INACTIVE", "LEAVE");
    private static final String DEFAULT_STATUS = "ACTIVE";

    private static final Map<String, AttributeColumnDefinition> COMMON_PRESET_ATTRIBUTE_COLUMNS = Map.ofEntries(
            Map.entry("기관장", new AttributeColumnDefinition("institution_head", true, true)),
            Map.entry("기관장여부", new AttributeColumnDefinition("institution_head", true, true)),
            Map.entry("소속장", new AttributeColumnDefinition("unit_head", true, true)),
            Map.entry("소속장여부", new AttributeColumnDefinition("unit_head", true, true)),
            Map.entry("부서장", new AttributeColumnDefinition("department_head", true, true)),
            Map.entry("부서장여부", new AttributeColumnDefinition("department_head", true, true)),
            Map.entry("평가제외", new AttributeColumnDefinition("evaluation_excluded", true, true)),
            Map.entry("평가제외여부", new AttributeColumnDefinition("evaluation_excluded", true, true))
    );
    private static final Map<String, AttributeColumnDefinition> HOSPITAL_PRESET_ATTRIBUTE_COLUMNS = Map.ofEntries(
            Map.entry("경혁팀", new AttributeColumnDefinition("change_innovation_team", true, true)),
            Map.entry("경혁팀여부", new AttributeColumnDefinition("change_innovation_team", true, true)),
            Map.entry("경혁팀장", new AttributeColumnDefinition("change_innovation_team_leader", true, true)),
            Map.entry("경혁팀장여부", new AttributeColumnDefinition("change_innovation_team_leader", true, true)),
            Map.entry("1인부서", new AttributeColumnDefinition("single_member_department", true, true)),
            Map.entry("1인부서여부", new AttributeColumnDefinition("single_member_department", true, true)),
            Map.entry("진료팀장", new AttributeColumnDefinition("clinical_team_leader", true, true)),
            Map.entry("진료팀장여부", new AttributeColumnDefinition("clinical_team_leader", true, true)),
            Map.entry("의료리더", new AttributeColumnDefinition("medical_leader", true, true)),
            Map.entry("의료리더여부", new AttributeColumnDefinition("medical_leader", true, true))
    );
    private static final Map<String, AttributeColumnDefinition> AFFILIATE_PRESET_ATTRIBUTE_COLUMNS = Map.ofEntries(
            Map.entry("계열사정책그룹", new AttributeColumnDefinition("affiliate_policy_group", false, true))
    );
    private static final Map<String, AttributeColumnDefinition> SUPPORT_COLUMNS = Map.ofEntries(
            Map.entry("평가대상여부", new AttributeColumnDefinition("evaluation_target_flag", true, false)),
            Map.entry("이전부서명", new AttributeColumnDefinition("previous_department_name", false, false)),
            Map.entry("입사일자", new AttributeColumnDefinition("hire_date", false, false)),
            Map.entry("입사일", new AttributeColumnDefinition("hire_date", false, false)),
            Map.entry("퇴사일자", new AttributeColumnDefinition("retire_date", false, false)),
            Map.entry("퇴사일", new AttributeColumnDefinition("retire_date", false, false)),
            Map.entry("핸드폰번호", new AttributeColumnDefinition("mobile_phone", false, false)),
            Map.entry("휴대폰번호", new AttributeColumnDefinition("mobile_phone", false, false)),
            Map.entry("기관명", new AttributeColumnDefinition("institution_name", false, false)),
            Map.entry("소속기관", new AttributeColumnDefinition("affiliated_organization_name", false, false)),
            Map.entry("비고", new AttributeColumnDefinition("note", false, false))
    );

    private static final Set<String> EMPLOYEE_NUMBER_HEADERS = Set.of("사원번호", "사번", "직원번호");
    private static final Set<String> NAME_HEADERS = Set.of("이름", "성명");
    private static final Set<String> DEPARTMENT_CODE_HEADERS = Set.of("부서코드");
    private static final Set<String> DEPARTMENT_NAME_HEADERS = Set.of("부서명", "소속부서", "현부서명");
    private static final Set<String> POSITION_HEADERS = Set.of("직위", "직급");
    private static final Set<String> JOB_TITLE_HEADERS = Set.of("직책", "직무", "직책명");
    private static final Set<String> EMAIL_HEADERS = Set.of("이메일", "메일");
    private static final Set<String> LOGIN_ID_HEADERS = Set.of("로그인ID", "로그인아이디", "LOGINID");
    private static final Set<String> STATUS_HEADERS = Set.of("상태", "재직상태");

    @Transactional
    public UploadResult handle(Long orgId, MultipartFile file) {
        return process(orgId, file, true).result();
    }

    @Transactional(readOnly = true)
    public EmployeeUploadPreview preview(Long orgId, MultipartFile file) {
        ProcessOutcome outcome = process(orgId, file, false);
        ImportContext context = outcome.context();
        return EmployeeUploadPreview.builder()
                .fileName(file.getOriginalFilename())
                .detectedFileType(context.detectedFileType())
                .importProfile(context.importProfile())
                .mappedColumns(context.mappedColumns())
                .missingRequiredColumns(context.missingRequiredColumns())
                .plannedTransformations(context.plannedTransformations())
                .departmentMatchingResults(outcome.departmentMatchingResults())
                .uploadable(context.missingRequiredColumns().isEmpty() && !"FAILED".equals(outcome.result().getStatus()))
                .build();
    }

    private ProcessOutcome process(Long orgId, MultipartFile file, boolean persist) {
        String fileName = file.getOriginalFilename();
        List<UploadError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> departmentMatchingResults = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Organization organization = organizationRepository.findById(orgId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
            ImportContext context = detectImportContext(organization, sheet);
            if (!context.missingRequiredColumns().isEmpty()) {
                context.missingRequiredColumns().forEach(col ->
                        errors.add(new UploadError(0, col, "필수 컬럼이 누락되었습니다."))
                );
                return new ProcessOutcome(UploadResult.failed("EMPLOYEE", fileName, errors), context, List.of());
            }

            List<Department> departments = departmentRepository.findByOrganizationIdOrderByNameAsc(orgId);
            Map<String, Department> deptByCode = departments.stream()
                    .filter(d -> d.getCode() != null && !d.getCode().isBlank())
                    .collect(Collectors.toMap(d -> d.getCode().trim().toUpperCase(), d -> d, (a, b) -> a, LinkedHashMap::new));
            Map<String, List<Department>> deptByName = departments.stream()
                    .collect(Collectors.groupingBy(d -> normalizeDepartmentNameKey(d.getName()), LinkedHashMap::new, Collectors.toList()));

            Set<String> existingNumbers = employeeRepository.findByOrganizationIdOrderByNameAsc(orgId)
                    .stream()
                    .map(Employee::getEmployeeNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> existingLoginIds = new HashSet<>();
            userAccountRepository.findByOrganizationId(orgId).forEach(ua -> existingLoginIds.add(ua.getLoginId()));
            accountRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId).forEach(ac -> existingLoginIds.add(ac.getLoginId()));

            Set<String> uploadedNumbers = new HashSet<>();
            Set<String> uploadedLoginIds = new HashSet<>();
            Map<String, EmployeeAttribute> attributeMap = loadOrCreateAttributes(
                    orgId,
                    context.attributeColumns().values().stream()
                            .filter(AttributeColumn::saveAsAttribute)
                            .map(AttributeColumn::attributeKey)
                            .collect(Collectors.toSet())
            );
            List<EmployeeAttributeValue> attributeValuesToSave = new ArrayList<>();

            for (int i = context.dataStartRowIndex(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelUtils.isRowEmpty(row, context.maxColumnIndex())) {
                    continue;
                }
                totalRows++;
                validateMaxRows(orgId, totalRows);

                ParsedRow parsed = parseRow(row, context, deptByCode, deptByName);
                if (departmentMatchingResults.size() < 20) {
                    departmentMatchingResults.add(formatDepartmentMatchResult(i + 1, parsed.departmentMatchResult()));
                }
                boolean rowError = false;

                if (parsed.employeeNumber().isBlank()) {
                    errors.add(new UploadError(i + 1, "사원번호", "필수 항목 누락"));
                    rowError = true;
                }
                if (parsed.name().isBlank()) {
                    errors.add(new UploadError(i + 1, "이름", "필수 항목 누락"));
                    rowError = true;
                }
                if (parsed.departmentId() == null) {
                    errors.add(new UploadError(i + 1, "부서", parsed.departmentErrorMessage()));
                    rowError = true;
                }
                if (!VALID_STATUSES.contains(parsed.status())) {
                    errors.add(new UploadError(i + 1, "상태", "허용값: ACTIVE/INACTIVE/LEAVE"));
                    rowError = true;
                }
                if (parsed.loginId().isBlank()) {
                    errors.add(new UploadError(i + 1, "로그인ID", "사번 기반 로그인ID 생성에 실패했습니다."));
                    rowError = true;
                } else if (!parsed.loginId().matches("^[a-zA-Z0-9._-]{1,50}$")) {
                    errors.add(new UploadError(i + 1, "로그인ID", "형식 오류 (1~50자, 영문/숫자/._-)"));
                    rowError = true;
                } else if (existingLoginIds.contains(parsed.loginId()) || uploadedLoginIds.contains(parsed.loginId())) {
                    errors.add(new UploadError(i + 1, "로그인ID", "이미 사용 중인 로그인 ID: " + parsed.loginId()));
                    rowError = true;
                }

                if (!parsed.employeeNumber().isBlank()) {
                    if (existingNumbers.contains(parsed.employeeNumber())) {
                        errors.add(new UploadError(i + 1, "사원번호", "이미 등록된 사원번호: " + parsed.employeeNumber()));
                        rowError = true;
                    } else if (uploadedNumbers.contains(parsed.employeeNumber())) {
                        errors.add(new UploadError(i + 1, "사원번호", "파일 내 중복 사원번호: " + parsed.employeeNumber()));
                        rowError = true;
                    }
                }

                List<ResolvedAttributeValue> resolvedAttributeValues = resolveAttributeValues(
                        row, i + 1, context.attributeColumns(), errors
                );
                if (resolvedAttributeValues == null) {
                    rowError = true;
                }
                if (rowError) {
                    continue;
                }

                if (persist) {
                    Employee employee = employeeRepository.save(Employee.builder()
                            .organizationId(orgId)
                            .departmentId(parsed.departmentId())
                            .name(parsed.name())
                            .employeeNumber(parsed.employeeNumber())
                            .position(parsed.position())
                            .jobTitle(parsed.jobTitle())
                            .email(parsed.email())
                            .status(parsed.status())
                            .build());

                    userAccountRepository.save(UserAccount.builder()
                            .employee(employee)
                            .organizationId(orgId)
                            .loginId(parsed.loginId())
                            .passwordHash(passwordEncoder.encode("password123"))
                            .role("ROLE_USER")
                            .build());

                    for (ResolvedAttributeValue value : resolvedAttributeValues) {
                        EmployeeAttribute attribute = attributeMap.get(value.attributeKey());
                        if (attribute == null) {
                            errors.add(new UploadError(i + 1, value.columnName(),
                                    "속성 매핑 실패: attribute_key=" + value.attributeKey()));
                            continue;
                        }
                        attributeValuesToSave.add(EmployeeAttributeValue.builder()
                                .employeeId(employee.getId())
                                .attributeId(attribute.getId())
                                .valueText(value.valueText())
                                .build());
                    }
                }

                existingNumbers.add(parsed.employeeNumber());
                existingLoginIds.add(parsed.loginId());
                uploadedNumbers.add(parsed.employeeNumber());
                uploadedLoginIds.add(parsed.loginId());
                successRows++;
            }

            if (persist && !attributeValuesToSave.isEmpty()) {
                employeeAttributeValueRepository.saveAll(attributeValuesToSave);
            }

            UploadResult result;
            if (totalRows == 0) {
                result = UploadResult.failed("EMPLOYEE", fileName, List.of(new UploadError(0, "-", "유효한 데이터 행이 없습니다.")));
            } else if (errors.isEmpty()) {
                result = UploadResult.success("EMPLOYEE", fileName, totalRows);
            } else if (successRows > 0) {
                result = UploadResult.partial("EMPLOYEE", fileName, totalRows, successRows, errors);
            } else {
                result = UploadResult.failed("EMPLOYEE", fileName, errors);
            }
            return new ProcessOutcome(result, context, departmentMatchingResults);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR);
        }
    }

    private ImportContext detectImportContext(Organization organization, Sheet sheet) {
        Map<String, AttributeColumnDefinition> presetColumns = buildPresetColumns(organization.getOrganizationType(), organization.getOrganizationProfile());
        HeaderProbe probe = findBestHeaderRow(sheet);
        if (probe == null) {
            return ImportContext.failed(
                    detectedTypeByProfile(organization.getOrganizationProfile()),
                    importProfileByMode(organization.getOrganizationProfile(), false),
                    List.of("사원번호", "이름", "부서코드 또는 부서명")
            );
        }

        Integer employeeNumberCol = findColumn(probe.normalizedHeaderByCol(), EMPLOYEE_NUMBER_HEADERS);
        Integer nameCol = findColumn(probe.normalizedHeaderByCol(), NAME_HEADERS);
        Integer deptCodeCol = findColumn(probe.normalizedHeaderByCol(), DEPARTMENT_CODE_HEADERS);
        Integer deptNameCol = findColumn(probe.normalizedHeaderByCol(), DEPARTMENT_NAME_HEADERS);
        Integer positionCol = findColumn(probe.normalizedHeaderByCol(), POSITION_HEADERS);
        Integer jobTitleCol = findColumn(probe.normalizedHeaderByCol(), JOB_TITLE_HEADERS);
        Integer emailCol = findColumn(probe.normalizedHeaderByCol(), EMAIL_HEADERS);
        Integer loginIdCol = findColumn(probe.normalizedHeaderByCol(), LOGIN_ID_HEADERS);
        Integer statusCol = findColumn(probe.normalizedHeaderByCol(), STATUS_HEADERS);

        List<String> missing = new ArrayList<>();
        if (employeeNumberCol == null) {
            missing.add("사원번호/사번");
        }
        if (nameCol == null) {
            missing.add("이름/성명");
        }
        if (deptCodeCol == null && deptNameCol == null) {
            missing.add("부서코드 또는 부서명");
        }

        boolean standardTemplate = loginIdCol != null && deptCodeCol != null && probe.rowIndex() == 0;
        String detectedType = standardTemplate
                ? "STANDARD_TEMPLATE"
                : detectedTypeByProfile(organization.getOrganizationProfile());
        String importProfile = importProfileByMode(organization.getOrganizationProfile(), standardTemplate);

        Map<Integer, AttributeColumn> attributeColumns = resolveAttributeColumns(
                probe, presetColumns
        );

        List<String> mappedColumns = new ArrayList<>();
        addMapped(mappedColumns, "사원번호", employeeNumberCol);
        addMapped(mappedColumns, "이름", nameCol);
        addMapped(mappedColumns, "부서코드", deptCodeCol);
        addMapped(mappedColumns, "부서명", deptNameCol);
        addMapped(mappedColumns, "직위", positionCol);
        addMapped(mappedColumns, "직책", jobTitleCol);
        addMapped(mappedColumns, "이메일", emailCol);
        addMapped(mappedColumns, "로그인ID", loginIdCol);
        addMapped(mappedColumns, "상태", statusCol);
        attributeColumns.forEach((idx, col) -> mappedColumns.add(col.columnName() + " -> " + col.attributeKey() + " (col=" + (idx + 1) + ")"));

        List<String> transforms = new ArrayList<>();
        if (loginIdCol == null) {
            transforms.add("login_id = 사번(사원번호) 자동 적용");
        }
        if (statusCol == null) {
            transforms.add("상태값 누락 시 ACTIVE 기본값 적용");
        }
        transforms.add("boolean 컬럼 값 정규화: Y/N, TRUE/FALSE, 1/0 허용");
        transforms.add("보조 컬럼(기관명/소속기관/입사일/퇴사일/핸드폰번호 등)은 실패 없이 수용");

        return new ImportContext(
                probe.rowIndex(),
                probe.maxColumnIndex(),
                employeeNumberCol,
                nameCol,
                deptCodeCol,
                deptNameCol,
                positionCol,
                jobTitleCol,
                emailCol,
                loginIdCol,
                statusCol,
                attributeColumns,
                detectedType,
                importProfile,
                mappedColumns,
                missing,
                transforms
        );
    }

    private HeaderProbe findBestHeaderRow(Sheet sheet) {
        HeaderProbe best = null;
        int bestScore = -1;
        int maxProbeRow = Math.min(6, sheet.getLastRowNum());
        for (int rowIdx = 0; rowIdx <= maxProbeRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                continue;
            }
            Map<Integer, String> normalized = new LinkedHashMap<>();
            Map<Integer, String> rawHeader = new LinkedHashMap<>();
            int maxCol = Math.max(0, row.getLastCellNum() - 1);
            for (int col = 0; col <= maxCol; col++) {
                String rawValue = ExcelUtils.getCellString(row, col);
                if (rawValue.isBlank()) {
                    continue;
                }
                normalized.put(col, normalizeHeader(rawValue));
                rawHeader.put(col, rawValue.trim());
            }
            if (normalized.isEmpty()) {
                continue;
            }
            int score = 0;
            if (findColumn(normalized, EMPLOYEE_NUMBER_HEADERS) != null) score += 3;
            if (findColumn(normalized, NAME_HEADERS) != null) score += 3;
            if (findColumn(normalized, DEPARTMENT_CODE_HEADERS) != null) score += 2;
            if (findColumn(normalized, DEPARTMENT_NAME_HEADERS) != null) score += 2;
            if (findColumn(normalized, LOGIN_ID_HEADERS) != null) score += 1;
            if (score > bestScore) {
                best = new HeaderProbe(rowIdx, maxCol, normalized, rawHeader);
                bestScore = score;
            }
        }
        return bestScore >= 5 ? best : null;
    }

    private Map<Integer, AttributeColumn> resolveAttributeColumns(HeaderProbe probe,
                                                                  Map<String, AttributeColumnDefinition> presetColumns) {
        Map<Integer, AttributeColumn> result = new LinkedHashMap<>();
        Set<String> usedAttributeKeys = new HashSet<>();
        for (Map.Entry<Integer, String> entry : probe.normalizedHeaderByCol().entrySet()) {
            Integer col = entry.getKey();
            String normalizedHeader = entry.getValue();
            if (isCoreColumn(normalizedHeader)) {
                continue;
            }
            AttributeColumn parsed = parseAttributeColumn(normalizedHeader, presetColumns, col, probe);
            if (parsed == null) {
                continue; // 운영 보조 컬럼/알수없는 컬럼은 무시(실패 유발 금지)
            }
            if (parsed.saveAsAttribute() && !usedAttributeKeys.add(parsed.attributeKey())) {
                continue;
            }
            result.put(col, parsed);
        }
        return result;
    }

    private boolean isCoreColumn(String normalizedHeader) {
        return containsAlias(normalizedHeader, EMPLOYEE_NUMBER_HEADERS)
                || containsAlias(normalizedHeader, NAME_HEADERS)
                || containsAlias(normalizedHeader, DEPARTMENT_CODE_HEADERS)
                || containsAlias(normalizedHeader, DEPARTMENT_NAME_HEADERS)
                || containsAlias(normalizedHeader, POSITION_HEADERS)
                || containsAlias(normalizedHeader, JOB_TITLE_HEADERS)
                || containsAlias(normalizedHeader, EMAIL_HEADERS)
                || containsAlias(normalizedHeader, LOGIN_ID_HEADERS)
                || containsAlias(normalizedHeader, STATUS_HEADERS);
    }

    private AttributeColumn parseAttributeColumn(String normalizedHeader,
                                                 Map<String, AttributeColumnDefinition> presetColumns,
                                                 int col,
                                                 HeaderProbe probe) {
        String originalHeader = probe.rawHeaderByCol().getOrDefault(col, normalizedHeader);
        for (Map.Entry<String, AttributeColumnDefinition> entry : presetColumns.entrySet()) {
            if (normalizeHeader(entry.getKey()).equals(normalizedHeader)) {
                AttributeColumnDefinition def = entry.getValue();
                return new AttributeColumn(originalHeader, def.attributeKey(), def.booleanLike(), def.saveAsAttribute());
            }
        }
        if (normalizedHeader.startsWith("ATTR")) {
            String key = originalHeader.replaceFirst("(?i)attr\\s*:", "").trim();
            if (key.matches("^[a-zA-Z0-9_-]{2,100}$")) {
                return new AttributeColumn(originalHeader, key, false, true);
            }
        }
        if (normalizedHeader.startsWith("속성")) {
            String key = originalHeader.replaceFirst("속성\\s*:", "").trim();
            if (key.matches("^[a-zA-Z0-9_-]{2,100}$")) {
                return new AttributeColumn(originalHeader, key, false, true);
            }
        }
        return null;
    }

    private ParsedRow parseRow(Row row,
                               ImportContext context,
                               Map<String, Department> deptByCode,
                               Map<String, List<Department>> deptByName) {
        String employeeNumber = get(row, context.employeeNumberColumnIndex());
        String name = get(row, context.nameColumnIndex());
        String position = get(row, context.positionColumnIndex());
        String jobTitle = get(row, context.jobTitleColumnIndex());
        String email = get(row, context.emailColumnIndex());

        String loginId = context.loginIdColumnIndex() == null
                ? employeeNumber
                : get(row, context.loginIdColumnIndex());
        if (loginId.isBlank()) {
            loginId = employeeNumber;
        }

        String status = context.statusColumnIndex() == null
                ? DEFAULT_STATUS
                : normalizeStatus(get(row, context.statusColumnIndex()));
        if (status.isBlank()) {
            status = DEFAULT_STATUS;
        }

        DepartmentResolveResult deptResult = resolveDepartment(row, context, deptByCode, deptByName);
        Long departmentId = deptResult.department() == null ? null : deptResult.department().getId();

        return new ParsedRow(
                employeeNumber,
                name,
                departmentId,
                deptResult.errorMessage(),
                deptResult.matchResult(),
                position,
                jobTitle,
                email,
                loginId,
                status
        );
    }

    private DepartmentResolveResult resolveDepartment(Row row,
                                                      ImportContext context,
                                                      Map<String, Department> deptByCode,
                                                      Map<String, List<Department>> deptByName) {
        String inputCode = context.departmentCodeColumnIndex() == null ? "" : get(row, context.departmentCodeColumnIndex());
        String inputName = context.departmentNameColumnIndex() == null ? "" : get(row, context.departmentNameColumnIndex());
        String normalizedCode = inputCode.trim().toUpperCase();
        String normalizedNameKey = normalizeDepartmentNameKey(inputName);

        boolean hasCode = !normalizedCode.isBlank();
        boolean hasName = !inputName.isBlank();

        Department byCode = hasCode ? deptByCode.get(normalizedCode) : null;
        List<Department> byNameCandidates = hasName
                ? deptByName.getOrDefault(normalizedNameKey, List.of())
                : List.of();

        if (!hasCode && !hasName) {
            return DepartmentResolveResult.fail(
                    new DepartmentMatchResult(inputName, "", "", "실패"),
                    "부서코드 또는 부서명을 입력해주세요."
            );
        }

        if (hasCode && byCode == null) {
            return DepartmentResolveResult.fail(
                    new DepartmentMatchResult(inputName, normalizedCode, "", "실패"),
                    "존재하지 않는 부서코드입니다: " + normalizedCode
            );
        }

        Department resolvedByName = null;
        if (hasName) {
            if (byNameCandidates.isEmpty()) {
                return DepartmentResolveResult.fail(
                        new DepartmentMatchResult(inputName, normalizedCode, "", "실패"),
                        "존재하지 않는 부서명입니다: " + inputName.trim()
                );
            }
            if (byNameCandidates.size() > 1) {
                return DepartmentResolveResult.fail(
                        new DepartmentMatchResult(inputName, normalizedCode, "", "모호"),
                        "모호한 부서명입니다. 동일 이름 부서가 2건 이상 있습니다: " + inputName.trim()
                );
            }
            resolvedByName = byNameCandidates.get(0);
        }

        if (hasCode && hasName) {
            if (resolvedByName == null || !Objects.equals(byCode.getId(), resolvedByName.getId())) {
                String matchedCode = resolvedByName == null ? "" : resolvedByName.getCode();
                return DepartmentResolveResult.fail(
                        new DepartmentMatchResult(inputName, normalizedCode, matchedCode, "실패"),
                        "부서코드와 부서명이 서로 다른 부서를 가리킵니다."
                );
            }
            return DepartmentResolveResult.success(byCode, inputName, normalizedCode);
        }

        if (hasCode) {
            return DepartmentResolveResult.success(byCode, inputName, normalizedCode);
        }
        return DepartmentResolveResult.success(resolvedByName, inputName, normalizedCode);
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
            if (normalized.isBlank() || !column.saveAsAttribute()) {
                continue;
            }
            resolved.add(new ResolvedAttributeValue(column.columnName(), column.attributeKey(), normalized));
        }
        return hasError ? null : resolved;
    }

    private String normalizeStatus(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if (value.isBlank()) {
            return DEFAULT_STATUS;
        }
        return value;
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

    private void validateMaxRows(Long orgId, int totalRows) {
        int maxRows = organizationSettingService.resolveUploadMaxRows(orgId);
        if (totalRows > maxRows) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "업로드 가능한 최대 행 수(" + maxRows + "행)를 초과했습니다."
            );
        }
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
        Map<String, AttributeColumnDefinition> allPreset = new LinkedHashMap<>();
        allPreset.putAll(COMMON_PRESET_ATTRIBUTE_COLUMNS);
        allPreset.putAll(HOSPITAL_PRESET_ATTRIBUTE_COLUMNS);
        allPreset.putAll(AFFILIATE_PRESET_ATTRIBUTE_COLUMNS);
        allPreset.putAll(SUPPORT_COLUMNS);
        return allPreset.entrySet().stream()
                .filter(entry -> entry.getValue().attributeKey().equals(key))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(key);
    }

    private Map<String, AttributeColumnDefinition> buildPresetColumns(OrganizationType organizationType,
                                                                      OrganizationProfile organizationProfile) {
        Map<String, AttributeColumnDefinition> preset = new LinkedHashMap<>();
        preset.putAll(COMMON_PRESET_ATTRIBUTE_COLUMNS);
        if (organizationType == OrganizationType.HOSPITAL || organizationProfile == OrganizationProfile.HOSPITAL_DEFAULT) {
            preset.putAll(HOSPITAL_PRESET_ATTRIBUTE_COLUMNS);
        } else if (organizationType == OrganizationType.AFFILIATE) {
            preset.putAll(AFFILIATE_PRESET_ATTRIBUTE_COLUMNS);
        }
        preset.putAll(SUPPORT_COLUMNS);
        return preset;
    }

    private static Integer findColumn(Map<Integer, String> normalizedHeaderByCol, Set<String> aliases) {
        for (Map.Entry<Integer, String> entry : normalizedHeaderByCol.entrySet()) {
            if (containsAlias(entry.getValue(), aliases)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean containsAlias(String normalizedValue, Set<String> aliases) {
        return aliases.stream()
                .map(EmployeeUploadHandler::normalizeHeader)
                .anyMatch(alias -> normalizedValue.equals(alias) || normalizedValue.startsWith(alias));
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replaceAll("[\\s\\-_/()\\[\\]{}:]", "")
                .replaceAll("[*·•]", "")
                .trim()
                .toUpperCase();
    }

    private static String normalizeDepartmentNameKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replaceAll("\\s+", " ")
                .replace(" ", "")
                .toUpperCase();
    }

    private static String get(Row row, Integer col) {
        return col == null ? "" : ExcelUtils.getCellString(row, col).trim();
    }

    private static void addMapped(List<String> mappedColumns, String label, Integer idx) {
        if (idx != null) {
            mappedColumns.add(label + " -> col=" + (idx + 1));
        }
    }

    private String formatDepartmentMatchResult(int rowNumber, DepartmentMatchResult result) {
        String inputName = result.inputDepartmentName().isBlank() ? "-" : result.inputDepartmentName();
        String inputCode = result.inputDepartmentCode().isBlank() ? "-" : result.inputDepartmentCode();
        String matchedCode = result.matchedDepartmentCode().isBlank() ? "-" : result.matchedDepartmentCode();
        return "row=" + rowNumber
                + ", 입력 부서명=" + inputName
                + ", 입력 부서코드=" + inputCode
                + ", 매칭 부서코드=" + matchedCode
                + ", 결과=" + result.result();
    }

    private String detectedTypeByProfile(OrganizationProfile profile) {
        return switch (profile) {
            case HOSPITAL_DEFAULT -> "OPERATIONS_HOSPITAL";
            case AFFILIATE_HOSPITAL -> "OPERATIONS_AFFILIATE_HOSPITAL";
            case AFFILIATE_GENERAL -> "OPERATIONS_AFFILIATE_GENERAL";
        };
    }

    private String importProfileByMode(OrganizationProfile profile, boolean standardTemplate) {
        if (standardTemplate) {
            return "STANDARD_TEMPLATE";
        }
        return switch (profile) {
            case HOSPITAL_DEFAULT -> "OPS_HOSPITAL";
            case AFFILIATE_HOSPITAL -> "OPS_AFFILIATE_HOSPITAL";
            case AFFILIATE_GENERAL -> "OPS_AFFILIATE_GENERAL";
        };
    }

    private record AttributeColumnDefinition(String attributeKey, boolean booleanLike, boolean saveAsAttribute) {
    }

    private record AttributeColumn(String columnName, String attributeKey, boolean booleanLike, boolean saveAsAttribute) {
    }

    private record ResolvedAttributeValue(String columnName, String attributeKey, String valueText) {
    }

    private record ParsedRow(String employeeNumber,
                             String name,
                             Long departmentId,
                             String departmentErrorMessage,
                             DepartmentMatchResult departmentMatchResult,
                             String position,
                             String jobTitle,
                             String email,
                             String loginId,
                             String status) {
    }

    private record ProcessOutcome(UploadResult result, ImportContext context, List<String> departmentMatchingResults) {
    }

    private record HeaderProbe(int rowIndex,
                               int maxColumnIndex,
                               Map<Integer, String> normalizedHeaderByCol,
                               Map<Integer, String> rawHeaderByCol) {
    }

    private record ImportContext(int dataStartRowIndex,
                                 int maxColumnIndex,
                                 Integer employeeNumberColumnIndex,
                                 Integer nameColumnIndex,
                                 Integer departmentCodeColumnIndex,
                                 Integer departmentNameColumnIndex,
                                 Integer positionColumnIndex,
                                 Integer jobTitleColumnIndex,
                                 Integer emailColumnIndex,
                                 Integer loginIdColumnIndex,
                                 Integer statusColumnIndex,
                                 Map<Integer, AttributeColumn> attributeColumns,
                                 String detectedFileType,
                                 String importProfile,
                                 List<String> mappedColumns,
                                 List<String> missingRequiredColumns,
                                 List<String> plannedTransformations) {
        static ImportContext failed(String detectedFileType, String importProfile, List<String> missingRequiredColumns) {
            return new ImportContext(
                    1, 0, null, null, null, null, null, null, null, null, null,
                    Map.of(),
                    detectedFileType,
                    importProfile,
                    List.of(),
                    missingRequiredColumns,
                    List.of("파일 구조 자동 인식 실패")
            );
        }
    }

    private record DepartmentResolveResult(Department department,
                                           String errorMessage,
                                           DepartmentMatchResult matchResult) {
        static DepartmentResolveResult success(Department department, String inputName, String inputCode) {
            return new DepartmentResolveResult(
                    department,
                    null,
                    new DepartmentMatchResult(inputName, inputCode, department.getCode(), "성공")
            );
        }

        static DepartmentResolveResult fail(DepartmentMatchResult matchResult, String errorMessage) {
            return new DepartmentResolveResult(null, errorMessage, matchResult);
        }
    }

    private record DepartmentMatchResult(String inputDepartmentName,
                                         String inputDepartmentCode,
                                         String matchedDepartmentCode,
                                         String result) {
    }
}
