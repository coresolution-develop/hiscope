package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.common.security.CustomUserDetails;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.upload.handler.EmployeeUploadHandler;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeUploadAutoDetectIntegrationTest {

    @Autowired
    private EmployeeUploadHandler employeeUploadHandler;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private EmployeeAttributeRepository employeeAttributeRepository;
    @Autowired
    private EmployeeAttributeValueRepository employeeAttributeValueRepository;

    @BeforeEach
    void setupAuthContext() {
        CustomUserDetails principal = CustomUserDetails.builder()
                .id(1L)
                .loginId("super")
                .password("noop")
                .organizationId(null)
                .employeeId(null)
                .role("ROLE_SUPER_ADMIN")
                .name("슈퍼관리자")
                .mustChangePassword(false)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 병원_운영파일_자동인식_테스트() throws Exception {
        Organization org = createOrg("OPS_HOSP_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("간호부").code("NURSE").active(true).build());
        MockMultipartFile file = new MockMultipartFile("file", "hospital_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsWorkbook(true, false, false, "1001", "홍길동", "간호부", "Y", "N", "Y", "TRUE"));

        var preview = employeeUploadHandler.preview(org.getId(), file);

        assertThat(preview.getDetectedFileType()).isEqualTo("OPERATIONS_HOSPITAL");
        assertThat(preview.getImportProfile()).isEqualTo("OPS_HOSPITAL");
        assertThat(preview.isUploadable()).isTrue();
    }

    @Test
    void 병원계열_계열사_운영파일_자동인식_테스트() throws Exception {
        Organization org = createOrg("OPS_AFH_", OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_HOSPITAL);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("영업팀").code("SALES").active(true).build());
        MockMultipartFile file = new MockMultipartFile("file", "affiliate_hospital_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsWorkbook(false, true, false, "2001", "김철수", "영업팀", "N", "Y", "N", "1"));

        var preview = employeeUploadHandler.preview(org.getId(), file);

        assertThat(preview.getDetectedFileType()).isEqualTo("OPERATIONS_AFFILIATE_HOSPITAL");
        assertThat(preview.getImportProfile()).isEqualTo("OPS_AFFILIATE_HOSPITAL");
        assertThat(preview.isUploadable()).isTrue();
    }

    @Test
    void 일반_계열사_운영파일_자동인식_테스트() throws Exception {
        Organization org = createOrg("OPS_AFG_", OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_GENERAL);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("지원팀").code("SUPPORT").active(true).build());
        MockMultipartFile file = new MockMultipartFile("file", "affiliate_general_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsWorkbook(false, false, true, "3001", "이영희", "지원팀", "N", "N", "N", "FALSE"));

        var preview = employeeUploadHandler.preview(org.getId(), file);

        assertThat(preview.getDetectedFileType()).isEqualTo("OPERATIONS_AFFILIATE_GENERAL");
        assertThat(preview.getImportProfile()).isEqualTo("OPS_AFFILIATE_GENERAL");
        assertThat(preview.isUploadable()).isTrue();
    }

    @Test
    void login_id_사번_자동생성_상태_ACTIVE_기본값_테스트() throws Exception {
        Organization org = createOrg("OPS_LOGIN_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("원무과").code("ADMIN").active(true).build());
        MockMultipartFile file = new MockMultipartFile("file", "hospital_ops_upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsWorkbook(true, false, false, "4001", "최민수", "원무과", "N", "N", "N", "0"));

        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getStatus()).isIn("SUCCESS", "PARTIAL");
        var employee = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> "4001".equals(e.getEmployeeNumber()))
                .findFirst().orElseThrow();
        assertThat(employee.getStatus()).isEqualTo("ACTIVE");
        var account = userAccountRepository.findByOrganizationIdAndLoginId(org.getId(), "4001").orElseThrow();
        assertThat(account.getEmployee().getId()).isEqualTo(employee.getId());
    }

    @Test
    void boolean_변환_테스트() throws Exception {
        Organization org = createOrg("OPS_BOOL_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("검진센터").code("CHK").active(true).build());
        MockMultipartFile file = new MockMultipartFile("file", "hospital_bool_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsWorkbook(true, false, false, "5001", "박지훈", "검진센터", "TRUE", "0", "1", "FALSE"));

        var result = employeeUploadHandler.handle(org.getId(), file);
        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getStatus()).isIn("SUCCESS", "PARTIAL");

        var employee = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> "5001".equals(e.getEmployeeNumber()))
                .findFirst().orElseThrow();
        var attrMap = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(org.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getAttributeKey(), a -> a.getId()));
        String institutionHead = employeeAttributeValueRepository.findByEmployeeIdAndAttributeId(employee.getId(), attrMap.get("institution_head"))
                .orElseThrow().getValueText();
        String unitHead = employeeAttributeValueRepository.findByEmployeeIdAndAttributeId(employee.getId(), attrMap.get("unit_head"))
                .orElseThrow().getValueText();
        String departmentHead = employeeAttributeValueRepository.findByEmployeeIdAndAttributeId(employee.getId(), attrMap.get("department_head"))
                .orElseThrow().getValueText();
        String excluded = employeeAttributeValueRepository.findByEmployeeIdAndAttributeId(employee.getId(), attrMap.get("evaluation_excluded"))
                .orElseThrow().getValueText();
        assertThat(institutionHead).isEqualTo("Y");
        assertThat(unitHead).isEqualTo("N");
        assertThat(departmentHead).isEqualTo("Y");
        assertThat(excluded).isEqualTo("N");
    }

    @Test
    void 잘못된_운영파일_형식_업로드_실패_테스트() throws Exception {
        Organization org = createOrg("OPS_BAD_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        MockMultipartFile file = new MockMultipartFile("file", "invalid_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createInvalidWorkbook());

        var preview = employeeUploadHandler.preview(org.getId(), file);
        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(preview.isUploadable()).isFalse();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void 부서명만으로_정상_매칭_테스트() throws Exception {
        Organization org = createOrg("OPS_DN_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        Department dept = departmentRepository.save(Department.builder().organizationId(org.getId()).name("간호부").code("NURSE").active(true).build());

        MockMultipartFile file = new MockMultipartFile("file", "dept_name_only.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardWorkbook(null, "간호부", "6001", "부서명매칭"));

        var preview = employeeUploadHandler.preview(org.getId(), file);
        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(preview.getDepartmentMatchingResults()).anyMatch(v -> v.contains("입력 부서명=간호부") && v.contains("매칭 부서코드=NURSE") && v.contains("결과=성공"));
        assertThat(result.getStatus()).isIn("SUCCESS", "PARTIAL");
        assertThat(result.getSuccessRows()).isEqualTo(1);
        var saved = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> "6001".equals(e.getEmployeeNumber()))
                .findFirst().orElseThrow();
        assertThat(saved.getDepartmentId()).isEqualTo(dept.getId());
    }

    @Test
    void 부서코드만으로_정상_매칭_테스트() throws Exception {
        Organization org = createOrg("OPS_DC_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        Department dept = departmentRepository.save(Department.builder().organizationId(org.getId()).name("원무과").code("ADMIN").active(true).build());

        MockMultipartFile file = new MockMultipartFile("file", "dept_code_only.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardWorkbook("ADMIN", null, "6002", "코드매칭"));

        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(result.getStatus()).isIn("SUCCESS", "PARTIAL");
        assertThat(result.getSuccessRows()).isEqualTo(1);
        var saved = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> "6002".equals(e.getEmployeeNumber()))
                .findFirst().orElseThrow();
        assertThat(saved.getDepartmentId()).isEqualTo(dept.getId());
    }

    @Test
    void 부서코드와_부서명_일치_정상_매칭_테스트() throws Exception {
        Organization org = createOrg("OPS_DCM_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        Department dept = departmentRepository.save(Department.builder().organizationId(org.getId()).name("검진센터").code("CHK").active(true).build());

        MockMultipartFile file = new MockMultipartFile("file", "dept_code_name_match.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardWorkbook("CHK", "검진센터", "6003", "일치매칭"));

        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(result.getStatus()).isIn("SUCCESS", "PARTIAL");
        assertThat(result.getSuccessRows()).isEqualTo(1);
        var saved = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> "6003".equals(e.getEmployeeNumber()))
                .findFirst().orElseThrow();
        assertThat(saved.getDepartmentId()).isEqualTo(dept.getId());
    }

    @Test
    void 부서코드와_부서명_불일치_오류_테스트() throws Exception {
        Organization org = createOrg("OPS_DCX_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("간호부").code("NURSE").active(true).build());
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("원무과").code("ADMIN").active(true).build());

        MockMultipartFile file = new MockMultipartFile("file", "dept_mismatch.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardWorkbook("NURSE", "원무과", "6004", "불일치"));

        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrors()).anySatisfy(e ->
                assertThat(e.getMessage()).contains("부서코드와 부서명이 서로 다른 부서를 가리킵니다."));
    }

    @Test
    void 존재하지_않는_부서명_오류_테스트() throws Exception {
        Organization org = createOrg("OPS_DNF_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("간호부").code("NURSE").active(true).build());

        MockMultipartFile file = new MockMultipartFile("file", "dept_name_not_found.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardWorkbook(null, "없는부서", "6005", "미존재"));

        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrors()).anySatisfy(e ->
                assertThat(e.getMessage()).contains("존재하지 않는 부서명입니다"));
    }

    @Test
    void 중복_부서명_모호_오류_테스트() throws Exception {
        Organization org = createOrg("OPS_DAM_", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("중복부서").code("DUP1").active(true).build());
        departmentRepository.save(Department.builder().organizationId(org.getId()).name("중복 부서").code("DUP2").active(true).build());

        MockMultipartFile file = new MockMultipartFile("file", "dept_name_ambiguous.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardWorkbook(null, "중복부서", "6006", "모호"));

        var result = employeeUploadHandler.handle(org.getId(), file);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrors()).anySatisfy(e ->
                assertThat(e.getMessage()).contains("모호한 부서명입니다"));
    }

    private Organization createOrg(String codePrefix, OrganizationType type, OrganizationProfile profile) {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        return organizationRepository.save(Organization.builder()
                .name(codePrefix + suffix)
                .code(codePrefix + suffix)
                .status("ACTIVE")
                .organizationType(type)
                .organizationProfile(profile)
                .build());
    }

    private byte[] createOpsWorkbook(boolean hospitalCols,
                                     boolean affiliateHospitalCols,
                                     boolean affiliateGeneralCols,
                                     String empNo,
                                     String name,
                                     String deptName,
                                     String institutionHead,
                                     String unitHead,
                                     String departmentHead,
                                     String excluded) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("직원명부");
            var header = sheet.createRow(1);
            int c = 1; // B열 시작
            header.createCell(c++).setCellValue("기관명");
            header.createCell(c++).setCellValue("소속기관");
            header.createCell(c++).setCellValue("부서명");
            header.createCell(c++).setCellValue("사원번호");
            header.createCell(c++).setCellValue("직책");
            header.createCell(c++).setCellValue("이름");
            header.createCell(c++).setCellValue("입사일");
            header.createCell(c++).setCellValue("퇴사일");
            header.createCell(c++).setCellValue("핸드폰번호");
            header.createCell(c++).setCellValue("기관장여부");
            header.createCell(c++).setCellValue("소속장여부");
            header.createCell(c++).setCellValue("부서장여부");
            header.createCell(c++).setCellValue("평가제외여부");
            if (hospitalCols) {
                header.createCell(c++).setCellValue("경혁팀장여부");
                header.createCell(c++).setCellValue("경혁팀여부");
                header.createCell(c++).setCellValue("1인부서여부");
                header.createCell(c++).setCellValue("의료리더여부");
            }
            if (affiliateHospitalCols || affiliateGeneralCols) {
                header.createCell(c++).setCellValue("평가대상여부");
            }

            var row = sheet.createRow(2);
            c = 1;
            row.createCell(c++).setCellValue("기관A");
            row.createCell(c++).setCellValue("소속A");
            row.createCell(c++).setCellValue(deptName);
            row.createCell(c++).setCellValue(empNo);
            row.createCell(c++).setCellValue("팀원");
            row.createCell(c++).setCellValue(name);
            row.createCell(c++).setCellValue("2020-01-01");
            row.createCell(c++).setCellValue("");
            row.createCell(c++).setCellValue("010-1234-5678");
            row.createCell(c++).setCellValue(institutionHead);
            row.createCell(c++).setCellValue(unitHead);
            row.createCell(c++).setCellValue(departmentHead);
            row.createCell(c++).setCellValue(excluded);
            if (hospitalCols) {
                row.createCell(c++).setCellValue("N");
                row.createCell(c++).setCellValue("Y");
                row.createCell(c++).setCellValue("N");
                row.createCell(c++).setCellValue("0");
            }
            if (affiliateHospitalCols || affiliateGeneralCols) {
                row.createCell(c++).setCellValue("Y");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createInvalidWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("invalid");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("잘못된컬럼");
            header.createCell(1).setCellValue("기타");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("x");
            row.createCell(1).setCellValue("y");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createStandardWorkbook(String deptCode, String deptName, String empNo, String name) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("직원업로드");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("이름");
            header.createCell(1).setCellValue("사원번호");
            header.createCell(2).setCellValue("부서코드");
            header.createCell(3).setCellValue("부서명");
            header.createCell(4).setCellValue("직위");
            header.createCell(5).setCellValue("직책");
            header.createCell(6).setCellValue("이메일");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(name);
            row.createCell(1).setCellValue(empNo);
            row.createCell(2).setCellValue(deptCode == null ? "" : deptCode);
            row.createCell(3).setCellValue(deptName == null ? "" : deptName);
            row.createCell(4).setCellValue("사원");
            row.createCell(5).setCellValue("팀원");
            row.createCell(6).setCellValue(empNo + "@example.com");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}
