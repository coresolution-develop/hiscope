package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.config.properties.OpenAiSummaryProperties;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserMyPageIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private EvaluationTemplateRepository templateRepository;
    @Autowired private EvaluationQuestionRepository questionRepository;
    @Autowired private EvaluationSessionRepository sessionRepository;
    @Autowired private EvaluationAssignmentRepository assignmentRepository;
    @Autowired private EvaluationResponseRepository responseRepository;
    @Autowired private EvaluationResponseItemRepository responseItemRepository;
    @Autowired private OpenAiSummaryProperties openAiSummaryProperties;

    @Test
    void 마이페이지_데이터없음_화면_및_문구_검증() throws Exception {
        String suffix = uniqueSuffix();
        Organization org = createOrg("MYE" + suffix);
        Department dept = createDept(org.getId(), "QA", "검증팀");

        Employee user = createEmployee(org.getId(), dept.getId(), "사용자-" + suffix, "EMP-" + suffix);
        createUserAccount(org.getId(), user, "mypage_empty_" + suffix, "password123");

        MockHttpSession session = loginAs("mypage_empty_" + suffix, "password123");
        String html = mockMvc.perform(get("/user/mypage").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("아직 받은 평가 결과가 없습니다.");
        assertThat(html).contains("평가 데이터 기반 요약");
        assertThat(html).contains("AI 요약");
        assertThat(html).doesNotContain("TEMP_HEURISTIC");
        assertThat(html).doesNotContain("OPENAI_PREPARED");
        assertThat(html).contains("Array.isArray(rawCategoryRows)");
    }

    @Test
    void 마이페이지_부분데이터_렌더링_검증() throws Exception {
        String suffix = uniqueSuffix();
        Organization org = createOrg("MYP" + suffix);
        Department dept = createDept(org.getId(), "DV", "개발팀");

        Employee evaluatee = createEmployee(org.getId(), dept.getId(), "피평가자-" + suffix, "EV-" + suffix);
        Employee evaluator = createEmployee(org.getId(), dept.getId(), "평가자-" + suffix, "ER-" + suffix);
        createUserAccount(org.getId(), evaluatee, "mypage_partial_" + suffix, "password123");

        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(org.getId())
                .name("MYPAGE-TEMPLATE-" + suffix)
                .description("mypage partial test")
                .active(true)
                .build());

        EvaluationQuestion scoreQuestion = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(org.getId())
                .category("협업")
                .content("협업 태도")
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(1)
                .active(true)
                .build());

        EvaluationSession evalSession = sessionRepository.save(EvaluationSession.builder()
                .organizationId(org.getId())
                .name("MYPAGE-SESSION-" + suffix)
                .description("mypage partial")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .templateId(template.getId())
                .build());

        EvaluationAssignment assignment = assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(evalSession.getId())
                .organizationId(org.getId())
                .evaluatorId(evaluator.getId())
                .evaluateeId(evaluatee.getId())
                .status("PENDING")
                .build());
        assignment.submit();

        EvaluationResponse response = responseRepository.save(EvaluationResponse.builder()
                .assignmentId(assignment.getId())
                .organizationId(org.getId())
                .finalSubmit(true)
                .submittedAt(LocalDateTime.now())
                .build());

        responseItemRepository.save(EvaluationResponseItem.builder()
                .responseId(response.getId())
                .questionId(scoreQuestion.getId())
                .scoreValue(4)
                .build());

        MockHttpSession session = loginAs("mypage_partial_" + suffix, "password123");
        String html = mockMvc.perform(get("/user/mypage").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("MYPAGE-SESSION-");
        assertThat(html).contains("협업 태도");
        assertThat(html).contains("평가 결과 요약");
        assertThat(html).contains("Number.isFinite");
    }

    @Test
    void 마이페이지_정상데이터_코멘트표시_검증() throws Exception {
        String suffix = uniqueSuffix();
        Organization org = createOrg("MYN" + suffix);
        Department dept = createDept(org.getId(), "OP", "운영팀");

        Employee evaluatee = createEmployee(org.getId(), dept.getId(), "피평가자N-" + suffix, "N-EV-" + suffix);
        Employee evaluator1 = createEmployee(org.getId(), dept.getId(), "평가자A-" + suffix, "N-ER1-" + suffix);
        Employee evaluator2 = createEmployee(org.getId(), dept.getId(), "평가자B-" + suffix, "N-ER2-" + suffix);
        createUserAccount(org.getId(), evaluatee, "mypage_normal_" + suffix, "password123");

        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(org.getId())
                .name("MYPAGE-NORMAL-TEMPLATE-" + suffix)
                .description("mypage normal test")
                .active(true)
                .build());

        EvaluationQuestion scoreQuestion = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(org.getId())
                .category("성과")
                .content("업무 성과")
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(1)
                .active(true)
                .build());

        EvaluationQuestion textQuestion = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(org.getId())
                .category("성과")
                .content("총평")
                .questionType("DESCRIPTIVE")
                .sortOrder(2)
                .active(true)
                .build());

        EvaluationSession evalSession = sessionRepository.save(EvaluationSession.builder()
                .organizationId(org.getId())
                .name("MYPAGE-NORMAL-SESSION-" + suffix)
                .description("mypage normal")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .templateId(template.getId())
                .build());

        createSubmittedResponse(org.getId(), evalSession.getId(), evaluator1.getId(), evaluatee.getId(), scoreQuestion.getId(), textQuestion.getId(), 5, "강점이 뚜렷합니다.");
        createSubmittedResponse(org.getId(), evalSession.getId(), evaluator2.getId(), evaluatee.getId(), scoreQuestion.getId(), textQuestion.getId(), 4, "협업 개선 여지가 있습니다.");

        MockHttpSession session = loginAs("mypage_normal_" + suffix, "password123");
        String html = mockMvc.perform(get("/user/mypage").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("MYPAGE-NORMAL-SESSION-");
        assertThat(html).contains("강점");
        assertThat(html).contains("보완점");
        assertThat(html).contains("강점이 뚜렷합니다.");
        assertThat(html).contains("협업 개선 여지가 있습니다.");
    }

    @Test
    void 마이페이지_OPENAI활성_API키없음_fallback휴리스틱_정상렌더링() throws Exception {
        OpenAiSummaryProperties.Provider prevProvider = openAiSummaryProperties.getProvider();
        boolean prevEnabled = openAiSummaryProperties.isEnabled();
        boolean prevFallback = openAiSummaryProperties.isFallbackToHeuristic();
        String prevApiKey = openAiSummaryProperties.getApiKey();

        openAiSummaryProperties.setProvider(OpenAiSummaryProperties.Provider.OPENAI);
        openAiSummaryProperties.setEnabled(true);
        openAiSummaryProperties.setFallbackToHeuristic(true);
        openAiSummaryProperties.setApiKey("");

        try {
            String suffix = uniqueSuffix();
            Organization org = createOrg("MYF" + suffix);
            Department dept = createDept(org.getId(), "RD", "연구개발팀");

            Employee evaluatee = createEmployee(org.getId(), dept.getId(), "피평가자F-" + suffix, "F-EV-" + suffix);
            Employee evaluator = createEmployee(org.getId(), dept.getId(), "평가자F-" + suffix, "F-ER-" + suffix);
            createUserAccount(org.getId(), evaluatee, "mypage_fallback_" + suffix, "password123");

            EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                    .organizationId(org.getId())
                    .name("MYPAGE-FALLBACK-TEMPLATE-" + suffix)
                    .description("mypage fallback")
                    .active(true)
                    .build());

            EvaluationQuestion scoreQuestion = questionRepository.save(EvaluationQuestion.builder()
                    .templateId(template.getId())
                    .organizationId(org.getId())
                    .category("협업")
                    .content("협업 기여도")
                    .questionType("SCALE")
                    .maxScore(5)
                    .sortOrder(1)
                    .active(true)
                    .build());

            EvaluationQuestion textQuestion = questionRepository.save(EvaluationQuestion.builder()
                    .templateId(template.getId())
                    .organizationId(org.getId())
                    .category("협업")
                    .content("총평")
                    .questionType("DESCRIPTIVE")
                    .sortOrder(2)
                    .active(true)
                    .build());

            EvaluationSession evalSession = sessionRepository.save(EvaluationSession.builder()
                    .organizationId(org.getId())
                    .name("MYPAGE-FALLBACK-SESSION-" + suffix)
                    .description("mypage fallback session")
                    .status("IN_PROGRESS")
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now().plusDays(5))
                    .templateId(template.getId())
                    .build());

            createSubmittedResponse(org.getId(), evalSession.getId(), evaluator.getId(), evaluatee.getId(),
                    scoreQuestion.getId(), textQuestion.getId(), 4, "안정적 협업 역량이 보입니다.");

            MockHttpSession session = loginAs("mypage_fallback_" + suffix, "password123");
            String html = mockMvc.perform(get("/user/mypage").session(session))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("평가 데이터 기반 요약");
            assertThat(html).contains("AI 요약");
            assertThat(html).doesNotContain("TEMP_HEURISTIC");
            assertThat(html).doesNotContain("OPENAI_PREPARED");
            assertThat(html).contains("강점 영역이 아직 명확하지 않아 응답 축적이 더 필요합니다.");
            assertThat(html).contains("취약 영역 편차가 크지 않아 현재 수준을 유지하는 것이 좋습니다.");
        } finally {
            openAiSummaryProperties.setProvider(prevProvider);
            openAiSummaryProperties.setEnabled(prevEnabled);
            openAiSummaryProperties.setFallbackToHeuristic(prevFallback);
            openAiSummaryProperties.setApiKey(prevApiKey);
        }
    }

    private void createSubmittedResponse(Long orgId,
                                         Long sessionId,
                                         Long evaluatorId,
                                         Long evaluateeId,
                                         Long scoreQuestionId,
                                         Long textQuestionId,
                                         int score,
                                         String comment) {
        EvaluationAssignment assignment = assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(sessionId)
                .organizationId(orgId)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());
        assignment.submit();

        EvaluationResponse response = responseRepository.save(EvaluationResponse.builder()
                .assignmentId(assignment.getId())
                .organizationId(orgId)
                .finalSubmit(true)
                .submittedAt(LocalDateTime.now())
                .build());

        responseItemRepository.save(EvaluationResponseItem.builder()
                .responseId(response.getId())
                .questionId(scoreQuestionId)
                .scoreValue(score)
                .build());

        responseItemRepository.save(EvaluationResponseItem.builder()
                .responseId(response.getId())
                .questionId(textQuestionId)
                .textValue(comment)
                .build());
    }

    private Organization createOrg(String code) {
        return organizationRepository.save(Organization.builder()
                .name("마이페이지검증-" + code)
                .code(code)
                .status("ACTIVE")
                .build());
    }

    private Department createDept(Long orgId, String code, String name) {
        return departmentRepository.save(Department.builder()
                .organizationId(orgId)
                .code(code)
                .name(name)
                .active(true)
                .build());
    }

    private Employee createEmployee(Long orgId, Long deptId, String name, String employeeNumber) {
        return employeeRepository.save(Employee.builder()
                .organizationId(orgId)
                .departmentId(deptId)
                .name(name)
                .employeeNumber(employeeNumber)
                .position("사원")
                .jobTitle("팀원")
                .email(employeeNumber.toLowerCase() + "@test.local")
                .status("ACTIVE")
                .build());
    }

    private UserAccount createUserAccount(Long orgId, Employee employee, String loginId, String rawPassword) {
        return userAccountRepository.save(UserAccount.builder()
                .employee(employee)
                .organizationId(orgId)
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("ROLE_USER")
                .mustChangePassword(false)
                .build());
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
