package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
    private AuditLogRepository auditLogRepository;

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
                .andExpect(status().isOk())
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
        assertThat(html).contains("최근 업로드 결과");
        assertThat(html).contains("최근 작업 이력");
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
}
