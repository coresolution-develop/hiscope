package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
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
import com.hiscope.evaluation.domain.upload.repository.UploadHistoryRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationWorkflowScenarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UploadHistoryRepository uploadHistoryRepository;

    @Autowired
    private EvaluationTemplateRepository templateRepository;

    @Autowired
    private EvaluationQuestionRepository questionRepository;

    @Autowired
    private EvaluationSessionRepository sessionRepository;

    @Autowired
    private EvaluationRelationshipRepository relationshipRepository;

    @Autowired
    private EvaluationAssignmentRepository assignmentRepository;

    @Autowired
    private EvaluationResponseRepository responseRepository;

    @Autowired
    private EvaluationResponseItemRepository responseItemRepository;

    @Test
    void 부서_직원_엑셀_업로드_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();

        String deptCode = ("QA_" + suffix).toUpperCase();
        MockMultipartFile deptFile = new MockMultipartFile(
                "file",
                "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentExcel(deptCode, "품질관리팀-" + suffix)
        );

        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(deptFile)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        Department department = departmentRepository.findByOrganizationIdAndCode(1L, deptCode).orElseThrow();
        assertThat(department.getName()).isEqualTo("품질관리팀-" + suffix);

        String employeeNumber = "EMP-" + suffix;
        String loginId = "empu" + suffix;
        MockMultipartFile empFile = new MockMultipartFile(
                "file",
                "employees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createEmployeeExcel(employeeNumber, "업로드직원-" + suffix, deptCode, loginId)
        );

        mockMvc.perform(multipart("/admin/uploads/employees")
                        .file(empFile)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));

        assertThat(employeeRepository.existsByOrganizationIdAndEmployeeNumber(1L, employeeNumber)).isTrue();
        var histories = uploadHistoryRepository.findByOrganizationIdOrderByCreatedAtDesc(1L);
        assertThat(histories.stream().anyMatch(h -> "DEPARTMENT".equals(h.getUploadType()))).isTrue();
        assertThat(histories.stream().anyMatch(h -> "EMPLOYEE".equals(h.getUploadType()))).isTrue();
    }

    @Test
    void 평가항목_등록_수정_삭제_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        String templateName = "템플릿-" + suffix;

        mockMvc.perform(post("/admin/evaluation/templates")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", templateName)
                        .param("description", "테스트 템플릿"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates"));

        EvaluationTemplate template = templateRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).stream()
                .filter(t -> templateName.equals(t.getName()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/admin/evaluation/templates/{id}/questions", template.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("category", "역량")
                        .param("content", "질문-" + suffix)
                        .param("questionType", "SCALE")
                        .param("maxScore", "5")
                        .param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        EvaluationQuestion question = questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(template.getId(), true)
                .stream().filter(q -> ("질문-" + suffix).equals(q.getContent())).findFirst().orElseThrow();

        mockMvc.perform(post("/admin/evaluation/templates/{id}/questions/{qId}/update", template.getId(), question.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("category", "역량")
                        .param("content", "질문수정-" + suffix)
                        .param("questionType", "SCALE")
                        .param("maxScore", "7")
                        .param("sortOrder", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        EvaluationQuestion updated = questionRepository.findById(question.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("질문수정-" + suffix);
        assertThat(updated.getMaxScore()).isEqualTo(7);

        mockMvc.perform(post("/admin/evaluation/templates/{id}/questions/{qId}/delete", template.getId(), question.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        EvaluationQuestion deleted = questionRepository.findById(question.getId()).orElseThrow();
        assertThat(deleted.isActive()).isFalse();
    }

    @Test
    void 평가세션_생성_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        EvaluationTemplate template = createTemplate(1L, "세션템플릿-" + suffix);

        String sessionName = "세션-" + suffix;
        mockMvc.perform(post("/admin/evaluation/sessions")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", sessionName)
                        .param("description", "세션 생성 테스트")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30")
                        .param("templateId", String.valueOf(template.getId()))
                        .param("allowResubmit", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions**"));

        assertThat(sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).stream()
                .anyMatch(s -> sessionName.equals(s.getName()))).isTrue();
    }

    @Test
    void 평가관계_추가_삭제_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        EvaluationTemplate template = createTemplate(1L, "관계템플릿-" + suffix);
        EvaluationSession session = createSession(1L, template.getId(), "관계세션-" + suffix, "PENDING", false);

        Long evaluatorId = findEmployeeIdByNumber(1L, "E001");
        Long evaluateeId = findEmployeeIdByNumber(1L, "E002");

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/relationships/manual", session.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("evaluatorId", String.valueOf(evaluatorId))
                        .param("evaluateeId", String.valueOf(evaluateeId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/" + session.getId() + "/relationships**"));

        EvaluationRelationship relationship = relationshipRepository
                .findBySessionIdAndEvaluatorIdAndEvaluateeId(session.getId(), evaluatorId, evaluateeId)
                .orElseThrow();
        assertThat(relationship.isActive()).isTrue();

        mockMvc.perform(post("/admin/evaluation/sessions/{sid}/relationships/{rid}/delete", session.getId(), relationship.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/" + session.getId() + "/relationships**"));

        assertThat(relationshipRepository.findBySessionIdAndEvaluatorIdAndEvaluateeId(session.getId(), evaluatorId, evaluateeId))
                .isEmpty();
    }

    @Test
    void 일반사용자_평가_제출_테스트() throws Exception {
        EvaluationFixture fixture = createEvaluationFixture(false);
        MockHttpSession userSession = loginAs("emp001", "password123");

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", fixture.assignmentId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + fixture.questionId() + "]", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + fixture.assignmentId() + "/complete"));

        EvaluationAssignment assignment = assignmentRepository.findById(fixture.assignmentId()).orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo("SUBMITTED");

        EvaluationResponse response = responseRepository.findByAssignmentId(fixture.assignmentId()).orElseThrow();
        assertThat(response.isFinalSubmit()).isTrue();
    }

    @Test
    void 중복제출_또는_허용되지않은_수정_방지_테스트() throws Exception {
        EvaluationFixture fixture = createEvaluationFixture(false);
        MockHttpSession userSession = loginAs("emp001", "password123");

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", fixture.assignmentId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + fixture.questionId() + "]", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + fixture.assignmentId() + "/complete"));

        EvaluationResponse firstResponse = responseRepository.findByAssignmentId(fixture.assignmentId()).orElseThrow();
        EvaluationResponseItem firstItem = responseItemRepository
                .findByResponseIdAndQuestionId(firstResponse.getId(), fixture.questionId())
                .orElseThrow();
        assertThat(firstItem.getScoreValue()).isEqualTo(3);

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", fixture.assignmentId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + fixture.questionId() + "]", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + fixture.assignmentId()))
                .andExpect(flash().attributeExists("errorMessage"));

        EvaluationResponse responseAfter = responseRepository.findByAssignmentId(fixture.assignmentId()).orElseThrow();
        EvaluationResponseItem itemAfter = responseItemRepository
                .findByResponseIdAndQuestionId(responseAfter.getId(), fixture.questionId())
                .orElseThrow();
        assertThat(itemAfter.getScoreValue()).isEqualTo(3);
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

    private EvaluationTemplate createTemplate(Long orgId, String name) {
        return templateRepository.save(EvaluationTemplate.builder()
                .organizationId(orgId)
                .name(name)
                .description("test template")
                .active(true)
                .build());
    }

    private EvaluationSession createSession(Long orgId, Long templateId, String name, String status, boolean allowResubmit) {
        return sessionRepository.save(EvaluationSession.builder()
                .organizationId(orgId)
                .name(name)
                .description("test session")
                .status(status)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(7))
                .allowResubmit(allowResubmit)
                .templateId(templateId)
                .createdBy(2L)
                .build());
    }

    private Long findEmployeeIdByNumber(Long orgId, String employeeNumber) {
        return employeeRepository.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(e -> employeeNumber.equals(e.getEmployeeNumber()))
                .map(Employee::getId)
                .findFirst()
                .orElseThrow();
    }

    private EvaluationFixture createEvaluationFixture(boolean allowResubmit) {
        String suffix = uniqueSuffix();
        EvaluationTemplate template = createTemplate(1L, "제출템플릿-" + suffix);
        EvaluationQuestion question = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(1L)
                .category("역량")
                .content("평가문항-" + suffix)
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(1)
                .active(true)
                .build());
        EvaluationSession session = createSession(1L, template.getId(), "제출세션-" + suffix, "IN_PROGRESS", allowResubmit);

        Long evaluatorId = findEmployeeIdByNumber(1L, "E001");
        Long evaluateeId = findEmployeeIdByNumber(1L, "E002");
        EvaluationAssignment assignment = assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .relationshipId(null)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());

        return new EvaluationFixture(assignment.getId(), question.getId());
    }

    private byte[] createDepartmentExcel(String deptCode, String deptName) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("departments");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("부서코드");
            header.createCell(1).setCellValue("부서명");
            header.createCell(2).setCellValue("상위부서코드");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(deptCode);
            row.createCell(1).setCellValue(deptName);
            row.createCell(2).setCellValue("");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createEmployeeExcel(String employeeNumber, String name, String deptCode, String loginId) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("employees");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("사원번호");
            header.createCell(1).setCellValue("이름");
            header.createCell(2).setCellValue("부서코드");
            header.createCell(3).setCellValue("직위");
            header.createCell(4).setCellValue("직책");
            header.createCell(5).setCellValue("이메일");
            header.createCell(6).setCellValue("로그인ID");
            header.createCell(7).setCellValue("상태");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(employeeNumber);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(deptCode);
            row.createCell(3).setCellValue("대리");
            row.createCell(4).setCellValue("팀원");
            row.createCell(5).setCellValue(loginId + "@test.local");
            row.createCell(6).setCellValue(loginId);
            row.createCell(7).setCellValue("ACTIVE");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record EvaluationFixture(Long assignmentId, Long questionId) {
    }
}
