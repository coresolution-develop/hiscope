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
    void 평가세션_수정_삭제_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        EvaluationTemplate template1 = createTemplate(1L, "세션수정템플릿1-" + suffix);
        EvaluationTemplate template2 = createTemplate(1L, "세션수정템플릿2-" + suffix);
        EvaluationSession session = createSession(1L, template1.getId(), "수정전세션-" + suffix, "PENDING", false);

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/update", session.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "수정후세션-" + suffix)
                        .param("description", "수정된 설명")
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-05-31")
                        .param("templateId", String.valueOf(template2.getId()))
                        .param("allowResubmit", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions/" + session.getId()));

        EvaluationSession updated = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("수정후세션-" + suffix);
        assertThat(updated.getDescription()).isEqualTo("수정된 설명");
        assertThat(updated.getTemplateId()).isEqualTo(template2.getId());
        assertThat(updated.isAllowResubmit()).isTrue();

        Long evaluatorId = findEmployeeIdByNumber(1L, "E001");
        Long evaluateeId = findEmployeeIdByNumber(1L, "E002");
        EvaluationRelationship relationship = relationshipRepository.save(EvaluationRelationship.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .relationType("MANUAL")
                .source("ADMIN_ADDED")
                .active(true)
                .build());

        EvaluationQuestion question = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template2.getId())
                .organizationId(1L)
                .category("역량")
                .content("삭제검증문항-" + suffix)
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(1)
                .active(true)
                .build());

        EvaluationAssignment assignment = assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(1L)
                .relationshipId(relationship.getId())
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());
        EvaluationResponse response = responseRepository.save(EvaluationResponse.builder()
                .assignmentId(assignment.getId())
                .organizationId(1L)
                .finalSubmit(false)
                .build());
        responseItemRepository.save(EvaluationResponseItem.builder()
                .responseId(response.getId())
                .questionId(question.getId())
                .scoreValue(3)
                .textValue("임시")
                .build());

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/delete", session.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions"));

        assertThat(sessionRepository.findById(session.getId())).isEmpty();
        assertThat(relationshipRepository.findById(relationship.getId())).isEmpty();
        assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
        assertThat(responseRepository.findById(response.getId())).isEmpty();
        assertThat(responseItemRepository.findByResponseId(response.getId())).isEmpty();
    }

    @Test
    void 평가세션_복제_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        EvaluationTemplate template = createTemplate(1L, "복제템플릿-" + suffix);
        EvaluationSession source = createSession(1L, template.getId(), "복제원본세션-" + suffix, "CLOSED", true);

        Long evaluatorId = findEmployeeIdByNumber(1L, "E001");
        Long evaluateeId = findEmployeeIdByNumber(1L, "E002");
        relationshipRepository.save(EvaluationRelationship.builder()
                .sessionId(source.getId())
                .organizationId(1L)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .relationType("MANUAL")
                .source("ADMIN_ADDED")
                .active(true)
                .build());

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/clone", source.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("copyRelationships", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/*"));

        EvaluationSession cloned = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).stream()
                .filter(s -> s.getName() != null && s.getName().contains("복제원본세션-" + suffix))
                .filter(s -> !s.getId().equals(source.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(cloned.getStatus()).isEqualTo("PENDING");
        assertThat(cloned.getTemplateId()).isEqualTo(source.getTemplateId());
        assertThat(cloned.isAllowResubmit()).isEqualTo(source.isAllowResubmit());

        boolean copiedRelationshipExists = relationshipRepository
                .findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(cloned.getId()).stream()
                .anyMatch(r -> r.getEvaluatorId().equals(evaluatorId)
                        && r.getEvaluateeId().equals(evaluateeId)
                        && "MANUAL".equals(r.getRelationType())
                        && r.isActive());
        assertThat(copiedRelationshipExists).isTrue();
    }

    @Test
    void 평가세션_복제_옵션지정_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        EvaluationTemplate template = createTemplate(1L, "복제옵션템플릿-" + suffix);
        EvaluationSession source = createSession(1L, template.getId(), "복제옵션원본-" + suffix, "PENDING", false);

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/clone", source.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("cloneName", "복제옵션대상-" + suffix)
                        .param("cloneStartDate", LocalDate.now().plusDays(10).toString())
                        .param("cloneEndDate", LocalDate.now().plusDays(20).toString())
                        .param("copyRelationships", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/*"));

        EvaluationSession cloned = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).stream()
                .filter(s -> ("복제옵션대상-" + suffix).equals(s.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(cloned.getStatus()).isEqualTo("PENDING");
        assertThat(cloned.getTemplateId()).isEqualTo(source.getTemplateId());
        assertThat(cloned.isAllowResubmit()).isFalse();
        assertThat(cloned.getStartDate()).isEqualTo(LocalDate.now().plusDays(10));
        assertThat(cloned.getEndDate()).isEqualTo(LocalDate.now().plusDays(20));
        assertThat(relationshipRepository.findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(cloned.getId())).isEmpty();
    }

    @Test
    void 평가세션_복제_명시이름_중복방지_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = uniqueSuffix();
        EvaluationTemplate template = createTemplate(1L, "복제중복템플릿-" + suffix);
        EvaluationSession source = createSession(1L, template.getId(), "복제중복원본-" + suffix, "PENDING", false);
        String duplicateName = "복제중복대상-" + suffix;
        createSession(1L, template.getId(), duplicateName, "PENDING", false);

        long beforeCount = sessionRepository.countByOrganizationId(1L);

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/clone", source.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("cloneName", duplicateName)
                        .param("copyRelationships", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions/" + source.getId()))
                .andExpect(flash().attributeExists("errorMessage"));

        long afterCount = sessionRepository.countByOrganizationId(1L);
        assertThat(afterCount).isEqualTo(beforeCount);
        long duplicateCount = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).stream()
                .filter(s -> duplicateName.equals(s.getName()))
                .count();
        assertThat(duplicateCount).isEqualTo(1);
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
    void 나의평가_목록은_IN_PROGRESS_세션만_노출된다() throws Exception {
        MockHttpSession userSession = loginAs("emp001", "password123");
        String suffix = uniqueSuffix();

        EvaluationTemplate template = createTemplate(1L, "목록필터템플릿-" + suffix);
        EvaluationSession inProgressSession = createSession(1L, template.getId(), "목록-IN_PROGRESS-" + suffix, "IN_PROGRESS", false);
        EvaluationSession pendingSession = createSession(1L, template.getId(), "목록-PENDING-" + suffix, "PENDING", false);
        EvaluationSession closedSession = createSession(1L, template.getId(), "목록-CLOSED-" + suffix, "CLOSED", false);

        Long evaluatorId = findEmployeeIdByNumber(1L, "E001");
        Long evaluateeId = findEmployeeIdByNumber(1L, "E002");

        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(inProgressSession.getId())
                .organizationId(1L)
                .relationshipId(null)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());
        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(pendingSession.getId())
                .organizationId(1L)
                .relationshipId(null)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());
        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(closedSession.getId())
                .organizationId(1L)
                .relationshipId(null)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .status("PENDING")
                .build());

        String html = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/user/evaluations")
                        .session(userSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("목록-IN_PROGRESS-" + suffix);
        assertThat(html).doesNotContain("목록-PENDING-" + suffix);
        assertThat(html).doesNotContain("목록-CLOSED-" + suffix);
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
    void 최종제출시_미응답_문항이_있으면_차단된다() throws Exception {
        MockHttpSession userSession = loginAs("emp001", "password123");
        String suffix = uniqueSuffix();

        EvaluationTemplate template = createTemplate(1L, "미응답차단템플릿-" + suffix);
        EvaluationQuestion q1 = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(1L)
                .category("역량")
                .content("미응답차단문항1-" + suffix)
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(1)
                .active(true)
                .build());
        questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(1L)
                .category("역량")
                .content("미응답차단문항2-" + suffix)
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(2)
                .active(true)
                .build());
        EvaluationSession session = createSession(1L, template.getId(), "미응답차단세션-" + suffix, "IN_PROGRESS", false);

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

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", assignment.getId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + q1.getId() + "]", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + assignment.getId()))
                .andExpect(flash().attributeExists("errorMessage"));

        EvaluationAssignment assignmentAfter = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(assignmentAfter.getStatus()).isEqualTo("PENDING");
        assertThat(responseRepository.findByAssignmentId(assignment.getId())).isEmpty();
    }

    @Test
    void 최종제출시_DESCRIPTIVE_미응답_문항이_있으면_차단된다() throws Exception {
        MockHttpSession userSession = loginAs("emp001", "password123");
        String suffix = uniqueSuffix();

        EvaluationTemplate template = createTemplate(1L, "서술미응답차단템플릿-" + suffix);
        EvaluationQuestion descriptiveQuestion = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(1L)
                .category("협업")
                .content("서술미응답차단문항-" + suffix)
                .questionType("DESCRIPTIVE")
                .maxScore(null)
                .sortOrder(1)
                .active(true)
                .build());
        EvaluationSession session = createSession(1L, template.getId(), "서술미응답차단세션-" + suffix, "IN_PROGRESS", false);

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

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", assignment.getId())
                        .session(userSession)
                        .with(csrf())
                        .param("texts[" + descriptiveQuestion.getId() + "]", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + assignment.getId()))
                .andExpect(flash().attributeExists("errorMessage"));

        EvaluationAssignment assignmentAfter = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(assignmentAfter.getStatus()).isEqualTo("PENDING");
        assertThat(responseRepository.findByAssignmentId(assignment.getId())).isEmpty();
    }

    @Test
    void 최종제출시_DESCRIPTIVE_응답이_있으면_제출된다() throws Exception {
        MockHttpSession userSession = loginAs("emp001", "password123");
        String suffix = uniqueSuffix();

        EvaluationTemplate template = createTemplate(1L, "서술응답통과템플릿-" + suffix);
        EvaluationQuestion descriptiveQuestion = questionRepository.save(EvaluationQuestion.builder()
                .templateId(template.getId())
                .organizationId(1L)
                .category("협업")
                .content("서술응답통과문항-" + suffix)
                .questionType("DESCRIPTIVE")
                .maxScore(null)
                .sortOrder(1)
                .active(true)
                .build());
        EvaluationSession session = createSession(1L, template.getId(), "서술응답통과세션-" + suffix, "IN_PROGRESS", false);

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

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", assignment.getId())
                        .session(userSession)
                        .with(csrf())
                        .param("texts[" + descriptiveQuestion.getId() + "]", "서술형 답변입니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + assignment.getId() + "/complete"));

        EvaluationAssignment assignmentAfter = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(assignmentAfter.getStatus()).isEqualTo("SUBMITTED");

        EvaluationResponse response = responseRepository.findByAssignmentId(assignment.getId()).orElseThrow();
        assertThat(response.isFinalSubmit()).isTrue();
        EvaluationResponseItem item = responseItemRepository
                .findByResponseIdAndQuestionId(response.getId(), descriptiveQuestion.getId())
                .orElseThrow();
        assertThat(item.getTextValue()).isEqualTo("서술형 답변입니다.");
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

    @Test
    void 기관관리자_결과조회_기본화면_테스트() throws Exception {
        EvaluationFixture fixture = createEvaluationFixture(false);
        MockHttpSession userSession = loginAs("emp001", "password123");
        MockHttpSession adminSession = loginAs("admin", "password123");

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", fixture.assignmentId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + fixture.questionId() + "]", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + fixture.assignmentId() + "/complete"));

        EvaluationAssignment assignment = assignmentRepository.findById(fixture.assignmentId()).orElseThrow();
        String html = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/evaluation/results")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(assignment.getSessionId()))
                        .param("keyword", "이지영"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("평가 결과 조회");
        assertThat(html).contains("이지영");
        assertThat(html).contains("4.0");
    }

    @Test
    void 기관관리자_미제출자_CSV_다운로드_테스트() throws Exception {
        EvaluationFixture fixture = createEvaluationFixture(false);
        MockHttpSession adminSession = loginAs("admin", "password123");
        EvaluationAssignment assignment = assignmentRepository.findById(fixture.assignmentId()).orElseThrow();

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/evaluation/results/pending.csv")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(assignment.getSessionId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        String disposition = response.getHeader("Content-Disposition");
        String csv = response.getContentAsString();
        assertThat(disposition).contains("attachment; filename=\"pending_submitters_session_" + assignment.getSessionId() + ".csv\"");
        assertThat(csv).contains("evaluator_name,evaluator_employee_number,evaluator_department,evaluatee_name,evaluatee_employee_number,evaluatee_department,assigned_at");
        assertThat(csv).contains("\"E001\"");
        assertThat(csv).contains("\"E002\"");
    }

    @Test
    void 기관관리자_미제출자_CSV_정렬_및_부서필터_테스트() throws Exception {
        EvaluationFixture fixture = createEvaluationFixture(false);
        MockHttpSession adminSession = loginAs("admin", "password123");
        EvaluationAssignment baseAssignment = assignmentRepository.findById(fixture.assignmentId()).orElseThrow();
        Long evaluatorId = baseAssignment.getEvaluatorId();

        String suffix = uniqueSuffix();
        Department deptA = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("결과필터A-" + suffix)
                .code(("RFA" + suffix).toUpperCase())
                .active(true)
                .build());
        Department deptB = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("결과필터B-" + suffix)
                .code(("RFB" + suffix).toUpperCase())
                .active(true)
                .build());

        Employee evaluateeA = employeeRepository.save(Employee.builder()
                .organizationId(1L)
                .departmentId(deptA.getId())
                .name("가나다-" + suffix)
                .employeeNumber("EPA-" + suffix)
                .position("대리")
                .jobTitle("팀원")
                .email("epa-" + suffix + "@example.com")
                .status("ACTIVE")
                .build());
        Employee evaluateeB = employeeRepository.save(Employee.builder()
                .organizationId(1L)
                .departmentId(deptB.getId())
                .name("하하하-" + suffix)
                .employeeNumber("EPB-" + suffix)
                .position("대리")
                .jobTitle("팀원")
                .email("epb-" + suffix + "@example.com")
                .status("ACTIVE")
                .build());

        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(baseAssignment.getSessionId())
                .organizationId(1L)
                .relationshipId(null)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeA.getId())
                .status("PENDING")
                .build());
        assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(baseAssignment.getSessionId())
                .organizationId(1L)
                .relationshipId(null)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeB.getId())
                .status("PENDING")
                .build());

        String sortedCsv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/evaluation/results/pending.csv")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(baseAssignment.getSessionId()))
                        .param("pendingSortBy", "evaluateeName")
                        .param("pendingSortDir", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int idxA = sortedCsv.indexOf("가나다-" + suffix);
        int idxB = sortedCsv.indexOf("하하하-" + suffix);
        assertThat(idxA).isPositive();
        assertThat(idxB).isPositive();
        assertThat(idxA).isLessThan(idxB);

        String filteredCsv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/evaluation/results/pending.csv")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(baseAssignment.getSessionId()))
                        .param("departmentId", String.valueOf(deptA.getId()))
                        .param("pendingSortBy", "evaluateeName")
                        .param("pendingSortDir", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(filteredCsv).contains("가나다-" + suffix);
        assertThat(filteredCsv).doesNotContain("하하하-" + suffix);
    }

    @Test
    void 기관관리자_결과_CSV_다운로드_고도화_테스트() throws Exception {
        EvaluationFixture fixture = createEvaluationFixture(false);
        MockHttpSession userSession = loginAs("emp001", "password123");
        MockHttpSession adminSession = loginAs("admin", "password123");
        EvaluationAssignment assignment = assignmentRepository.findById(fixture.assignmentId()).orElseThrow();

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", fixture.assignmentId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + fixture.questionId() + "]", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + fixture.assignmentId() + "/complete"));

        String evaluateeCsv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/evaluation/results/evaluatees.csv")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(assignment.getSessionId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(evaluateeCsv).contains("evaluatee_name,employee_number,department,submitted_count,total_count,submission_rate,average_score");
        assertThat(evaluateeCsv).contains("\"이지영\"");

        String departmentCsv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/evaluation/results/departments.csv")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(assignment.getSessionId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(departmentCsv).contains("department,evaluatee_count,submitted_count,total_count,submission_rate,average_score");

        String assignmentCsv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/evaluation/results/assignments.csv")
                        .session(adminSession)
                        .param("sessionId", String.valueOf(assignment.getSessionId()))
                        .param("assignmentSortBy", "evaluateeName")
                        .param("assignmentSortDir", "asc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(assignmentCsv).contains("evaluator_name,evaluator_employee_number,evaluator_department,evaluatee_name,evaluatee_employee_number,evaluatee_department,status,submitted_at,average_score");
        assertThat(assignmentCsv).contains("\"김민수\"");
        assertThat(assignmentCsv).contains("\"이지영\"");
        assertThat(assignmentCsv).contains("\"SUBMITTED\"");
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
