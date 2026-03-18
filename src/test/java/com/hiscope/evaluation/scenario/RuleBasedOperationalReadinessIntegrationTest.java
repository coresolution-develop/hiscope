package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionRelationshipGenerationRunRepository;
import com.hiscope.evaluation.domain.evaluation.rule.service.RelationshipGenerationService;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleBasedOperationalReadinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeAttributeRepository employeeAttributeRepository;

    @Autowired
    private EmployeeAttributeValueRepository employeeAttributeValueRepository;

    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;

    @Autowired
    private RelationshipDefinitionRuleRepository definitionRuleRepository;

    @Autowired
    private RelationshipRuleMatcherRepository matcherRepository;

    @Autowired
    private EvaluationSessionRepository sessionRepository;

    @Autowired
    private SessionRelationshipGenerationRunRepository generationRunRepository;

    @Autowired
    private RelationshipGenerationService relationshipGenerationService;

    @Autowired
    private EvaluationRelationshipRepository relationshipRepository;

    @Test
    void 직원업로드_템플릿_최종화_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        byte[] content = mockMvc.perform(get("/admin/uploads/template/employees").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            Row headerRow = workbook.getSheet("직원").getRow(0);
            List<String> headerValues = IntStream.rangeClosed(0, headerRow.getLastCellNum() - 1)
                    .mapToObj(i -> headerRow.getCell(i).getStringCellValue())
                    .toList();
            assertThat(headerValues).anyMatch(v -> v.contains("경혁팀"));
            assertThat(headerValues).anyMatch(v -> v.contains("경혁팀장"));
            assertThat(headerValues).anyMatch(v -> v.contains("진료팀장"));
            assertThat(headerValues).anyMatch(v -> v.contains("평가제외"));
            assertThat(headerValues).anyMatch(v -> v.contains("attr:clinical_track"));
            assertThat(workbook.getSheet("가이드")).isNotNull();
            assertThat(workbook.getSheet("가이드").getRow(1).getCell(0).getStringCellValue()).isEqualTo("로그인ID");
        }
    }

    @Test
    void 직원업로드_속성연결_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(8);
        String deptCode = ("ATTR" + suffix).toUpperCase();
        departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("속성부서-" + suffix)
                .code(deptCode)
                .active(true)
                .build());

        MockMultipartFile empFile = new MockMultipartFile(
                "file",
                "employee-attribute.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createEmployeeExcelWithAttributes("UP-" + suffix, "업로더-" + suffix, deptCode, "upl" + suffix)
        );

        mockMvc.perform(multipart("/admin/uploads/employees")
                        .file(empFile)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Employee employee = employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(e -> ("UP-" + suffix).equals(e.getEmployeeNumber()))
                .findFirst()
                .orElseThrow();

        var attrs = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(1L);
        assertThat(attrs.stream().map(a -> a.getAttributeKey()))
                .contains("change_innovation_team", "clinical_team_leader", "evaluation_excluded");

        var values = employeeAttributeValueRepository.findByEmployeeIdOrderByAttributeIdAscValueTextAsc(employee.getId());
        assertThat(values).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void 직원업로드_독립속성_조합저장_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(8);
        String deptCode = ("INDEP" + suffix).toUpperCase();
        departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("독립속성부서-" + suffix)
                .code(deptCode)
                .active(true)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "employee-independent-attribute.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createEmployeeExcelWithIndependentAttributes(deptCode, suffix)
        );

        mockMvc.perform(multipart("/admin/uploads/employees")
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Employee case1 = findEmployeeByNumber("IN1-" + suffix);
        Employee case2 = findEmployeeByNumber("IN2-" + suffix);
        Employee case3 = findEmployeeByNumber("IN3-" + suffix);

        assertThat(attributeValueMap(case1.getId()))
                .containsEntry("change_innovation_team", "Y")
                .containsEntry("change_innovation_team_leader", "Y")
                .containsEntry("department_head", "N");
        assertThat(attributeValueMap(case2.getId()))
                .containsEntry("change_innovation_team", "Y")
                .containsEntry("change_innovation_team_leader", "Y")
                .containsEntry("department_head", "Y");
        assertThat(attributeValueMap(case3.getId()))
                .containsEntry("change_innovation_team", "Y")
                .containsEntry("change_innovation_team_leader", "N")
                .containsEntry("department_head", "Y");
    }

    @Test
    void RULE_BASED_실행결과_요약_이력_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(1L)
                .name("run-summary-" + System.nanoTime())
                .active(true)
                .isDefault(false)
                .createdBy(2L)
                .build());
        RelationshipDefinitionRule rule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(set.getId())
                .ruleName("summary-rule")
                .relationType(RelationshipRuleType.PEER)
                .priority(10)
                .active(true)
                .build());
        matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(rule.getId())
                .subjectType(RelationshipSubjectType.EVALUATOR)
                .matcherType(RelationshipMatcherType.EMPLOYEE)
                .operator(RelationshipRuleOperator.IN)
                .valueText("1")
                .build());
        matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(rule.getId())
                .subjectType(RelationshipSubjectType.EVALUATEE)
                .matcherType(RelationshipMatcherType.EMPLOYEE)
                .operator(RelationshipRuleOperator.IN)
                .valueText("2")
                .build());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(1L)
                .name("run-summary-session-" + System.nanoTime())
                .templateId(1L)
                .createdBy(2L)
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(set.getId())
                .status("PENDING")
                .build());

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/auto-generate", session.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        var latestRun = generationRunRepository.findBySessionIdOrderByExecutedAtDesc(session.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().orElseThrow();

        assertThat(latestRun.getStatus()).isEqualTo("SUCCESS");
        assertThat(latestRun.getGeneratedCount()).isGreaterThanOrEqualTo(1);
        assertThat(latestRun.getFinalCount()).isGreaterThanOrEqualTo(1);
        assertThat(latestRun.getExecutedByLoginId()).isEqualTo("admin");

        String html = mockMvc.perform(get("/admin/evaluation/sessions/{sessionId}/relationships", session.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("rule#");
        assertThat(html).contains("admin");
    }

    @Test
    void 검색_필터_페이징_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        definitionSetRepository.save(RelationshipDefinitionSet.builder().organizationId(1L).name("검색A-" + System.nanoTime()).active(true).isDefault(false).createdBy(2L).build());
        definitionSetRepository.save(RelationshipDefinitionSet.builder().organizationId(1L).name("검색B-" + System.nanoTime()).active(false).isDefault(false).createdBy(2L).build());

        String html = mockMvc.perform(get("/admin/settings/relationships")
                        .session(adminSession)
                        .param("setKeyword", "검색A")
                        .param("setActive", "true")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("검색A");

        String attrHtml = mockMvc.perform(get("/admin/settings/employee-attributes")
                        .session(adminSession)
                        .param("attributeKeyword", "평가")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(attrHtml).contains("직원 속성 관리");
    }

    @Test
    void 대량데이터_시뮬레이션_테스트() {
        String suffix = String.valueOf(System.nanoTime()).substring(8);
        Department department = departmentRepository.save(Department.builder()
                .organizationId(1L)
                .name("대량부서-" + suffix)
                .code(("BULK" + suffix).toUpperCase())
                .active(true)
                .build());

        List<Employee> employees = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            employees.add(Employee.builder()
                    .organizationId(1L)
                    .departmentId(department.getId())
                    .name("대량직원-" + suffix + "-" + i)
                    .employeeNumber("BULK-" + suffix + "-" + i)
                    .position("사원")
                    .jobTitle(i % 10 == 0 ? "팀장" : "팀원")
                    .email("bulk" + suffix + i + "@test.local")
                    .status("ACTIVE")
                    .build());
        }
        employeeRepository.saveAll(employees);

        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(1L)
                .name("bulk-set-" + suffix)
                .active(true)
                .isDefault(false)
                .createdBy(2L)
                .build());
        RelationshipDefinitionRule rule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(set.getId())
                .ruleName("bulk-rule")
                .relationType(RelationshipRuleType.PEER)
                .priority(10)
                .active(true)
                .build());
        matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(rule.getId())
                .subjectType(RelationshipSubjectType.EVALUATOR)
                .matcherType(RelationshipMatcherType.DEPARTMENT)
                .operator(RelationshipRuleOperator.IN)
                .valueText(String.valueOf(department.getId()))
                .build());
        matcherRepository.save(RelationshipRuleMatcher.builder()
                .ruleId(rule.getId())
                .subjectType(RelationshipSubjectType.EVALUATEE)
                .matcherType(RelationshipMatcherType.DEPARTMENT)
                .operator(RelationshipRuleOperator.IN)
                .valueText(String.valueOf(department.getId()))
                .build());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(1L)
                .name("bulk-session-" + suffix)
                .templateId(1L)
                .createdBy(2L)
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(set.getId())
                .status("PENDING")
                .build());

        var summary = relationshipGenerationService.generateForSession(1L, session.getId(), set.getId());
        assertThat(summary.generatedRelationshipCount()).isGreaterThan(100L);
    }

    @Test
    void legacy_rule_전환보조_비교_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(1L)
                .name("compare-set-" + System.nanoTime())
                .active(true)
                .isDefault(false)
                .createdBy(2L)
                .build());
        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(1L)
                .name("compare-session-" + System.nanoTime())
                .templateId(1L)
                .createdBy(2L)
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(set.getId())
                .status("PENDING")
                .build());

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/compare-legacy", session.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/*/relationships?compareCommon=*&compareOnlyRule=*&compareOnlyLegacy=*"));

        String html = mockMvc.perform(get("/admin/evaluation/sessions/{sessionId}/relationships", session.getId())
                        .session(adminSession)
                        .param("compareCommon", "0")
                        .param("compareOnlyRule", "0")
                        .param("compareOnlyLegacy", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("LEGACY 비교 요약");
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

    private byte[] createEmployeeExcelWithAttributes(String employeeNumber,
                                                     String name,
                                                     String departmentCode,
                                                     String loginId) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("employees");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("사원번호");
            header.createCell(1).setCellValue("이름");
            header.createCell(2).setCellValue("부서코드");
            header.createCell(3).setCellValue("직위");
            header.createCell(4).setCellValue("직책");
            header.createCell(5).setCellValue("이메일");
            header.createCell(6).setCellValue("로그인ID");
            header.createCell(7).setCellValue("상태");
            header.createCell(8).setCellValue("경혁팀");
            header.createCell(9).setCellValue("진료팀장");
            header.createCell(10).setCellValue("평가제외");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(employeeNumber);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(departmentCode);
            row.createCell(3).setCellValue("사원");
            row.createCell(4).setCellValue("팀원");
            row.createCell(5).setCellValue(loginId + "@test.local");
            row.createCell(6).setCellValue(loginId);
            row.createCell(7).setCellValue("ACTIVE");
            row.createCell(8).setCellValue("Y");
            row.createCell(9).setCellValue("Y");
            row.createCell(10).setCellValue("N");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createEmployeeExcelWithIndependentAttributes(String departmentCode, String suffix) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("employees");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("사원번호");
            header.createCell(1).setCellValue("이름");
            header.createCell(2).setCellValue("부서코드");
            header.createCell(3).setCellValue("직위");
            header.createCell(4).setCellValue("직책");
            header.createCell(5).setCellValue("이메일");
            header.createCell(6).setCellValue("로그인ID");
            header.createCell(7).setCellValue("상태");
            header.createCell(8).setCellValue("경혁팀");
            header.createCell(9).setCellValue("경혁팀장");
            header.createCell(10).setCellValue("부서장");

            writeIndependentAttributeRow(sheet.createRow(1), "IN1-" + suffix, "조합1-" + suffix, departmentCode, "indep1" + suffix, "Y", "Y", "N");
            writeIndependentAttributeRow(sheet.createRow(2), "IN2-" + suffix, "조합2-" + suffix, departmentCode, "indep2" + suffix, "Y", "Y", "Y");
            writeIndependentAttributeRow(sheet.createRow(3), "IN3-" + suffix, "조합3-" + suffix, departmentCode, "indep3" + suffix, "Y", "N", "Y");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private void writeIndependentAttributeRow(Row row,
                                              String employeeNumber,
                                              String name,
                                              String departmentCode,
                                              String loginId,
                                              String changeInnovationTeam,
                                              String changeInnovationTeamLeader,
                                              String departmentHead) {
        row.createCell(0).setCellValue(employeeNumber);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(departmentCode);
        row.createCell(3).setCellValue("사원");
        row.createCell(4).setCellValue("팀원");
        row.createCell(5).setCellValue(loginId + "@test.local");
        row.createCell(6).setCellValue(loginId);
        row.createCell(7).setCellValue("ACTIVE");
        row.createCell(8).setCellValue(changeInnovationTeam);
        row.createCell(9).setCellValue(changeInnovationTeamLeader);
        row.createCell(10).setCellValue(departmentHead);
    }

    private Employee findEmployeeByNumber(String employeeNumber) {
        return employeeRepository.findByOrganizationIdOrderByNameAsc(1L).stream()
                .filter(e -> employeeNumber.equals(e.getEmployeeNumber()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, String> attributeValueMap(Long employeeId) {
        Map<Long, String> attributeKeyById = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(1L)
                .stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a.getAttributeKey()));
        return employeeAttributeValueRepository.findByEmployeeIdOrderByAttributeIdAscValueTextAsc(employeeId).stream()
                .collect(Collectors.toMap(v -> attributeKeyById.get(v.getAttributeId()), v -> v.getValueText(), (a, b) -> b));
    }
}
