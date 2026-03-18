package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.service.EvaluationResponseService;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.upload.dto.EmployeeUploadPreview;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperatorE2ERehearsalIntegrationTest {

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
    private UserAccountRepository userAccountRepository;
    @Autowired
    private EvaluationTemplateRepository templateRepository;
    @Autowired
    private EvaluationSessionRepository sessionRepository;
    @Autowired
    private EvaluationAssignmentRepository assignmentRepository;
    @Autowired
    private EvaluationResponseService responseService;
    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;
    @Autowired
    private RelationshipDefinitionRuleRepository ruleRepository;
    @Autowired
    private RelationshipRuleMatcherRepository matcherRepository;

    @Test
    void HOSPITAL_DEFAULT_운영자_E2E_리허설() throws Exception {
        OrgAdminFixture fixture = createOrgAndAdmin("HOSP_E2E", OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT);
        assertThat(fixture.organization().getOrganizationType()).isEqualTo(OrganizationType.HOSPITAL);
        assertThat(fixture.organization().getOrganizationProfile()).isEqualTo(OrganizationProfile.HOSPITAL_DEFAULT);

        assertOperatorUxVisibility(fixture.adminSession());
        Department baseDept = departmentRepository.save(Department.builder()
                .organizationId(fixture.organization().getId())
                .name("병원기준부서")
                .code("HBASE_" + fixture.suffix())
                .active(true)
                .build());

        MockMultipartFile employeeOpsFile = new MockMultipartFile(
                "file",
                "hospital_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsEmployeeWorkbook(
                        true,
                        false,
                        List.of(
                                new EmployeeRow("HHEAD1_" + fixture.suffix(), "병원부서장1", baseDept.getName(), "Y", "N", "Y", "N", "N", "N", "N", "N", null),
                                new EmployeeRow("HHEAD2_" + fixture.suffix(), "병원부서장2", baseDept.getName(), "N", "N", "Y", "N", "N", "N", "N", "N", null),
                                new EmployeeRow("HMEM1_" + fixture.suffix(), "병원직원1", baseDept.getName(), "N", "N", "N", "N", "N", "Y", "N", "N", null),
                                new EmployeeRow("HMEM2_" + fixture.suffix(), "병원직원2", baseDept.getName(), "N", "N", "N", "N", "N", "Y", "N", "N", null)
                        )
                )
        );

        EmployeeUploadPreview preview = previewEmployees(fixture.adminSession(), employeeOpsFile);
        assertThat(preview.getDetectedFileType()).isEqualTo("OPERATIONS_HOSPITAL");
        assertThat(preview.getImportProfile()).isEqualTo("OPS_HOSPITAL");
        assertThat(preview.isUploadable()).isTrue();

        UploadResult uploadResult = uploadEmployees(fixture.adminSession(), employeeOpsFile);
        assertThat(uploadResult.getStatus()).isIn("SUCCESS", "PARTIAL");
        assertThat(uploadResult.getSuccessRows()).isEqualTo(4);
        assertThat(userAccountRepository.findByOrganizationIdAndLoginId(fixture.organization().getId(), "HHEAD1_" + fixture.suffix())).isPresent();

        UploadResult deptUpload = uploadDepartments(fixture.adminSession(), new MockMultipartFile(
                "file",
                "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentWorkbook(List.of(
                        new String[]{"HNEW_" + fixture.suffix(), "병원신규부서", ""},
                        new String[]{"HSUB_" + fixture.suffix(), "병원하위부서", "HNEW_" + fixture.suffix()}
                ))
        ));
        assertThat(deptUpload.getStatus()).isIn("SUCCESS", "PARTIAL");

        UploadResult mismatch = uploadEmployees(fixture.adminSession(), new MockMultipartFile(
                "file",
                "employee_mismatch.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardEmployeeWorkbookWithDeptCodeAndName(
                        "HMM_" + fixture.suffix(),
                        "병원오류직원",
                        "HBASE_" + fixture.suffix(),
                        "병원신규부서"
                )
        ));
        assertThat(mismatch.getStatus()).isEqualTo("FAILED");
        assertThat(mismatch.getErrors().stream().map(e -> e.getMessage()))
                .anyMatch(msg -> msg.contains("부서코드와 부서명이 서로 다른 부서를 가리킵니다"));

        Long templateId = createTemplate(fixture.adminSession(), fixture.organization().getId(), "병원 E2E 템플릿-" + fixture.suffix());
        UploadResult invalidQuestionUpload = uploadQuestions(
                fixture.adminSession(),
                templateId,
                createQuestionWorkbook(List.of("AC"))
        );
        assertThat(invalidQuestionUpload.getStatus()).isEqualTo("FAILED");
        assertThat(invalidQuestionUpload.getErrors().stream().map(e -> e.getMessage()))
                .anyMatch(msg -> msg.contains("허용되지 않는 문항군 코드"));

        UploadResult validQuestionUpload = uploadQuestions(
                fixture.adminSession(),
                templateId,
                createQuestionWorkbook(List.of("AA", "AB"))
        );
        assertThat(validQuestionUpload.getStatus()).isIn("SUCCESS", "PARTIAL");

        Map<String, Employee> employeeByNumber = employeeRepository.findByOrganizationIdOrderByNameAsc(fixture.organization().getId()).stream()
                .filter(e -> e.getEmployeeNumber().endsWith("_" + fixture.suffix()))
                .collect(Collectors.toMap(Employee::getEmployeeNumber, e -> e));

        Long setId = createDefinitionSetWithRules(
                fixture.organization().getId(),
                fixture.adminAccount().getId(),
                List.of(
                        new RuleSpec(RelationshipRuleType.UPWARD, employeeByNumber.get("HMEM1_" + fixture.suffix()).getId(), employeeByNumber.get("HHEAD1_" + fixture.suffix()).getId()),
                        new RuleSpec(RelationshipRuleType.DOWNWARD, employeeByNumber.get("HHEAD1_" + fixture.suffix()).getId(), employeeByNumber.get("HMEM1_" + fixture.suffix()).getId()),
                        new RuleSpec(RelationshipRuleType.PEER, employeeByNumber.get("HHEAD1_" + fixture.suffix()).getId(), employeeByNumber.get("HHEAD2_" + fixture.suffix()).getId()),
                        new RuleSpec(RelationshipRuleType.PEER, employeeByNumber.get("HMEM1_" + fixture.suffix()).getId(), employeeByNumber.get("HMEM2_" + fixture.suffix()).getId())
                )
        );

        Long sessionId = createRuleBasedSession(
                fixture.adminSession(),
                fixture.organization().getId(),
                templateId,
                setId,
                "병원 E2E 세션-" + fixture.suffix()
        );
        String sessionDetailHtml = mockMvc.perform(get("/admin/evaluation/sessions/{id}", sessionId).session(fixture.adminSession()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(sessionDetailHtml).contains("assignment 생성/세션 시작 운영 안내");
        assertThat(sessionDetailHtml).contains("저장/실행 전 확인");

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/auto-generate", sessionId)
                        .session(fixture.adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String relationshipHtml = mockMvc.perform(get("/admin/evaluation/sessions/{id}/relationships", sessionId).session(fixture.adminSession()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(relationshipHtml).contains("관계 자동 생성 화면 안내");
        assertThat(relationshipHtml).contains("resolved_question_group_code");

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/start", sessionId)
                        .session(fixture.adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions/" + sessionId));

        List<EvaluationAssignment> assignments = assignmentRepository.findBySessionId(sessionId);
        assertThat(assignments).hasSize(4);
        assertAssignmentCode(assignments, employeeByNumber, "HMEM1_" + fixture.suffix(), "HHEAD1_" + fixture.suffix(), "AA");
        assertAssignmentCode(assignments, employeeByNumber, "HHEAD1_" + fixture.suffix(), "HMEM1_" + fixture.suffix(), "AB");
        assertAssignmentCode(assignments, employeeByNumber, "HHEAD1_" + fixture.suffix(), "HHEAD2_" + fixture.suffix(), "AA");
        assertAssignmentCode(assignments, employeeByNumber, "HMEM1_" + fixture.suffix(), "HMEM2_" + fixture.suffix(), "AB");
        assertAssignmentQuestionGroupSnapshot(fixture.organization().getId(), assignments);
    }

    @Test
    void AFFILIATE_HOSPITAL_운영자_E2E_리허설() throws Exception {
        OrgAdminFixture fixture = createOrgAndAdmin("AFH_E2E", OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_HOSPITAL);
        assertThat(fixture.organization().getOrganizationType()).isEqualTo(OrganizationType.AFFILIATE);
        assertThat(fixture.organization().getOrganizationProfile()).isEqualTo(OrganizationProfile.AFFILIATE_HOSPITAL);

        Department baseDept = departmentRepository.save(Department.builder()
                .organizationId(fixture.organization().getId())
                .name("병원계열기준부서")
                .code("ABASE_" + fixture.suffix())
                .active(true)
                .build());

        MockMultipartFile employeeOpsFile = new MockMultipartFile(
                "file",
                "affiliate_hospital_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsEmployeeWorkbook(
                        false,
                        true,
                        List.of(
                                new EmployeeRow("AHLEAD_" + fixture.suffix(), "계열사리더", baseDept.getName(), "N", "N", "Y", "N", null, null, null, null, "Y"),
                                new EmployeeRow("AHMEM_" + fixture.suffix(), "계열사직원", baseDept.getName(), "N", "N", "N", "N", null, null, null, null, "Y")
                        )
                )
        );
        EmployeeUploadPreview preview = previewEmployees(fixture.adminSession(), employeeOpsFile);
        assertThat(preview.getDetectedFileType()).isEqualTo("OPERATIONS_AFFILIATE_HOSPITAL");
        assertThat(preview.getImportProfile()).isEqualTo("OPS_AFFILIATE_HOSPITAL");

        UploadResult uploadResult = uploadEmployees(fixture.adminSession(), employeeOpsFile);
        assertThat(uploadResult.getSuccessRows()).isEqualTo(2);

        UploadResult deptUpload = uploadDepartments(fixture.adminSession(), new MockMultipartFile(
                "file",
                "dept_aff_h.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentWorkbook(java.util.Collections.singletonList(
                        new String[]{"AHNEW_" + fixture.suffix(), "병원계열신규부서", ""}))
        ));
        assertThat(deptUpload.getStatus()).isIn("SUCCESS", "PARTIAL");

        UploadResult mismatch = uploadEmployees(fixture.adminSession(), new MockMultipartFile(
                "file",
                "employee_mismatch_affh.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardEmployeeWorkbookWithDeptCodeAndName(
                        "AHMM_" + fixture.suffix(),
                        "계열병원오류",
                        "ABASE_" + fixture.suffix(),
                        "병원계열신규부서"
                )
        ));
        assertThat(mismatch.getStatus()).isEqualTo("FAILED");

        Long templateId = createTemplate(fixture.adminSession(), fixture.organization().getId(), "병원계열 E2E 템플릿-" + fixture.suffix());
        UploadResult invalidQuestionUpload = uploadQuestions(
                fixture.adminSession(),
                templateId,
                createQuestionWorkbook(List.of("AE"))
        );
        assertThat(invalidQuestionUpload.getStatus()).isEqualTo("FAILED");

        UploadResult validQuestionUpload = uploadQuestions(
                fixture.adminSession(),
                templateId,
                createQuestionWorkbook(List.of("AC", "AD"))
        );
        assertThat(validQuestionUpload.getStatus()).isIn("SUCCESS", "PARTIAL");

        Map<String, Employee> employeeByNumber = employeeRepository.findByOrganizationIdOrderByNameAsc(fixture.organization().getId()).stream()
                .filter(e -> e.getEmployeeNumber().endsWith("_" + fixture.suffix()))
                .collect(Collectors.toMap(Employee::getEmployeeNumber, e -> e));
        Long setId = createDefinitionSetWithRules(
                fixture.organization().getId(),
                fixture.adminAccount().getId(),
                List.of(
                        new RuleSpec(RelationshipRuleType.PEER, employeeByNumber.get("AHMEM_" + fixture.suffix()).getId(), employeeByNumber.get("AHLEAD_" + fixture.suffix()).getId()),
                        new RuleSpec(RelationshipRuleType.PEER, employeeByNumber.get("AHLEAD_" + fixture.suffix()).getId(), employeeByNumber.get("AHMEM_" + fixture.suffix()).getId())
                )
        );
        Long sessionId = createRuleBasedSession(
                fixture.adminSession(),
                fixture.organization().getId(),
                templateId,
                setId,
                "병원계열 E2E 세션-" + fixture.suffix()
        );

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/auto-generate", sessionId)
                        .session(fixture.adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/evaluation/sessions/{id}/start", sessionId)
                        .session(fixture.adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<EvaluationAssignment> assignments = assignmentRepository.findBySessionId(sessionId);
        assertThat(assignments).hasSize(2);
        assertAssignmentCode(assignments, employeeByNumber, "AHMEM_" + fixture.suffix(), "AHLEAD_" + fixture.suffix(), "AC");
        assertAssignmentCode(assignments, employeeByNumber, "AHLEAD_" + fixture.suffix(), "AHMEM_" + fixture.suffix(), "AD");
        assertAssignmentQuestionGroupSnapshot(fixture.organization().getId(), assignments);
    }

    @Test
    void AFFILIATE_GENERAL_운영자_E2E_리허설() throws Exception {
        OrgAdminFixture fixture = createOrgAndAdmin("AFG_E2E", OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_GENERAL);
        assertThat(fixture.organization().getOrganizationType()).isEqualTo(OrganizationType.AFFILIATE);
        assertThat(fixture.organization().getOrganizationProfile()).isEqualTo(OrganizationProfile.AFFILIATE_GENERAL);

        Department baseDept = departmentRepository.save(Department.builder()
                .organizationId(fixture.organization().getId())
                .name("일반계열기준부서")
                .code("GBASE_" + fixture.suffix())
                .active(true)
                .build());

        MockMultipartFile employeeOpsFile = new MockMultipartFile(
                "file",
                "affiliate_general_ops.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createOpsEmployeeWorkbook(
                        false,
                        true,
                        List.of(
                                new EmployeeRow("AGLEAD_" + fixture.suffix(), "일반계열리더", baseDept.getName(), "N", "N", "Y", "N", null, null, null, null, "Y"),
                                new EmployeeRow("AGMEM_" + fixture.suffix(), "일반계열직원", baseDept.getName(), "N", "N", "N", "N", null, null, null, null, "Y")
                        )
                )
        );
        EmployeeUploadPreview preview = previewEmployees(fixture.adminSession(), employeeOpsFile);
        assertThat(preview.getDetectedFileType()).isEqualTo("OPERATIONS_AFFILIATE_GENERAL");
        assertThat(preview.getImportProfile()).isEqualTo("OPS_AFFILIATE_GENERAL");

        UploadResult uploadResult = uploadEmployees(fixture.adminSession(), employeeOpsFile);
        assertThat(uploadResult.getSuccessRows()).isEqualTo(2);

        UploadResult deptUpload = uploadDepartments(fixture.adminSession(), new MockMultipartFile(
                "file",
                "dept_aff_g.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createDepartmentWorkbook(java.util.Collections.singletonList(
                        new String[]{"AGNEW_" + fixture.suffix(), "일반계열신규부서", ""}))
        ));
        assertThat(deptUpload.getStatus()).isIn("SUCCESS", "PARTIAL");

        UploadResult mismatch = uploadEmployees(fixture.adminSession(), new MockMultipartFile(
                "file",
                "employee_mismatch_affg.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createStandardEmployeeWorkbookWithDeptCodeAndName(
                        "AGMM_" + fixture.suffix(),
                        "일반계열오류",
                        "GBASE_" + fixture.suffix(),
                        "일반계열신규부서"
                )
        ));
        assertThat(mismatch.getStatus()).isEqualTo("FAILED");

        Long templateId = createTemplate(fixture.adminSession(), fixture.organization().getId(), "일반계열 E2E 템플릿-" + fixture.suffix());
        UploadResult invalidQuestionUpload = uploadQuestions(
                fixture.adminSession(),
                templateId,
                createQuestionWorkbook(List.of("AD"))
        );
        assertThat(invalidQuestionUpload.getStatus()).isEqualTo("FAILED");

        UploadResult validQuestionUpload = uploadQuestions(
                fixture.adminSession(),
                templateId,
                createQuestionWorkbook(List.of("AC", "AE"))
        );
        assertThat(validQuestionUpload.getStatus()).isIn("SUCCESS", "PARTIAL");

        Map<String, Employee> employeeByNumber = employeeRepository.findByOrganizationIdOrderByNameAsc(fixture.organization().getId()).stream()
                .filter(e -> e.getEmployeeNumber().endsWith("_" + fixture.suffix()))
                .collect(Collectors.toMap(Employee::getEmployeeNumber, e -> e));
        Long setId = createDefinitionSetWithRules(
                fixture.organization().getId(),
                fixture.adminAccount().getId(),
                List.of(
                        new RuleSpec(RelationshipRuleType.PEER, employeeByNumber.get("AGMEM_" + fixture.suffix()).getId(), employeeByNumber.get("AGLEAD_" + fixture.suffix()).getId()),
                        new RuleSpec(RelationshipRuleType.PEER, employeeByNumber.get("AGLEAD_" + fixture.suffix()).getId(), employeeByNumber.get("AGMEM_" + fixture.suffix()).getId())
                )
        );
        Long sessionId = createRuleBasedSession(
                fixture.adminSession(),
                fixture.organization().getId(),
                templateId,
                setId,
                "일반계열 E2E 세션-" + fixture.suffix()
        );

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/auto-generate", sessionId)
                        .session(fixture.adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/evaluation/sessions/{id}/start", sessionId)
                        .session(fixture.adminSession())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<EvaluationAssignment> assignments = assignmentRepository.findBySessionId(sessionId);
        assertThat(assignments).hasSize(2);
        assertAssignmentCode(assignments, employeeByNumber, "AGMEM_" + fixture.suffix(), "AGLEAD_" + fixture.suffix(), "AC");
        assertAssignmentCode(assignments, employeeByNumber, "AGLEAD_" + fixture.suffix(), "AGMEM_" + fixture.suffix(), "AE");
        assertAssignmentQuestionGroupSnapshot(fixture.organization().getId(), assignments);
    }

    private void assertOperatorUxVisibility(MockHttpSession adminSession) throws Exception {
        String employeeHtml = mockMvc.perform(get("/admin/employees").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(employeeHtml).contains("직원 관리/업로드 운영 안내");
        assertThat(employeeHtml).contains("부서 매칭 정책");

        String sessionHtml = mockMvc.perform(get("/admin/evaluation/sessions").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(sessionHtml).contains("세션 생성/수정 운영 안내");
    }

    private EmployeeUploadPreview previewEmployees(MockHttpSession adminSession, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/admin/uploads/employees/preview")
                        .file(file)
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"))
                .andReturn();
        return (EmployeeUploadPreview) result.getFlashMap().get("employeeUploadPreview");
    }

    private UploadResult uploadEmployees(MockHttpSession adminSession, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/admin/uploads/employees")
                        .file(file)
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/employees"))
                .andReturn();
        return (UploadResult) result.getFlashMap().get("uploadResult");
    }

    private UploadResult uploadDepartments(MockHttpSession adminSession, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/admin/uploads/departments")
                        .file(file)
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"))
                .andReturn();
        return (UploadResult) result.getFlashMap().get("uploadResult");
    }

    private UploadResult uploadQuestions(MockHttpSession adminSession, Long templateId, byte[] workbookBytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes
        );
        MvcResult result = mockMvc.perform(multipart("/admin/evaluation/templates/{id}/questions/upload", templateId)
                        .file(file)
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates/" + templateId + "/questions"))
                .andReturn();
        return (UploadResult) result.getFlashMap().get("uploadResult");
    }

    private Long createTemplate(MockHttpSession adminSession, Long orgId, String templateName) throws Exception {
        mockMvc.perform(post("/admin/evaluation/templates")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", templateName)
                        .param("description", "E2E template"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/templates"));
        return templateRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .filter(t -> templateName.equals(t.getName()))
                .map(EvaluationTemplate::getId)
                .findFirst()
                .orElseThrow();
    }

    private Long createRuleBasedSession(MockHttpSession adminSession,
                                        Long orgId,
                                        Long templateId,
                                        Long setId,
                                        String sessionName) throws Exception {
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusDays(7);
        mockMvc.perform(post("/admin/evaluation/sessions")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", sessionName)
                        .param("description", "E2E rehearsal session")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                        .param("templateId", String.valueOf(templateId))
                        .param("relationshipGenerationMode", "RULE_BASED")
                        .param("relationshipDefinitionSetId", String.valueOf(setId))
                        .param("allowResubmit", "false"))
                .andExpect(status().is3xxRedirection());
        return sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .filter(s -> sessionName.equals(s.getName()))
                .map(EvaluationSession::getId)
                .findFirst()
                .orElseThrow();
    }

    private Long createDefinitionSetWithRules(Long orgId, Long createdBy, List<RuleSpec> specs) {
        String suffix = String.valueOf(System.nanoTime());
        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(orgId)
                .name("E2E-SET-" + suffix)
                .active(true)
                .isDefault(false)
                .createdBy(createdBy)
                .build());

        int priority = 1;
        for (RuleSpec spec : specs) {
            RelationshipDefinitionRule rule = ruleRepository.save(RelationshipDefinitionRule.builder()
                    .setId(set.getId())
                    .ruleName(spec.relationType().name() + "-" + priority)
                    .relationType(spec.relationType())
                    .priority(priority++)
                    .active(true)
                    .build());
            matcherRepository.save(RelationshipRuleMatcher.builder()
                    .ruleId(rule.getId())
                    .subjectType(RelationshipSubjectType.EVALUATOR)
                    .matcherType(RelationshipMatcherType.EMPLOYEE)
                    .operator(RelationshipRuleOperator.IN)
                    .valueText(String.valueOf(spec.evaluatorId()))
                    .build());
            matcherRepository.save(RelationshipRuleMatcher.builder()
                    .ruleId(rule.getId())
                    .subjectType(RelationshipSubjectType.EVALUATEE)
                    .matcherType(RelationshipMatcherType.EMPLOYEE)
                    .operator(RelationshipRuleOperator.IN)
                    .valueText(String.valueOf(spec.evaluateeId()))
                    .build());
        }
        return set.getId();
    }

    private void assertAssignmentCode(List<EvaluationAssignment> assignments,
                                      Map<String, Employee> employeeByNumber,
                                      String evaluatorNumber,
                                      String evaluateeNumber,
                                      String expectedCode) {
        Long evaluatorId = employeeByNumber.get(evaluatorNumber).getId();
        Long evaluateeId = employeeByNumber.get(evaluateeNumber).getId();
        EvaluationAssignment assignment = assignments.stream()
                .filter(a -> a.getEvaluatorId().equals(evaluatorId) && a.getEvaluateeId().equals(evaluateeId))
                .findFirst()
                .orElseThrow();
        assertThat(assignment.getResolvedQuestionGroupCode()).isEqualTo(expectedCode);
    }

    private void assertAssignmentQuestionGroupSnapshot(Long orgId, List<EvaluationAssignment> assignments) {
        assignments.forEach(assignment -> {
            String code = assignment.getResolvedQuestionGroupCode();
            assertThat(code).isNotBlank();
            assertThat(responseService.getQuestionsForAssignment(orgId, assignment.getId()))
                    .extracting(q -> q.getQuestionGroupCode())
                    .containsOnly(code);
        });
    }

    private OrgAdminFixture createOrgAndAdmin(String prefix,
                                              OrganizationType organizationType,
                                              OrganizationProfile profile) throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(6);
        String code = (prefix + "_" + suffix).toUpperCase();

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", prefix + "-ORG-" + suffix)
                        .param("code", code)
                        .param("organizationType", organizationType.name())
                        .param("organizationProfile", profile.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations"));

        Organization org = organizationRepository.findByCode(code).orElseThrow();
        String adminLoginId = ("adm_" + prefix + "_" + suffix).toLowerCase();
        mockMvc.perform(post("/super-admin/organizations/{id}/admins", org.getId())
                        .session(superSession)
                        .with(csrf())
                        .param("name", "관리자-" + suffix)
                        .param("loginId", adminLoginId)
                        .param("password", "password123")
                        .param("email", adminLoginId + "@e2e.local"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations/" + org.getId()));

        Account admin = accountRepository.findByOrganizationIdAndLoginId(org.getId(), adminLoginId).orElseThrow();
        MockHttpSession adminSession = loginAs(adminLoginId, "password123");
        return new OrgAdminFixture(org, admin, adminSession, suffix);
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

    private byte[] createOpsEmployeeWorkbook(boolean hospitalColumns,
                                             boolean affiliateColumns,
                                             List<EmployeeRow> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("직원명부");
            var header = sheet.createRow(1); // B2 시작 운영파일 패턴
            int c = 1;
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
            if (hospitalColumns) {
                header.createCell(c++).setCellValue("경혁팀장여부");
                header.createCell(c++).setCellValue("경혁팀여부");
                header.createCell(c++).setCellValue("1인부서여부");
                header.createCell(c++).setCellValue("의료리더여부");
            }
            if (affiliateColumns) {
                header.createCell(c++).setCellValue("평가대상여부");
            }

            int rowIndex = 2;
            for (EmployeeRow rowData : rows) {
                var row = sheet.createRow(rowIndex++);
                c = 1;
                row.createCell(c++).setCellValue("기관");
                row.createCell(c++).setCellValue("소속기관");
                row.createCell(c++).setCellValue(rowData.departmentName());
                row.createCell(c++).setCellValue(rowData.employeeNumber());
                row.createCell(c++).setCellValue("팀원");
                row.createCell(c++).setCellValue(rowData.name());
                row.createCell(c++).setCellValue("2023-01-01");
                row.createCell(c++).setCellValue("");
                row.createCell(c++).setCellValue("010-0000-0000");
                row.createCell(c++).setCellValue(rowData.institutionHead());
                row.createCell(c++).setCellValue(rowData.unitHead());
                row.createCell(c++).setCellValue(rowData.departmentHead());
                row.createCell(c++).setCellValue(rowData.excluded());
                if (hospitalColumns) {
                    row.createCell(c++).setCellValue(rowData.changeLeader() == null ? "N" : rowData.changeLeader());
                    row.createCell(c++).setCellValue(rowData.changeTeam() == null ? "N" : rowData.changeTeam());
                    row.createCell(c++).setCellValue(rowData.singleDept() == null ? "N" : rowData.singleDept());
                    row.createCell(c++).setCellValue(rowData.medicalLeader() == null ? "N" : rowData.medicalLeader());
                }
                if (affiliateColumns) {
                    row.createCell(c++).setCellValue(rowData.evaluationTarget() == null ? "Y" : rowData.evaluationTarget());
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createStandardEmployeeWorkbookWithDeptCodeAndName(String empNo,
                                                                      String name,
                                                                      String deptCode,
                                                                      String deptName) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("직원");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("이름");
            header.createCell(1).setCellValue("사원번호");
            header.createCell(2).setCellValue("부서코드");
            header.createCell(3).setCellValue("부서명");
            header.createCell(4).setCellValue("직위");
            header.createCell(5).setCellValue("직책");
            header.createCell(6).setCellValue("이메일");
            header.createCell(7).setCellValue("로그인ID");
            header.createCell(8).setCellValue("상태");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(name);
            row.createCell(1).setCellValue(empNo);
            row.createCell(2).setCellValue(deptCode);
            row.createCell(3).setCellValue(deptName);
            row.createCell(4).setCellValue("사원");
            row.createCell(5).setCellValue("팀원");
            row.createCell(6).setCellValue(empNo + "@example.com");
            row.createCell(7).setCellValue("u" + empNo.substring(Math.max(0, empNo.length() - 10)));
            row.createCell(8).setCellValue("ACTIVE");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createDepartmentWorkbook(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("부서");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("부서코드");
            header.createCell(1).setCellValue("부서명");
            header.createCell(2).setCellValue("상위부서코드");

            int rowIndex = 1;
            for (String[] row : rows) {
                var r = sheet.createRow(rowIndex++);
                r.createCell(0).setCellValue(row[0]);
                r.createCell(1).setCellValue(row[1]);
                r.createCell(2).setCellValue(row[2]);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createQuestionWorkbook(List<String> questionGroupCodes) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("문항");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("카테고리");
            header.createCell(1).setCellValue("문항내용(필수)");
            header.createCell(2).setCellValue("문항유형(SCALE/DESCRIPTIVE)(필수)");
            header.createCell(3).setCellValue("문항군코드(선택)");
            header.createCell(4).setCellValue("최대점수(SCALE필수)");
            header.createCell(5).setCellValue("정렬순서");

            int rowIndex = 1;
            for (String code : questionGroupCodes) {
                var row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue("공통");
                row.createCell(1).setCellValue("문항-" + code);
                row.createCell(2).setCellValue("SCALE");
                row.createCell(3).setCellValue(code);
                row.createCell(4).setCellValue("5");
                row.createCell(5).setCellValue(String.valueOf(rowIndex));
                rowIndex++;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private record OrgAdminFixture(Organization organization,
                                   Account adminAccount,
                                   MockHttpSession adminSession,
                                   String suffix) {
    }

    private record EmployeeRow(String employeeNumber,
                               String name,
                               String departmentName,
                               String institutionHead,
                               String unitHead,
                               String departmentHead,
                               String excluded,
                               String changeLeader,
                               String changeTeam,
                               String singleDept,
                               String medicalLeader,
                               String evaluationTarget) {
    }

    private record RuleSpec(RelationshipRuleType relationType,
                            Long evaluatorId,
                            Long evaluateeId) {
    }
}
