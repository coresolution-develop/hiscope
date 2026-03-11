package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.upload.repository.UploadHistoryRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.upload.max-rows=300"
})
class StagingReadinessRehearsalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EvaluationTemplateRepository templateRepository;

    @Autowired
    private UploadHistoryRepository uploadHistoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void 운영유사_대량데이터에서_기관격리_목록조회_리허설() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123"); // orgId=1

        Department org1Dept = resolveOrCreateDepartment(1L, "STG_ORG1");
        Department org2Dept = resolveOrCreateDepartment(2L, "STG_ORG2");

        seedEmployeesIfNeeded(1L, org1Dept.getId(), "LOAD_ORG1_", 1200);
        seedEmployeesIfNeeded(2L, org2Dept.getId(), "LEAK_ORG2_", 1200);

        long startedAt = System.currentTimeMillis();
        String html = mockMvc.perform(get("/admin/employees")
                        .session(adminSession)
                        .param("page", "0")
                        .param("size", "200")
                        .param("keyword", "LOAD_ORG1_"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long elapsedMs = System.currentTimeMillis() - startedAt;

        assertThat(html).contains("LOAD_ORG1_");
        assertThat(html).doesNotContain("LEAK_ORG2_");
        assertThat(elapsedMs).isLessThan(3000L);
    }

    @Test
    void 업로드_행수초과시_실패이력과_감사로그가_남는다() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "departments_over_limit.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentExcel(301)
        );

        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        var latestHistory = uploadHistoryRepository.findByOrganizationIdAndUploadTypeOrderByCreatedAtDesc(1L, "DEPARTMENT")
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(latestHistory.getStatus()).isEqualTo("FAILED");
        assertThat(latestHistory.getErrorDetail()).contains("최대 행 수");

        var latestAudit = auditLogRepository.findAll().stream()
                .filter(log -> "DEPT_UPLOAD".equals(log.getAction()) && "FAIL".equals(log.getOutcome()))
                .max(Comparator.comparing(log -> log.getId()))
                .orElseThrow();
        assertThat(latestAudit.getRequestId()).isNotBlank();
        assertThat(latestAudit.getOrganizationId()).isEqualTo(1L);
    }

    @Test
    void 문항업로드_실패시에도_이력이_기록된다() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(1L)
                .name("staging-template-" + suffix)
                .description("staging rehearsal")
                .active(true)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid_questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createInvalidQuestionExcel()
        );

        mockMvc.perform(multipart("/admin/evaluation/templates/{id}/questions/upload", template.getId())
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        var latestHistory = uploadHistoryRepository.findByOrganizationIdAndUploadTypeOrderByCreatedAtDesc(1L, "QUESTION")
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(latestHistory.getStatus()).isEqualTo("FAILED");
        assertThat(latestHistory.getErrorDetail()).contains("문항유형");
    }

    @Test
    void 업로드_실패내역_CSV_다운로드_가능() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "departments_over_limit_again.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentExcel(301)
        );

        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        Long historyId = uploadHistoryRepository.findByOrganizationIdAndUploadTypeOrderByCreatedAtDesc(1L, "DEPARTMENT")
                .stream()
                .findFirst()
                .orElseThrow()
                .getId();

        String csv = mockMvc.perform(get("/admin/uploads/history/{id}/errors.csv", historyId)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"upload_errors_" + historyId + ".csv\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("row_number,column,message");
        assertThat(csv).contains("최대 행 수");
    }

    @Test
    void 업로드이력_검색필터_쿼리파라미터_동작() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String fileName = "departments_filter_" + UUID.randomUUID().toString().substring(0, 6) + ".xlsx";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentExcel(301)
        );

        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String html = mockMvc.perform(get("/admin/uploads/history")
                        .session(adminSession)
                        .param("uploadType", "DEPARTMENT")
                        .param("status", "FAILED")
                        .param("keyword", "departments_filter_"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains(fileName);
    }

    private MockHttpSession loginAs(String loginId, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login")
                        .user("loginId", loginId)
                        .password("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private void seedEmployeesIfNeeded(Long orgId, Long deptId, String prefix, int count) {
        long existing = employeeRepository.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(e -> e.getEmployeeNumber() != null && e.getEmployeeNumber().startsWith(prefix))
                .count();
        if (existing >= count) {
            return;
        }

        List<Employee> toSave = new ArrayList<>();
        for (int i = (int) existing + 1; i <= count; i++) {
            String no = prefix + String.format("%04d", i);
            toSave.add(Employee.builder()
                    .organizationId(orgId)
                    .departmentId(deptId)
                    .name(no)
                    .employeeNumber(no)
                    .position("사원")
                    .jobTitle("팀원")
                    .email(no.toLowerCase() + "@staging.local")
                    .status("ACTIVE")
                    .build());
        }
        employeeRepository.saveAll(toSave);
    }

    private Department resolveOrCreateDepartment(Long orgId, String code) {
        return departmentRepository.findByOrganizationIdAndCode(orgId, code)
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .organizationId(orgId)
                        .code(code)
                        .name("Staging-" + code)
                        .active(true)
                        .build()));
    }

    private byte[] createDepartmentExcel(int rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("departments");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("부서코드");
            header.createCell(1).setCellValue("부서명");
            header.createCell(2).setCellValue("상위부서코드");

            for (int i = 1; i <= rows; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("DEP" + String.format("%04d", i));
                row.createCell(1).setCellValue("부서-" + i);
                row.createCell(2).setCellValue("");
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createInvalidQuestionExcel() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("questions");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("카테고리");
            header.createCell(1).setCellValue("문항내용");
            header.createCell(2).setCellValue("문항유형");
            header.createCell(3).setCellValue("최대점수");
            header.createCell(4).setCellValue("정렬순서");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("역량");
            row.createCell(1).setCellValue("잘못된 문항 타입");
            row.createCell(2).setCellValue("INVALID_TYPE");
            row.createCell(3).setCellValue("5");
            row.createCell(4).setCellValue("1");

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
