package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttribute;
import com.hiscope.evaluation.domain.employee.attribute.entity.EmployeeAttributeValue;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeRepository;
import com.hiscope.evaluation.domain.employee.attribute.repository.EmployeeAttributeValueRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.service.EvaluationAssignmentService;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.response.service.EvaluationResponseService;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.upload.handler.QuestionUploadHandler;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuestionGroupResolutionIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private EmployeeAttributeRepository employeeAttributeRepository;
    @Autowired
    private EmployeeAttributeValueRepository employeeAttributeValueRepository;
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
    private EvaluationAssignmentService assignmentService;
    @Autowired
    private EvaluationResponseService responseService;
    @Autowired
    private QuestionUploadHandler questionUploadHandler;

    @Test
    void 병원_RULE_BASED_문항군_선택_정책_적용() {
        Fixture fixture = createFixture(OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT, "HOSP-GRP");
        seedLeaderAttributes(fixture.orgId(), fixture.headA().getId(), fixture.headB().getId());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(fixture.orgId())
                .name("병원 문항군 세션")
                .templateId(fixture.templateId())
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .build());

        relationshipRepository.saveAll(List.of(
                relationship(session.getId(), fixture.orgId(), fixture.memberA().getId(), fixture.headA().getId(), "UPWARD"),
                relationship(session.getId(), fixture.orgId(), fixture.headA().getId(), fixture.memberA().getId(), "DOWNWARD"),
                relationship(session.getId(), fixture.orgId(), fixture.headA().getId(), fixture.headB().getId(), "PEER"),
                relationship(session.getId(), fixture.orgId(), fixture.memberA().getId(), fixture.memberB().getId(), "PEER")
        ));

        assignmentService.createAssignmentsForSession(session);

        Map<String, String> codeByPair = assignmentRepository.findBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(
                        a -> a.getEvaluatorId() + "->" + a.getEvaluateeId(),
                        a -> a.getResolvedQuestionGroupCode()
                ));

        assertThat(codeByPair.get(fixture.memberA().getId() + "->" + fixture.headA().getId())).isEqualTo("AA");
        assertThat(codeByPair.get(fixture.headA().getId() + "->" + fixture.memberA().getId())).isEqualTo("AB");
        assertThat(codeByPair.get(fixture.headA().getId() + "->" + fixture.headB().getId())).isEqualTo("AA");
        assertThat(codeByPair.get(fixture.memberA().getId() + "->" + fixture.memberB().getId())).isEqualTo("AB");

        var oneAssignment = assignmentRepository.findBySessionId(session.getId()).stream()
                .filter(a -> "AA".equals(a.getResolvedQuestionGroupCode()))
                .findFirst()
                .orElseThrow();
        assertThat(responseService.getQuestionsForAssignment(fixture.orgId(), oneAssignment.getId()))
                .extracting(EvaluationQuestion::getQuestionGroupCode)
                .containsOnly("AA");
    }

    @Test
    void 계열사_병원계열_프로파일_문항군_선택_정책_적용() {
        Fixture fixture = createFixture(OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_HOSPITAL, "AFF-H-GRP");
        seedLeaderAttributes(fixture.orgId(), fixture.headA().getId(), fixture.headB().getId());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(fixture.orgId())
                .name("계열사 병원계열 세션")
                .templateId(fixture.templateId())
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .build());

        relationshipRepository.saveAll(List.of(
                relationship(session.getId(), fixture.orgId(), fixture.memberA().getId(), fixture.headA().getId(), "UPWARD"),
                relationship(session.getId(), fixture.orgId(), fixture.headA().getId(), fixture.memberA().getId(), "DOWNWARD")
        ));

        assignmentService.createAssignmentsForSession(session);
        Map<String, String> codeByPair = assignmentRepository.findBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(
                        a -> a.getEvaluatorId() + "->" + a.getEvaluateeId(),
                        a -> a.getResolvedQuestionGroupCode()
                ));

        assertThat(codeByPair.get(fixture.memberA().getId() + "->" + fixture.headA().getId())).isEqualTo("AC");
        assertThat(codeByPair.get(fixture.headA().getId() + "->" + fixture.memberA().getId())).isEqualTo("AD");
    }

    @Test
    void 계열사_일반계열_프로파일_문항군_선택_정책_적용() {
        Fixture fixture = createFixture(OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_GENERAL, "AFF-G-GRP");
        seedLeaderAttributes(fixture.orgId(), fixture.headA().getId(), fixture.headB().getId());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(fixture.orgId())
                .name("계열사 일반계열 세션")
                .templateId(fixture.templateId())
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .build());

        relationshipRepository.saveAll(List.of(
                relationship(session.getId(), fixture.orgId(), fixture.memberA().getId(), fixture.headA().getId(), "UPWARD"),
                relationship(session.getId(), fixture.orgId(), fixture.headA().getId(), fixture.memberA().getId(), "DOWNWARD")
        ));

        assignmentService.createAssignmentsForSession(session);
        Map<String, String> codeByPair = assignmentRepository.findBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(
                        a -> a.getEvaluatorId() + "->" + a.getEvaluateeId(),
                        a -> a.getResolvedQuestionGroupCode()
                ));

        assertThat(codeByPair.get(fixture.memberA().getId() + "->" + fixture.headA().getId())).isEqualTo("AC");
        assertThat(codeByPair.get(fixture.headA().getId() + "->" + fixture.memberA().getId())).isEqualTo("AE");
    }

    @Test
    void LEGACY_세션은_문항군_자동선택을_적용하지_않는다() {
        Fixture fixture = createFixture(OrganizationType.HOSPITAL, OrganizationProfile.HOSPITAL_DEFAULT, "HOSP-LEGACY");
        seedLeaderAttributes(fixture.orgId(), fixture.headA().getId(), fixture.headB().getId());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(fixture.orgId())
                .name("레거시 세션")
                .templateId(fixture.templateId())
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.LEGACY)
                .build());

        relationshipRepository.save(relationship(session.getId(), fixture.orgId(), fixture.memberA().getId(), fixture.headA().getId(), "UPWARD"));
        assignmentService.createAssignmentsForSession(session);

        var assignment = assignmentRepository.findBySessionId(session.getId()).get(0);
        assertThat(assignment.getResolvedQuestionGroupCode()).isNull();
        assertThat(responseService.getQuestionsForAssignment(fixture.orgId(), assignment.getId()))
                .extracting(EvaluationQuestion::getQuestionGroupCode)
                .contains("AA", "AB");
    }

    @Test
    void 문제은행_업로드시_프로파일별_허용되지_않은_문항군코드는_오류처리된다() throws Exception {
        Fixture fixture = createFixture(OrganizationType.AFFILIATE, OrganizationProfile.AFFILIATE_GENERAL, "AFF-UPLOAD");
        MockMultipartFile invalid = new MockMultipartFile(
                "file",
                "question-invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createQuestionWorkbook("AD")
        );

        var result = questionUploadHandler.handle(fixture.orgId(), fixture.templateId(), invalid);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).contains("허용되지 않는 문항군 코드");
    }

    private Fixture createFixture(OrganizationType type, OrganizationProfile profile, String prefix) {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        Organization org = organizationRepository.save(Organization.builder()
                .name(prefix + "-" + suffix)
                .code(prefix + "_" + suffix)
                .status("ACTIVE")
                .organizationType(type)
                .organizationProfile(profile)
                .build());
        Department dept = departmentRepository.save(Department.builder()
                .organizationId(org.getId())
                .name("부서-" + suffix)
                .code("D" + suffix)
                .active(true)
                .build());
        Employee headA = createEmployee(org.getId(), dept.getId(), "부서장A-" + suffix, "EHA-" + suffix);
        Employee headB = createEmployee(org.getId(), dept.getId(), "부서장B-" + suffix, "EHB-" + suffix);
        Employee memberA = createEmployee(org.getId(), dept.getId(), "부서원A-" + suffix, "EMA-" + suffix);
        Employee memberB = createEmployee(org.getId(), dept.getId(), "부서원B-" + suffix, "EMB-" + suffix);

        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(org.getId())
                .name("템플릿-" + suffix)
                .description("qgroup")
                .active(true)
                .build());
        questionRepository.save(EvaluationQuestion.builder().organizationId(org.getId()).templateId(template.getId()).category("공통").content("AA 문항").questionType("SCALE").questionGroupCode("AA").maxScore(5).sortOrder(1).active(true).build());
        questionRepository.save(EvaluationQuestion.builder().organizationId(org.getId()).templateId(template.getId()).category("공통").content("AB 문항").questionType("SCALE").questionGroupCode("AB").maxScore(5).sortOrder(2).active(true).build());
        questionRepository.save(EvaluationQuestion.builder().organizationId(org.getId()).templateId(template.getId()).category("공통").content("AC 문항").questionType("SCALE").questionGroupCode("AC").maxScore(5).sortOrder(3).active(true).build());
        questionRepository.save(EvaluationQuestion.builder().organizationId(org.getId()).templateId(template.getId()).category("공통").content("AD 문항").questionType("SCALE").questionGroupCode("AD").maxScore(5).sortOrder(4).active(true).build());
        questionRepository.save(EvaluationQuestion.builder().organizationId(org.getId()).templateId(template.getId()).category("공통").content("AE 문항").questionType("SCALE").questionGroupCode("AE").maxScore(5).sortOrder(5).active(true).build());

        return new Fixture(org.getId(), template.getId(), headA, headB, memberA, memberB);
    }

    private Employee createEmployee(Long orgId, Long deptId, String name, String empNo) {
        return employeeRepository.save(Employee.builder()
                .organizationId(orgId)
                .departmentId(deptId)
                .name(name)
                .employeeNumber(empNo)
                .position("사원")
                .jobTitle("팀원")
                .status("ACTIVE")
                .build());
    }

    private void seedLeaderAttributes(Long orgId, Long... leaderEmployeeIds) {
        EmployeeAttribute deptHead = employeeAttributeRepository.findByOrganizationIdAndAttributeKey(orgId, "department_head")
                .orElseGet(() -> employeeAttributeRepository.save(EmployeeAttribute.builder()
                        .organizationId(orgId)
                        .attributeKey("department_head")
                        .attributeName("부서장")
                        .active(true)
                        .build()));
        for (Long employeeId : leaderEmployeeIds) {
            employeeAttributeValueRepository.save(EmployeeAttributeValue.builder()
                    .employeeId(employeeId)
                    .attributeId(deptHead.getId())
                    .valueText("Y")
                    .build());
        }
    }

    private EvaluationRelationship relationship(Long sessionId, Long orgId, Long evaluatorId, Long evaluateeId, String type) {
        return EvaluationRelationship.builder()
                .sessionId(sessionId)
                .organizationId(orgId)
                .evaluatorId(evaluatorId)
                .evaluateeId(evaluateeId)
                .relationType(type)
                .source("AUTO_GENERATED")
                .active(true)
                .build();
    }

    private byte[] createQuestionWorkbook(String groupCode) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("문제은행");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("카테고리");
            header.createCell(1).setCellValue("문항내용(필수)");
            header.createCell(2).setCellValue("문항유형(SCALE/DESCRIPTIVE)(필수)");
            header.createCell(3).setCellValue("문항군코드(선택:AA/AB)");
            header.createCell(4).setCellValue("최대점수(SCALE필수)");
            header.createCell(5).setCellValue("정렬순서");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("공통");
            row.createCell(1).setCellValue("테스트 문항");
            row.createCell(2).setCellValue("SCALE");
            row.createCell(3).setCellValue(groupCode);
            row.createCell(4).setCellValue("5");
            row.createCell(5).setCellValue("1");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private record Fixture(Long orgId,
                           Long templateId,
                           Employee headA,
                           Employee headB,
                           Employee memberA,
                           Employee memberB) {
    }
}
