package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.common.audit.repository.AuditLogRepository;
import com.hiscope.evaluation.support.TestcontainersConfig;
import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StagingPostgresFinalRehearsalIntegrationTest extends TestcontainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

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
    private UploadHistoryRepository uploadHistoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void postgres_스테이징_최종_리허설() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 1) 최고 관리자 기관 생성
        MockHttpSession superSession = loginAs("super", "password123");
        String orgCode = "STG" + suffix.toUpperCase();
        String orgName = "Staging Org " + suffix;
        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", orgName)
                        .param("code", orgCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations"));

        Organization org = organizationRepository.findByCode(orgCode).orElseThrow();
        assertThat(org.getStatus()).isEqualTo("ACTIVE");

        // 2) 기관 관리자 생성 및 로그인
        String orgAdminLoginId = "orgadm_" + suffix;
        String orgAdminPassword = "Pass@" + suffix;
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "기관관리자-" + suffix)
                        .param("loginId", orgAdminLoginId)
                        .param("password", orgAdminPassword)
                        .param("email", orgAdminLoginId + "@staging.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account orgAdmin = accountRepository.findByLoginId(orgAdminLoginId).orElseThrow();
        assertThat(orgAdmin.getOrganizationId()).isEqualTo(org.getId());
        MockHttpSession orgAdminSession = loginAs(orgAdminLoginId, orgAdminPassword);

        // 3) 부서/사용자 엑셀 업로드
        String deptCodeA = "DPA" + suffix.toUpperCase();
        String deptCodeB = "DPB" + suffix.toUpperCase();
        MockMultipartFile deptFile = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentExcel(List.of(deptCodeA, deptCodeB))
        );
        mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(deptFile)
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));

        String evalLogin = "eval_" + suffix;
        String eedLogin = "eed_" + suffix;
        String obsLogin = "obs_" + suffix;
        MockMultipartFile empFile = new MockMultipartFile(
                "file", "employees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createEmployeeExcel(List.of(
                        new EmpRow("EMP-A-" + suffix, "평가자-" + suffix, deptCodeA, evalLogin),
                        new EmpRow("EMP-B-" + suffix, "피평가자-" + suffix, deptCodeA, eedLogin),
                        new EmpRow("EMP-C-" + suffix, "옵저버-" + suffix, deptCodeB, obsLogin)
                ))
        );
        mockMvc.perform(multipart("/admin/uploads/employees")
                        .file(empFile)
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"));

        Employee evaluator = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> ("EMP-A-" + suffix).equals(e.getEmployeeNumber()))
                .findFirst()
                .orElseThrow();
        Employee evaluatee = employeeRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(e -> ("EMP-B-" + suffix).equals(e.getEmployeeNumber()))
                .findFirst()
                .orElseThrow();

        // 4) 평가 항목 업로드/수정/삭제
        String templateName = "Template-" + suffix;
        mockMvc.perform(post("/admin/evaluation/templates")
                        .session(orgAdminSession)
                        .with(csrf())
                        .param("name", templateName)
                        .param("description", "staging rehearsal template"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates"));

        EvaluationTemplate template = templateRepository.findByOrganizationIdOrderByCreatedAtDesc(org.getId()).stream()
                .filter(t -> templateName.equals(t.getName()))
                .findFirst()
                .orElseThrow();

        MockMultipartFile questionFile = new MockMultipartFile(
                "file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createQuestionExcel("업로드 문항-" + suffix)
        );
        mockMvc.perform(multipart("/admin/evaluation/templates/{id}/questions/upload", template.getId())
                        .file(questionFile)
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        EvaluationQuestion uploadedQuestion = questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(template.getId(), true).stream()
                .filter(q -> ("업로드 문항-" + suffix).equals(q.getContent()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/admin/evaluation/templates/{id}/questions/{qId}/update", template.getId(), uploadedQuestion.getId())
                        .session(orgAdminSession)
                        .with(csrf())
                        .param("category", "역량")
                        .param("content", "수정 문항-" + suffix)
                        .param("questionType", "SCALE")
                        .param("maxScore", "7")
                        .param("sortOrder", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        mockMvc.perform(post("/admin/evaluation/templates/{id}/questions/{qId}/delete", template.getId(), uploadedQuestion.getId())
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        mockMvc.perform(post("/admin/evaluation/templates/{id}/questions", template.getId())
                        .session(orgAdminSession)
                        .with(csrf())
                        .param("category", "역량")
                        .param("content", "제출용 문항-" + suffix)
                        .param("questionType", "SCALE")
                        .param("maxScore", "5")
                        .param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        EvaluationQuestion activeQuestion = questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(template.getId(), true).stream()
                .filter(q -> ("제출용 문항-" + suffix).equals(q.getContent()))
                .findFirst()
                .orElseThrow();

        // 5) 평가 세션 생성
        String sessionName = "Session-" + suffix;
        mockMvc.perform(post("/admin/evaluation/sessions")
                        .session(orgAdminSession)
                        .with(csrf())
                        .param("name", sessionName)
                        .param("description", "staging session")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().plusDays(7).toString())
                        .param("templateId", String.valueOf(template.getId()))
                        .param("allowResubmit", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions**"));

        EvaluationSession session = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(org.getId()).stream()
                .filter(s -> sessionName.equals(s.getName()))
                .findFirst()
                .orElseThrow();

        // 6) 평가 관계 자동 생성 및 수동 조정
        mockMvc.perform(post("/admin/evaluation/sessions/{id}/relationships/auto-generate", session.getId())
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/" + session.getId() + "/relationships**"));
        assertThat(relationshipRepository.countBySessionId(session.getId())).isGreaterThan(0);

        var anyRel = relationshipRepository.findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(session.getId()).stream()
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/admin/evaluation/sessions/{sid}/relationships/{rid}/delete", session.getId(), anyRel.getId())
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/" + session.getId() + "/relationships**"));

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/relationships/manual", session.getId())
                        .session(orgAdminSession)
                        .with(csrf())
                        .param("evaluatorId", String.valueOf(evaluator.getId()))
                        .param("evaluateeId", String.valueOf(evaluatee.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/" + session.getId() + "/relationships**"));

        // 7) 일반 사용자 평가 제출
        mockMvc.perform(post("/admin/evaluation/sessions/{id}/start", session.getId())
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions/" + session.getId()));

        MockHttpSession userSession = loginAs(evalLogin, "password123");
        var assignment = assignmentRepository.findByEvaluatorAndOrg(evaluator.getId(), org.getId()).stream()
                .filter(a -> a.getSessionId().equals(session.getId()) && a.getEvaluateeId().equals(evaluatee.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/user/evaluations/{assignmentId}/submit", assignment.getId())
                        .session(userSession)
                        .with(csrf())
                        .param("scores[" + activeQuestion.getId() + "]", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/evaluations/" + assignment.getId() + "/complete"));

        var submitted = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");

        // 8) 감사 로그 및 오류 추적 확인 (실패 업로드 의도 발생)
        MockMultipartFile badQuestionFile = new MockMultipartFile(
                "file", "bad_questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createInvalidQuestionExcel()
        );
        mockMvc.perform(multipart("/admin/evaluation/templates/{id}/questions/upload", template.getId())
                        .file(badQuestionFile)
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        var latestQuestionUploadHistory = uploadHistoryRepository
                .findByOrganizationIdAndUploadTypeOrderByCreatedAtDesc(org.getId(), "QUESTION")
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(latestQuestionUploadHistory.getStatus()).isEqualTo("FAILED");
        assertThat(latestQuestionUploadHistory.getErrorDetail()).contains("문항유형");

        MockMultipartFile invalidFormat = new MockMultipartFile(
                "file", "bad_questions.txt", "text/plain", "not-an-excel".getBytes()
        );
        mockMvc.perform(multipart("/admin/evaluation/templates/{id}/questions/upload", template.getId())
                        .file(invalidFormat)
                        .session(orgAdminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + template.getId() + "/questions"));

        var latestFailAudit = auditLogRepository.findAll().stream()
                .filter(a -> "EVAL_QUESTION_UPLOAD".equals(a.getAction()) && "FAIL".equals(a.getOutcome()))
                .max(Comparator.comparing(a -> a.getId()))
                .orElseThrow();
        assertThat(latestFailAudit.getOrganizationId()).isEqualTo(org.getId());
        assertThat(latestFailAudit.getRequestId()).isNotBlank();
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

    private byte[] createDepartmentExcel(List<String> deptCodes) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("departments");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("부서코드");
            header.createCell(1).setCellValue("부서명");
            header.createCell(2).setCellValue("상위부서코드");

            for (int i = 0; i < deptCodes.size(); i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(deptCodes.get(i));
                row.createCell(1).setCellValue("부서-" + deptCodes.get(i));
                row.createCell(2).setCellValue("");
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createEmployeeExcel(List<EmpRow> rows) throws IOException {
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

            int idx = 1;
            for (EmpRow rowData : rows) {
                var row = sheet.createRow(idx++);
                row.createCell(0).setCellValue(rowData.employeeNumber());
                row.createCell(1).setCellValue(rowData.name());
                row.createCell(2).setCellValue(rowData.deptCode());
                row.createCell(3).setCellValue("대리");
                row.createCell(4).setCellValue("팀원");
                row.createCell(5).setCellValue(rowData.loginId() + "@staging.local");
                row.createCell(6).setCellValue(rowData.loginId());
                row.createCell(7).setCellValue("ACTIVE");
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createQuestionExcel(String questionContent) throws IOException {
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
            row.createCell(1).setCellValue(questionContent);
            row.createCell(2).setCellValue("SCALE");
            row.createCell(3).setCellValue("5");
            row.createCell(4).setCellValue("1");
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
            row.createCell(1).setCellValue("잘못된 문항");
            row.createCell(2).setCellValue("INVALID");
            row.createCell(3).setCellValue("5");
            row.createCell(4).setCellValue("1");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private record EmpRow(String employeeNumber, String name, String deptCode, String loginId) {
    }
}
