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
