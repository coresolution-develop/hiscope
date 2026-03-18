package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.upload.handler.DepartmentUploadHandler;
import com.hiscope.evaluation.domain.upload.handler.QuestionUploadHandler;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImportPolicyCompatibilityIntegrationTest {

    private static EmbeddedPostgres embeddedPostgres;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentUploadHandler departmentUploadHandler;

    @Autowired
    private QuestionUploadHandler questionUploadHandler;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EvaluationTemplateRepository templateRepository;

    @Autowired
    private EvaluationQuestionRepository questionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        EmbeddedPostgres pg = postgres();
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/common,classpath:db/migration/local");
        registry.add("spring.thymeleaf.cache", () -> false);
        registry.add("app.bootstrap.super-admin.enabled", () -> false);
        registry.add("logging.level.root", () -> "WARN");
    }

    @BeforeEach
    void setupAuthContext() {
        var principal = com.hiscope.evaluation.common.security.CustomUserDetails.builder()
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

    @AfterAll
    static void shutdown() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @Test
    void 부서_운영파일_BC포맷_루트등록_호환() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dept-ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsDepartmentWorkbook()
        );

        var result = departmentUploadHandler.handle(1L, file);
        assertThat(result.getSuccessRows()).isEqualTo(2);

        Department dept = departmentRepository.findByOrganizationIdAndCode(1L, "OPS_A").orElseThrow();
        assertThat(dept.getParentId()).isNull();
    }

    @Test
    void 문제은행_AA_AB_문항군_코드_저장_호환() throws Exception {
        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(1L)
                .name("ops-bank-template-" + System.nanoTime())
                .description("ops")
                .active(true)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question-ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsQuestionWorkbook()
        );

        var result = questionUploadHandler.handle(1L, template.getId(), file);
        assertThat(result.getSuccessRows()).isEqualTo(2);

        var questions = questionRepository.findByTemplateIdOrderBySortOrderAsc(template.getId());
        assertThat(questions).extracting("questionGroupCode").containsExactly("AA", "AB");
        assertThat(questions).extracting("questionType").containsExactly("SCALE", "DESCRIPTIVE");
    }

    @Test
    void 로그인ID_기관컨텍스트_중복허용_로그인분기() throws Exception {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        String sharedLogin = "same" + suffix;

        Department org1Dept = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("org1-login-" + suffix)
                .code("L1" + suffix)
                .active(true)
                .build());
        Department org2Dept = departmentRepository.save(Department.builder()
                .organizationId(2L)
                .name("org2-login-" + suffix)
                .code("L2" + suffix)
                .active(true)
                .build());

        Employee org1Emp = employeeRepository.save(Employee.builder()
                .organizationId(1L).departmentId(org1Dept.getId()).name("org1-user")
                .employeeNumber("ORG1-" + suffix).position("사원").jobTitle("팀원").status("ACTIVE").build());
        Employee org2Emp = employeeRepository.save(Employee.builder()
                .organizationId(2L).departmentId(org2Dept.getId()).name("org2-user")
                .employeeNumber("ORG2-" + suffix).position("사원").jobTitle("팀원").status("ACTIVE").build());

        userAccountRepository.save(UserAccount.builder()
                .employee(org1Emp).organizationId(1L).loginId(sharedLogin)
                .passwordHash(passwordEncoder.encode("password123")).role("ROLE_USER").build());
        userAccountRepository.save(UserAccount.builder()
                .employee(org2Emp).organizationId(2L).loginId(sharedLogin)
                .passwordHash(passwordEncoder.encode("password123")).role("ROLE_USER").build());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("orgCode", "KIDR")
                        .param("loginId", sharedLogin)
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/dashboard"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginId", sharedLogin)
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?error=*"));
    }

    private byte[] createOpsDepartmentWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("부서템플릿");
            sheet.createRow(0).createCell(1).setCellValue("샘플 안내");
            var header = sheet.createRow(1);
            header.createCell(1).setCellValue("부서명");
            header.createCell(2).setCellValue("부서코드");
            var row1 = sheet.createRow(2);
            row1.createCell(1).setCellValue("운영부서A");
            row1.createCell(2).setCellValue("OPS_A");
            var row2 = sheet.createRow(3);
            row2.createCell(1).setCellValue("운영부서B");
            row2.createCell(2).setCellValue("OPS_B");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createOpsQuestionWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("문제은행 템플릿");
            var header = sheet.createRow(1);
            header.createCell(1).setCellValue("문제");
            header.createCell(2).setCellValue("문제유형");
            header.createCell(3).setCellValue("구분");

            var row1 = sheet.createRow(2);
            row1.createCell(1).setCellValue("리더십 문항");
            row1.createCell(2).setCellValue("AA");
            row1.createCell(3).setCellValue("섬김");

            var row2 = sheet.createRow(3);
            row2.createCell(1).setCellValue("종합평가(※ 의견을 기재해주세요)");
            row2.createCell(2).setCellValue("AB");
            row2.createCell(3).setCellValue("주관식");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private static synchronized EmbeddedPostgres postgres() {
        if (embeddedPostgres == null) {
            try {
                embeddedPostgres = EmbeddedPostgres.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start embedded postgres", e);
            }
        }
        return embeddedPostgres;
    }
}
