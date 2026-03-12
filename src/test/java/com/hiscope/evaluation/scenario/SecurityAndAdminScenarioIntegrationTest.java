package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.settings.entity.OrganizationSetting;
import com.hiscope.evaluation.domain.settings.repository.OrganizationSettingRepository;
import com.hiscope.evaluation.domain.settings.service.OrganizationSettingService;
import com.hiscope.evaluation.domain.upload.entity.UploadHistory;
import com.hiscope.evaluation.domain.upload.repository.UploadHistoryRepository;
import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndAdminScenarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EvaluationTemplateRepository templateRepository;

    @Autowired
    private EvaluationSessionRepository sessionRepository;

    @Autowired
    private EvaluationAssignmentRepository assignmentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UploadHistoryRepository uploadHistoryRepository;

    @Autowired
    private OrganizationSettingRepository organizationSettingRepository;

    @Test
    void 로그인_및_권한_검증() throws Exception {
        mockMvc.perform(formLogin("/login")
                        .user("loginId", "super")
                        .password("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/dashboard"));

        mockMvc.perform(formLogin("/login")
                        .user("loginId", "admin")
                        .password("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        mockMvc.perform(formLogin("/login")
                        .user("loginId", "emp001")
                        .password("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/dashboard"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        MockHttpSession userSession = loginAs("emp001", "password123");
        mockMvc.perform(get("/admin/dashboard").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void 최고관리자_기관_생성_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();
        String code = "ORG" + suffix.toUpperCase();

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", "테스트기관-" + suffix)
                        .param("code", code))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations"));

        assertThat(organizationRepository.findByCode(code)).isPresent();
    }

    @Test
    void 기관관리자_생성_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        Organization org = organizationRepository.save(Organization.builder()
                .name("관리자생성기관-" + suffix)
                .code("ADM" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());

        String loginId = "orgadm_" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "기관관리자-" + suffix)
                        .param("loginId", loginId)
                        .param("password", "password123")
                        .param("email", loginId + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account created = accountRepository.findByLoginId(loginId).orElseThrow();
        assertThat(created.getRole()).isEqualTo("ROLE_ORG_ADMIN");
        assertThat(created.getOrganizationId()).isEqualTo(org.getId());
    }

    @Test
    void 슈퍼관리자_기관생성후_관리자추가_연계_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();
        String orgCode = "LNK" + suffix.toUpperCase();

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", "연계기관-" + suffix)
                        .param("code", orgCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations"));

        Organization createdOrg = organizationRepository.findByCode(orgCode).orElseThrow();
        String loginId = "linkadm_" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", createdOrg.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "연계관리자-" + suffix)
                        .param("loginId", loginId)
                        .param("password", "password123")
                        .param("email", loginId + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + createdOrg.getId()));

        Account createdAdmin = accountRepository.findByLoginId(loginId).orElseThrow();
        assertThat(createdAdmin.getOrganizationId()).isEqualTo(createdOrg.getId());
        assertThat(createdAdmin.getRole()).isEqualTo("ROLE_ORG_ADMIN");

        String detailHtml = mockMvc.perform(get("/super-admin/organizations/{id}", createdOrg.getId())
                        .session(superSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(detailHtml).contains(loginId);
    }

    @Test
    void 슈퍼관리자_기관관리자생성_비밀번호정책위반_처리_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        Organization org = organizationRepository.save(Organization.builder()
                .name("정책기관-" + suffix)
                .code("POL" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());

        upsertOrgSetting(org.getId(), OrganizationSettingService.KEY_PASSWORD_MIN_LENGTH, "12");

        String loginId = "poladm_" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "정책관리자-" + suffix)
                        .param("loginId", loginId)
                        .param("password", "short123")
                        .param("email", loginId + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()))
                .andExpect(flash().attributeExists("errorMessage"));

        assertThat(accountRepository.findByLoginId(loginId)).isEmpty();
    }

    @Test
    void 기관관리자_수정_상태변경_비밀번호초기화_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        Organization org = organizationRepository.save(Organization.builder()
                .name("관리자관리기관-" + suffix)
                .code("MNG" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());

        String loginId = "orgmgr_" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "관리자원본-" + suffix)
                        .param("loginId", loginId)
                        .param("password", "password123")
                        .param("email", loginId + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account created = accountRepository.findByLoginId(loginId).orElseThrow();
        String previousPasswordHash = created.getPasswordHash();

        mockMvc.perform(post("/super-admin/organizations/{orgId}/admins/{adminId}/update", org.getId(), created.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "관리자수정-" + suffix)
                        .param("email", "updated-" + suffix + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account updated = accountRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("관리자수정-" + suffix);
        assertThat(updated.getEmail()).isEqualTo("updated-" + suffix + "@test.local");

        mockMvc.perform(post("/super-admin/organizations/{orgId}/admins/{adminId}/status", org.getId(), created.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("status", "INACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account inactivated = accountRepository.findById(created.getId()).orElseThrow();
        assertThat(inactivated.getStatus()).isEqualTo("INACTIVE");

        mockMvc.perform(post("/super-admin/organizations/{orgId}/admins/{adminId}/reset-password", org.getId(), created.getId())
                        .session(superSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account resetAccount = accountRepository.findById(created.getId()).orElseThrow();
        assertThat(resetAccount.getPasswordHash()).isNotEqualTo(previousPasswordHash);
        assertThat(resetAccount.isMustChangePassword()).isTrue();
    }

    @Test
    void 임시비밀번호_로그인시_비밀번호변경_화면으로_강제이동_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        Organization org = organizationRepository.save(Organization.builder()
                .name("임시비번기관-" + suffix)
                .code("TMP" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());

        String loginId = "tmpadm_" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "임시관리자-" + suffix)
                        .param("loginId", loginId)
                        .param("password", "password123")
                        .param("email", loginId + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account created = accountRepository.findByLoginId(loginId).orElseThrow();
        String temporaryPassword = resetPasswordAndExtractTemporaryPassword(superSession, org.getId(), created.getId());

        MvcResult loginResult = mockMvc.perform(formLogin("/login")
                        .user("loginId", loginId)
                        .password("password", temporaryPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/change-password"))
                .andReturn();

        MockHttpSession tempSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/admin/dashboard").session(tempSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/change-password"));
    }

    @Test
    void 비밀번호변경_완료후_일반접근_허용_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        Organization org = organizationRepository.save(Organization.builder()
                .name("변경완료기관-" + suffix)
                .code("PWD" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());

        String loginId = "pwdadm_" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "변경관리자-" + suffix)
                        .param("loginId", loginId)
                        .param("password", "password123")
                        .param("email", loginId + "@test.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account created = accountRepository.findByLoginId(loginId).orElseThrow();
        String temporaryPassword = resetPasswordAndExtractTemporaryPassword(superSession, org.getId(), created.getId());

        MvcResult loginResult = mockMvc.perform(formLogin("/login")
                        .user("loginId", loginId)
                        .password("password", temporaryPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/change-password"))
                .andReturn();
        MockHttpSession tempSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/auth/change-password")
                        .session(tempSession)
                        .with(csrf())
                        .param("newPassword", "newPassword123!")
                        .param("confirmPassword", "newPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        Account changed = accountRepository.findById(created.getId()).orElseThrow();
        assertThat(changed.isMustChangePassword()).isFalse();

        mockMvc.perform(get("/admin/dashboard").session(tempSession))
                .andExpect(status().isOk());
    }

    @Test
    void 부서_템플릿_다운로드_성공_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        mockMvc.perform(get("/admin/uploads/template/departments").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void 하위부서가_있으면_삭제_불가_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        Department parent = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("상위부서-" + suffix)
                .code("P" + suffix.toUpperCase())
                .active(true)
                .build());
        departmentRepository.save(Department.builder()
                .organizationId(1L)
                .parentId(parent.getId())
                .name("하위부서-" + suffix)
                .code("C" + suffix.toUpperCase())
                .active(true)
                .build());

        mockMvc.perform(post("/admin/departments/{id}/delete", parent.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"))
                .andExpect(flash().attribute("errorMessage",
                        "하위 부서가 존재합니다. 하위 부서를 먼저 정리한 후 비활성화해주세요."));

        Department stillActive = departmentRepository.findById(parent.getId()).orElseThrow();
        assertThat(stillActive.isActive()).isTrue();
    }

    @Test
    void 직원이_있으면_삭제_불가_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        Department dept = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("직원존재부서-" + suffix)
                .code("E" + suffix.toUpperCase())
                .active(true)
                .build());
        employeeRepository.save(Employee.builder()
                .organizationId(1L)
                .departmentId(dept.getId())
                .name("소속직원-" + suffix)
                .employeeNumber("EMP-DEL-" + suffix)
                .position("대리")
                .jobTitle("팀원")
                .email("emp-del-" + suffix + "@test.local")
                .status("ACTIVE")
                .build());

        mockMvc.perform(post("/admin/departments/{id}/delete", dept.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"))
                .andExpect(flash().attribute("errorMessage", "직원이 배정된 부서는 삭제할 수 없습니다."));

        Department stillActive = departmentRepository.findById(dept.getId()).orElseThrow();
        assertThat(stillActive.isActive()).isTrue();
    }

    @Test
    void 삭제가능_조건이면_부서_비활성화_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        Department dept = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("삭제가능부서-" + suffix)
                .code("D" + suffix.toUpperCase())
                .active(true)
                .build());

        mockMvc.perform(post("/admin/departments/{id}/delete", dept.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"))
                .andExpect(flash().attribute("successMessage", "부서가 비활성화되었습니다."));

        Department deactivated = departmentRepository.findById(dept.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void 부서업로드_CSRF포함_정상요청_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        MockMultipartFile file = createDepartmentUploadFile("UP" + suffix.toUpperCase(), "업로드부서-" + suffix, "");

        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"))
                .andExpect(flash().attributeExists("uploadResult"));
    }

    @Test
    void 부서업로드_CSRF실패시_403_처리_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        MockMultipartFile file = createDepartmentUploadFile("CF" + suffix.toUpperCase(), "CSRF부서-" + suffix, "");

        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(file)
                        .session(adminSession)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 업로드_화면들_CSRF_토큰_렌더링_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        String deptHtml = mockMvc.perform(get("/admin/departments").session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(deptHtml).contains("name=\"_csrf\"");
        assertThat(deptHtml).contains("/admin/uploads/departments");

        String empHtml = mockMvc.perform(get("/admin/employees").session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(empHtml).contains("name=\"_csrf\"");
        assertThat(empHtml).contains("/admin/uploads/employees");

        String questionHtml = mockMvc.perform(get("/admin/evaluation/templates/{id}/questions", 1L).session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(questionHtml).contains("name=\"_csrf\"");
        assertThat(questionHtml).contains("/admin/evaluation/templates/1/questions/upload");
    }

    @Test
    void 기관관리자_직원생성수정_비밀번호정책위반_처리_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        upsertOrgSetting(1L, OrganizationSettingService.KEY_PASSWORD_MIN_LENGTH, "12");

        Department department = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("정책부서-" + suffix)
                .code("PD" + suffix.toUpperCase())
                .active(true)
                .build());

        String shortLoginId = "emp_short_" + suffix;
        mockMvc.perform(post("/admin/employees")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "정책직원-짧은비번-" + suffix)
                        .param("employeeNumber", "POL-SHORT-" + suffix)
                        .param("departmentId", String.valueOf(department.getId()))
                        .param("position", "대리")
                        .param("jobTitle", "구성원")
                        .param("email", "short-" + suffix + "@test.local")
                        .param("loginId", shortLoginId)
                        .param("password", "short123")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/employees**"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(employeeRepository.existsByOrganizationIdAndEmployeeNumber(1L, "POL-SHORT-" + suffix)).isFalse();

        String validLoginId = "emp_valid_" + suffix;
        mockMvc.perform(post("/admin/employees")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "정책직원-정상-" + suffix)
                        .param("employeeNumber", "POL-VALID-" + suffix)
                        .param("departmentId", String.valueOf(department.getId()))
                        .param("position", "사원")
                        .param("jobTitle", "구성원")
                        .param("email", "valid-" + suffix + "@test.local")
                        .param("loginId", validLoginId)
                        .param("password", "password12345")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/employees**"));

        Employee created = employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(emp -> ("POL-VALID-" + suffix).equals(emp.getEmployeeNumber()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/admin/employees/{id}", created.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("name", created.getName())
                        .param("employeeNumber", created.getEmployeeNumber())
                        .param("departmentId", String.valueOf(department.getId()))
                        .param("position", "주임")
                        .param("jobTitle", "구성원")
                        .param("email", "updated-" + suffix + "@test.local")
                        .param("loginId", validLoginId)
                        .param("password", "short123")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/employees**"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void 기관별_데이터_격리_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123"); // orgId=1
        String suffix = uniqueSuffix();

        Department foreignDept = departmentRepository.save(Department.builder()
                .organizationId(2L)
                .name("외부부서-" + suffix)
                .code("FD" + suffix.toUpperCase())
                .active(true)
                .build());

        employeeRepository.save(Employee.builder()
                .organizationId(2L)
                .departmentId(foreignDept.getId())
                .name("외부직원-" + suffix)
                .employeeNumber("FEMP-" + suffix)
                .position("대리")
                .jobTitle("팀원")
                .email("foreign-" + suffix + "@test.local")
                .status("ACTIVE")
                .build());

        EvaluationTemplate foreignTemplate = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(2L)
                .name("외부템플릿-" + suffix)
                .description("org2 template")
                .active(true)
                .build());

        EvaluationSession foreignSession = sessionRepository.save(EvaluationSession.builder()
                .organizationId(2L)
                .name("외부세션-" + suffix)
                .description("org2 session")
                .status("PENDING")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .templateId(foreignTemplate.getId())
                .allowResubmit(false)
                .createdBy(2L)
                .build());

        mockMvc.perform(get("/admin/evaluation/sessions/{id}", foreignSession.getId())
                        .session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("errorCode", "SESSION_NOT_FOUND"));
    }

    @Test
    void 기관목록_검색_상태필터_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        organizationRepository.save(Organization.builder()
                .name("검색대상활성기관-" + suffix)
                .code("SEA" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());
        organizationRepository.save(Organization.builder()
                .name("검색대상비활성기관-" + suffix)
                .code("SEI" + suffix.toUpperCase())
                .status("INACTIVE")
                .build());

        String html = mockMvc.perform(get("/super-admin/organizations")
                        .session(superSession)
                        .param("keyword", "검색대상")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("검색대상활성기관-" + suffix);
        assertThat(html).doesNotContain("검색대상비활성기관-" + suffix);
    }

    @Test
    void 기관목록_정렬_페이징_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = uniqueSuffix();

        organizationRepository.save(Organization.builder()
                .name("정렬기관A-" + suffix)
                .code("SRA" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());
        organizationRepository.save(Organization.builder()
                .name("정렬기관B-" + suffix)
                .code("SRB" + suffix.toUpperCase())
                .status("ACTIVE")
                .build());

        String page0 = mockMvc.perform(get("/super-admin/organizations")
                        .session(superSession)
                        .param("keyword", "정렬기관")
                        .param("sortBy", "name")
                        .param("sortDir", "asc")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page0).contains("정렬기관A-" + suffix);
        assertThat(page0).doesNotContain("정렬기관B-" + suffix);

        String page1 = mockMvc.perform(get("/super-admin/organizations")
                        .session(superSession)
                        .param("keyword", "정렬기관")
                        .param("sortBy", "name")
                        .param("sortDir", "asc")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page1).contains("정렬기관B-" + suffix);
    }

    @Test
    void 기관관리자_대시보드_운영지표_표시_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String html = mockMvc.perform(get("/admin/dashboard")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("전체 대상자 수");
        assertThat(html).contains("미제출 배정 수");
        assertThat(html).contains("부서별 진행률");
        assertThat(html).contains("7일 내 마감 세션");
        assertThat(html).contains("마감 경과 진행중 세션");
        assertThat(html).contains("최근 7일 실패 작업");
        assertThat(html).contains("평가자 미제출 상위");
        assertThat(html).contains("마감 임박 세션");
        assertThat(html).contains("최근 업로드 결과");
        assertThat(html).contains("최근 작업 이력");
        assertThat(html).contains("바로가기");
        assertThat(html).contains("평가 세션 관리");
        assertThat(html).contains("감사 로그");
    }

    @Test
    void 슈퍼관리자_대시보드_기관별_운영현황_표시_테스트() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String html = mockMvc.perform(get("/super-admin/dashboard")
                        .session(superSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("기관별 운영 현황");
        assertThat(html).contains("사용자 수");
        assertThat(html).contains("세션(진행중)");
        assertThat(html).contains("위험 기관 수");
        assertThat(html).contains("마감경과 세션");
        assertThat(html).contains("최근7일 실패");
        assertThat(html).contains("최근 업로드");
        assertThat(html).contains("최근 오류");
    }

    @Test
    void 기관관리자_운영설정_저장_및_세션기본값_반영_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        mockMvc.perform(post("/admin/settings")
                        .session(adminSession)
                        .with(csrf())
                        .param("uploadMaxRows", "300")
                        .param("sessionDefaultDurationDays", "21")
                        .param("sessionDefaultAllowResubmit", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));

        String settingsHtml = mockMvc.perform(get("/admin/settings")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(settingsHtml).contains("운영 설정");
        assertThat(settingsHtml).contains("300");
        assertThat(settingsHtml).contains("21일");
        assertThat(settingsHtml).contains("허용");

        String sessionsHtml = mockMvc.perform(get("/admin/evaluation/sessions")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(sessionsHtml).contains(LocalDate.now().plusDays(21).toString());
        int allowResubmitIndex = sessionsHtml.indexOf("id=\"allowResubmit\"");
        assertThat(allowResubmitIndex).isPositive();
        String aroundAllowResubmit = sessionsHtml.substring(
                Math.max(0, allowResubmitIndex - 80),
                Math.min(sessionsHtml.length(), allowResubmitIndex + 180)
        );
        assertThat(aroundAllowResubmit).contains("checked");

        boolean settingsAuditExists = auditLogRepository.findAll().stream()
                .anyMatch(log -> "ADMIN_SETTINGS_UPDATE".equals(log.getAction())
                        && "SUCCESS".equals(log.getOutcome())
                        && "ORG_SETTINGS".equals(log.getTargetType()));
        assertThat(settingsAuditExists).isTrue();
    }

    @Test
    void 업로드이력_정렬_필터_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String prefix = "정렬업로드-" + suffix;

        uploadHistoryRepository.save(UploadHistory.builder()
                .organizationId(1L)
                .uploadType("EMPLOYEE")
                .fileName(prefix + "-B.xlsx")
                .totalRows(10)
                .successRows(8)
                .failRows(2)
                .status("PARTIAL")
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .build());
        uploadHistoryRepository.save(UploadHistory.builder()
                .organizationId(1L)
                .uploadType("EMPLOYEE")
                .fileName(prefix + "-A.xlsx")
                .totalRows(10)
                .successRows(10)
                .failRows(0)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build());

        String html = mockMvc.perform(get("/admin/uploads/history")
                        .session(adminSession)
                        .param("keyword", prefix)
                        .param("sortBy", "fileName")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int idxA = html.indexOf(prefix + "-A.xlsx");
        int idxB = html.indexOf(prefix + "-B.xlsx");
        assertThat(idxA).isPositive();
        assertThat(idxB).isPositive();
        assertThat(idxA).isLessThan(idxB);
    }

    @Test
    void 부서_템플릿_검색필터_동작_및_부서감사로그_기록() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "필터부서-" + suffix)
                        .param("code", ("FD" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String departmentHtml = mockMvc.perform(get("/admin/departments")
                        .session(adminSession)
                        .param("keyword", "필터부서-" + suffix)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(departmentHtml).contains("필터부서-" + suffix);

        mockMvc.perform(post("/admin/evaluation/templates")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "필터템플릿-" + suffix)
                        .param("description", "검색필터 테스트"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates"));

        String templateHtml = mockMvc.perform(get("/admin/evaluation/templates")
                        .session(adminSession)
                        .param("keyword", "필터템플릿-" + suffix)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(templateHtml).contains("필터템플릿-" + suffix);

        boolean deptCreateAuditExists = auditLogRepository.findAll().stream()
                .anyMatch(log -> "DEPT_CREATE".equals(log.getAction())
                        && "SUCCESS".equals(log.getOutcome())
                        && log.getDetail() != null
                        && log.getDetail().contains("필터부서-" + suffix));
        assertThat(deptCreateAuditExists).isTrue();
    }

    @Test
    void 부서목록_정렬_페이징_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "정렬부서A-" + suffix)
                        .param("code", ("DA" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "정렬부서B-" + suffix)
                        .param("code", ("DB" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection());

        String page0 = mockMvc.perform(get("/admin/departments")
                        .session(adminSession)
                        .param("keyword", "정렬부서")
                        .param("sortBy", "name")
                        .param("sortDir", "asc")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String page0Table = extractFirstTableSection(page0);
        assertThat(page0Table).contains("정렬부서A-" + suffix);
        assertThat(page0Table).doesNotContain("정렬부서B-" + suffix);

        String page1 = mockMvc.perform(get("/admin/departments")
                        .session(adminSession)
                        .param("keyword", "정렬부서")
                        .param("sortBy", "name")
                        .param("sortDir", "asc")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String page1Table = extractFirstTableSection(page1);
        assertThat(page1Table).contains("정렬부서B-" + suffix);
    }

    @Test
    void 템플릿목록_정렬_페이징_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        templateRepository.save(EvaluationTemplate.builder()
                .organizationId(1L)
                .name("정렬템플릿B-" + suffix)
                .description("template sort paging test")
                .active(true)
                .build());
        templateRepository.save(EvaluationTemplate.builder()
                .organizationId(1L)
                .name("정렬템플릿A-" + suffix)
                .description("template sort paging test")
                .active(true)
                .build());

        String page0 = mockMvc.perform(get("/admin/evaluation/templates")
                        .session(adminSession)
                        .param("keyword", "정렬템플릿")
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page0).contains("정렬템플릿A-" + suffix);
        assertThat(page0).doesNotContain("정렬템플릿B-" + suffix);

        String page1 = mockMvc.perform(get("/admin/evaluation/templates")
                        .session(adminSession)
                        .param("keyword", "정렬템플릿")
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page1).contains("정렬템플릿B-" + suffix);
    }

    @Test
    void 세션상세_assignment_검색필터_서버동작_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(1L)
                .name("배정필터템플릿-" + suffix)
                .description("assignment filter test")
                .active(true)
                .build());
        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(1L)
                .name("배정필터세션-" + suffix)
                .description("assignment filter test")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .templateId(template.getId())
                .allowResubmit(false)
                .createdBy(2L)
                .build());

        Long evaluatorId = employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(e -> "E001".equals(e.getEmployeeNumber()))
                .map(Employee::getId)
                .findFirst()
                .orElseThrow();
        Long evaluateeId = employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(e -> "E002".equals(e.getEmployeeNumber()))
                .map(Employee::getId)
                .findFirst()
                .orElseThrow();
        Long anotherEvaluateeId = employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(e -> "E003".equals(e.getEmployeeNumber()))
                .map(Employee::getId)
                .findFirst()
                .orElseThrow();

        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());
        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(evaluatorId)
                .evaluateeId(anotherEvaluateeId)
                .status("SUBMITTED")
                .build());

        String html = mockMvc.perform(get("/admin/evaluation/sessions/{id}", session.getId())
                        .session(adminSession)
                        .param("assignmentKeyword", "E002")
                        .param("assignmentStatus", "PENDING"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("E002");
        assertThat(html).doesNotContain("E003");
    }

    @Test
    void 세션상세_assignment_정렬_페이징_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(1L)
                .name("배정정렬템플릿-" + suffix)
                .description("assignment sort paging test")
                .active(true)
                .build());
        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(1L)
                .name("배정정렬세션-" + suffix)
                .description("assignment sort paging test")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .templateId(template.getId())
                .allowResubmit(false)
                .createdBy(2L)
                .build());

        Employee evaluator = employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(e -> "E001".equals(e.getEmployeeNumber()))
                .findFirst()
                .orElseThrow();
        Employee evaluateeB = employeeRepository.save(Employee.builder()
                .organizationId(1L)
                .departmentId(evaluator.getDepartmentId())
                .name("정렬대상B-" + suffix)
                .employeeNumber("ASB-" + suffix)
                .position("대리")
                .jobTitle("팀원")
                .email("asb-" + suffix + "@test.local")
                .status("ACTIVE")
                .build());
        Employee evaluateeA = employeeRepository.save(Employee.builder()
                .organizationId(1L)
                .departmentId(evaluator.getDepartmentId())
                .name("정렬대상A-" + suffix)
                .employeeNumber("ASA-" + suffix)
                .position("대리")
                .jobTitle("팀원")
                .email("asa-" + suffix + "@test.local")
                .status("ACTIVE")
                .build());

        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(evaluator.getId())
                .evaluateeId(evaluateeB.getId())
                .status("PENDING")
                .build());
        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(evaluator.getId())
                .evaluateeId(evaluateeA.getId())
                .status("PENDING")
                .build());

        String page0 = mockMvc.perform(get("/admin/evaluation/sessions/{id}", session.getId())
                        .session(adminSession)
                        .param("assignmentKeyword", "정렬대상")
                        .param("assignmentSortBy", "evaluateeName")
                        .param("assignmentSortDir", "asc")
                        .param("assignmentSize", "1")
                        .param("assignmentPage", "0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page0).contains("정렬대상A-" + suffix);
        assertThat(page0).doesNotContain("정렬대상B-" + suffix);

        String page1 = mockMvc.perform(get("/admin/evaluation/sessions/{id}", session.getId())
                        .session(adminSession)
                        .param("assignmentKeyword", "정렬대상")
                        .param("assignmentSortBy", "evaluateeName")
                        .param("assignmentSortDir", "asc")
                        .param("assignmentSize", "1")
                        .param("assignmentPage", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page1).contains("정렬대상B-" + suffix);
    }

    @Test
    void 감사로그_키워드_검색_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String deptName = "감사키워드부서-" + suffix;

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", deptName)
                        .param("code", ("AK" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String html = mockMvc.perform(get("/admin/audit")
                        .session(adminSession)
                        .param("action", "DEPT_CREATE")
                        .param("keyword", deptName))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("DEPT_CREATE");
        assertThat(html).contains(deptName);
    }

    @Test
    void 감사로그_대상타입_필터_및_CSV_내보내기_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String deptName = "감사내보내기부서-" + suffix;

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", deptName)
                        .param("code", ("AE" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String filteredHtml = mockMvc.perform(get("/admin/audit")
                        .session(adminSession)
                        .param("targetType", "DEPARTMENT")
                        .param("action", "DEPT_CREATE")
                        .param("keyword", deptName))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(filteredHtml).contains("DEPARTMENT");
        assertThat(filteredHtml).contains("DEPT_CREATE");

        var csvResponse = mockMvc.perform(get("/admin/audit/export.csv")
                        .session(adminSession)
                        .param("targetType", "DEPARTMENT")
                        .param("action", "DEPT_CREATE")
                        .param("keyword", deptName))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        String disposition = csvResponse.getHeader("Content-Disposition");
        String csv = csvResponse.getContentAsString();
        assertThat(disposition).contains("attachment; filename=\"audit_logs.csv\"");
        assertThat(csv).contains("occurred_at,outcome,action,actor_login_id,actor_role,organization_id,target_type,target_id,request_id,ip_address,detail");
        assertThat(csv).contains("\"DEPT_CREATE\"");
        assertThat(csv).contains("\"DEPARTMENT\"");
        assertThat(csv).contains(deptName);
    }

    @Test
    void 감사로그_정렬_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String deptA = "감사정렬A-" + suffix;
        String deptB = "감사정렬B-" + suffix;

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", deptA)
                        .param("code", ("SA" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", deptB)
                        .param("code", ("SB" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/admin/audit")
                        .session(adminSession)
                        .param("action", "DEPT_CREATE")
                        .param("keyword", "감사정렬")
                        .param("sortBy", "occurredAt")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int idxA = html.indexOf(deptA);
        int idxB = html.indexOf(deptB);
        assertThat(idxA).isPositive();
        assertThat(idxB).isPositive();
        assertThat(idxA).isLessThan(idxB);
    }

    @Test
    void 감사로그_역할필터_및_액션키워드검색_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String deptName = "감사역할필터부서-" + suffix;

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", deptName)
                        .param("code", ("AR" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String html = mockMvc.perform(get("/admin/audit")
                        .session(adminSession)
                        .param("actorRole", "ROLE_ORG_ADMIN")
                        .param("keyword", "DEPT_CREATE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("DEPT_CREATE");
        assertThat(html).contains("ROLE_ORG_ADMIN");
        assertThat(html).contains(deptName);

        String csv = mockMvc.perform(get("/admin/audit/export.csv")
                        .session(adminSession)
                        .param("actorRole", "ROLE_ORG_ADMIN")
                        .param("keyword", deptName))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("\"ROLE_ORG_ADMIN\"");
        assertThat(csv).contains(deptName);
    }

    @Test
    void 감사로그_작업군필터_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String deptName = "감사작업군부서-" + suffix;

        mockMvc.perform(post("/admin/departments")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", deptName)
                        .param("code", ("AG" + suffix).toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String html = mockMvc.perform(get("/admin/audit")
                        .session(adminSession)
                        .param("actionGroup", "USER_ADMIN_OPERATIONS")
                        .param("keyword", deptName))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("DEPT_CREATE");
        assertThat(html).contains(deptName);

        String groupedCsv = mockMvc.perform(get("/admin/audit/export.csv")
                        .session(adminSession)
                        .param("actionGroup", "USER_ADMIN_OPERATIONS"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(groupedCsv).contains("\"DEPT_CREATE\"");
        assertThat(groupedCsv).doesNotContain("\"AUTH_LOGIN\"");
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

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String resetPasswordAndExtractTemporaryPassword(MockHttpSession superSession, Long orgId, Long adminId) throws Exception {
        MvcResult resetResult = mockMvc.perform(post("/super-admin/organizations/{orgId}/admins/{adminId}/reset-password", orgId, adminId)
                        .session(superSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + orgId))
                .andReturn();
        String successMessage = (String) resetResult.getFlashMap().get("successMessage");
        int markerIdx = successMessage.lastIndexOf("임시 비밀번호: ");
        return successMessage.substring(markerIdx + "임시 비밀번호: ".length()).trim();
    }

    private MockMultipartFile createDepartmentUploadFile(String code, String name, String parentCode) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("부서");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("부서코드(필수)");
            header.createCell(1).setCellValue("부서명(필수)");
            header.createCell(2).setCellValue("상위부서코드(선택)");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(code);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(parentCode);
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "departments.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }

    private void upsertOrgSetting(Long orgId, String key, String value) {
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

    private String extractFirstTableSection(String html) {
        int tableStart = html.indexOf("<table class=\"table table-hover mb-0\">");
        if (tableStart < 0) {
            return html;
        }
        int tableEnd = html.indexOf("</table>", tableStart);
        if (tableEnd < 0) {
            return html.substring(tableStart);
        }
        return html.substring(tableStart, tableEnd);
    }
}
