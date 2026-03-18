package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionGeneratedRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.service.RelationshipGenerationService;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationTypeProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EmployeeAttributeRepository employeeAttributeRepository;

    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeAttributeValueRepository employeeAttributeValueRepository;

    @Autowired
    private EvaluationSessionRepository sessionRepository;

    @Autowired
    private RelationshipGenerationService relationshipGenerationService;

    @Autowired
    private SessionGeneratedRelationshipRepository generatedRelationshipRepository;

    @Test
    void 기관생성시_HOSPITAL_프로파일_자동적용() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        String code = "HOSP_" + suffix;

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", "병원기관-" + suffix)
                        .param("code", code)
                        .param("organizationType", "HOSPITAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations"));

        Organization org = organizationRepository.findByCode(code).orElseThrow();
        assertThat(org.getOrganizationType()).isEqualTo(OrganizationType.HOSPITAL);

        var keys = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(org.getId())
                .stream().map(a -> a.getAttributeKey()).toList();
        assertThat(keys).contains(
                "institution_head", "unit_head", "department_head", "evaluation_excluded",
                "change_innovation_team", "change_innovation_team_leader", "single_member_department",
                "clinical_team_leader", "medical_leader"
        );

        var defaultSet = definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(org.getId()).orElseThrow();
        assertThat(defaultSet.getName()).contains("병원 기본");
    }

    @Test
    void 기관생성시_AFFILIATE_프로파일_자동적용() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        String code = "AFF_" + suffix;

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", "계열기관-" + suffix)
                        .param("code", code)
                        .param("organizationType", "AFFILIATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/organizations"));

        Organization org = organizationRepository.findByCode(code).orElseThrow();
        assertThat(org.getOrganizationType()).isEqualTo(OrganizationType.AFFILIATE);

        var keys = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(org.getId())
                .stream().map(a -> a.getAttributeKey()).toList();
        assertThat(keys).contains("institution_head", "unit_head", "department_head", "evaluation_excluded", "affiliate_policy_group");
        assertThat(keys).doesNotContain("clinical_team_leader", "change_innovation_team");

        var defaultSet = definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(org.getId()).orElseThrow();
        assertThat(defaultSet.getName()).contains("계열사 기본");
    }

    @Test
    void 조직유형별_직원업로드_템플릿_분리제공() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        byte[] hospitalContent = mockMvc.perform(get("/admin/uploads/template/employees/hospital")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] affiliateContent = mockMvc.perform(get("/admin/uploads/template/employees/affiliate")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        List<String> hospitalHeaders = extractHeaders(hospitalContent);
        List<String> affiliateHeaders = extractHeaders(affiliateContent);

        assertThat(hospitalHeaders).contains("진료팀장(Y/N)", "경혁팀(Y/N)");
        assertThat(affiliateHeaders).doesNotContain("진료팀장(Y/N)", "경혁팀(Y/N)");
        assertThat(affiliateHeaders).contains("attr:affiliate_policy_group(선택)");
    }

    @Test
    void 병원_기본_rule_set_표본군_검증() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        String code = "HCHK_" + suffix;

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", "병원검증-" + suffix)
                        .param("code", code)
                        .param("organizationType", "HOSPITAL"))
                .andExpect(status().is3xxRedirection());

        Organization org = organizationRepository.findByCode(code).orElseThrow();
        Long orgId = org.getId();
        Department dept = departmentRepository.save(Department.builder()
                .organizationId(orgId)
                .name("검증부서-" + suffix)
                .code("D" + suffix)
                .active(true)
                .build());

        Employee institutionHead = createEmployee(orgId, dept.getId(), "기관장", "H-I-" + suffix);
        Employee unitHead = createEmployee(orgId, dept.getId(), "소속장", "H-U-" + suffix);
        Employee departmentHead = createEmployee(orgId, dept.getId(), "부서장", "H-D-" + suffix);
        Employee changeLeader = createEmployee(orgId, dept.getId(), "경혁팀장", "H-C1-" + suffix);
        Employee changeMember = createEmployee(orgId, dept.getId(), "경혁팀원", "H-C2-" + suffix);
        Employee clinicalLeader = createEmployee(orgId, dept.getId(), "진료팀장", "H-CL-" + suffix);
        Employee singleDept = createEmployee(orgId, dept.getId(), "1인부서직원", "H-S-" + suffix);
        Employee excluded = createEmployee(orgId, dept.getId(), "제외직원", "H-X-" + suffix);
        Employee normal = createEmployee(orgId, dept.getId(), "일반직원", "H-N-" + suffix);

        upsertAttr(orgId, institutionHead.getId(), "institution_head", "Y");
        upsertAttr(orgId, unitHead.getId(), "unit_head", "Y");
        upsertAttr(orgId, departmentHead.getId(), "department_head", "Y");
        upsertAttr(orgId, changeLeader.getId(), "change_innovation_team_leader", "Y");
        upsertAttr(orgId, changeLeader.getId(), "change_innovation_team", "Y");
        upsertAttr(orgId, changeMember.getId(), "change_innovation_team", "Y");
        upsertAttr(orgId, clinicalLeader.getId(), "clinical_team_leader", "Y");
        upsertAttr(orgId, normal.getId(), "clinical_team_leader", "N");
        upsertAttr(orgId, singleDept.getId(), "single_member_department", "Y");
        upsertAttr(orgId, excluded.getId(), "evaluation_excluded", "Y");

        Long setId = definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(orgId).orElseThrow().getId();
        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(orgId)
                .name("병원룰검증-" + suffix)
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(setId)
                .build());

        relationshipGenerationService.generateForSession(orgId, session.getId(), setId);
        var generated = generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId());
        Set<String> pairs = generated.stream()
                .map(r -> r.getEvaluatorId() + "_" + r.getEvaluateeId())
                .collect(Collectors.toSet());
        Set<Long> excludedIds = generated.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getEvaluatorId(), r.getEvaluateeId()))
                .collect(Collectors.toSet());

        assertThat(generated).isNotEmpty();
        assertThat(pairs).contains(institutionHead.getId() + "_" + normal.getId());
        assertThat(pairs).contains(unitHead.getId() + "_" + singleDept.getId());
        assertThat(pairs).contains(changeLeader.getId() + "_" + changeMember.getId());
        assertThat(pairs).contains(clinicalLeader.getId() + "_" + normal.getId());
        assertThat(excludedIds).doesNotContain(excluded.getId());
    }

    @Test
    void 계열사_기본_rule_set_표본군_검증() throws Exception {
        MockHttpSession superSession = loginAs("super", "password123");
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        String code = "ACHK_" + suffix;

        mockMvc.perform(post("/super-admin/organizations")
                        .session(superSession)
                        .with(csrf())
                        .param("name", "계열사검증-" + suffix)
                        .param("code", code)
                        .param("organizationType", "AFFILIATE"))
                .andExpect(status().is3xxRedirection());

        Organization org = organizationRepository.findByCode(code).orElseThrow();
        Long orgId = org.getId();
        Department dept = departmentRepository.save(Department.builder()
                .organizationId(orgId)
                .name("계열부서-" + suffix)
                .code("A" + suffix)
                .active(true)
                .build());

        Employee institutionHead = createEmployee(orgId, dept.getId(), "기관장", "A-I-" + suffix);
        Employee unitHead = createEmployee(orgId, dept.getId(), "소속장", "A-U-" + suffix);
        Employee departmentHead = createEmployee(orgId, dept.getId(), "부서장", "A-D-" + suffix);
        Employee normal = createEmployee(orgId, dept.getId(), "일반직원", "A-N-" + suffix);
        Employee excluded = createEmployee(orgId, dept.getId(), "제외직원", "A-X-" + suffix);

        upsertAttr(orgId, institutionHead.getId(), "institution_head", "Y");
        upsertAttr(orgId, unitHead.getId(), "unit_head", "Y");
        upsertAttr(orgId, departmentHead.getId(), "department_head", "Y");
        upsertAttr(orgId, excluded.getId(), "evaluation_excluded", "Y");

        Long setId = definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(orgId).orElseThrow().getId();
        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(orgId)
                .name("계열사룰검증-" + suffix)
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(setId)
                .build());

        relationshipGenerationService.generateForSession(orgId, session.getId(), setId);
        var generated = generatedRelationshipRepository.findBySessionIdOrderByEvaluatorIdAscEvaluateeIdAsc(session.getId());
        Set<String> pairs = generated.stream()
                .map(r -> r.getEvaluatorId() + "_" + r.getEvaluateeId())
                .collect(Collectors.toSet());
        Set<Long> linkedIds = generated.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getEvaluatorId(), r.getEvaluateeId()))
                .collect(Collectors.toSet());

        assertThat(generated).isNotEmpty();
        assertThat(pairs).contains(institutionHead.getId() + "_" + normal.getId());
        assertThat(pairs).contains(unitHead.getId() + "_" + normal.getId());
        assertThat(pairs).contains(departmentHead.getId() + "_" + normal.getId());
        assertThat(linkedIds).doesNotContain(excluded.getId());
    }

    private List<String> extractHeaders(byte[] bytes) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Row headerRow = workbook.getSheet("직원").getRow(0);
            return java.util.stream.IntStream.range(0, headerRow.getLastCellNum())
                    .mapToObj(i -> headerRow.getCell(i).getStringCellValue())
                    .toList();
        }
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

    private Employee createEmployee(Long orgId, Long deptId, String name, String employeeNumber) {
        return employeeRepository.save(Employee.builder()
                .organizationId(orgId)
                .departmentId(deptId)
                .name(name)
                .employeeNumber(employeeNumber)
                .position("사원")
                .jobTitle("팀원")
                .status("ACTIVE")
                .build());
    }

    private void upsertAttr(Long orgId, Long employeeId, String attributeKey, String value) {
        Map<String, EmployeeAttribute> attributeMap = employeeAttributeRepository.findByOrganizationIdOrderByAttributeNameAsc(orgId)
                .stream()
                .collect(Collectors.toMap(EmployeeAttribute::getAttributeKey, a -> a));
        EmployeeAttribute attribute = attributeMap.get(attributeKey);
        assertThat(attribute).isNotNull();
        var current = employeeAttributeValueRepository.findByEmployeeIdAndAttributeId(employeeId, attribute.getId());
        EmployeeAttributeValue saved = current.map(v -> {
            v.updateValue(value);
            return v;
        }).orElseGet(() -> EmployeeAttributeValue.builder()
                .employeeId(employeeId)
                .attributeId(attribute.getId())
                .valueText(value)
                .build());
        employeeAttributeValueRepository.save(saved);
    }
}
