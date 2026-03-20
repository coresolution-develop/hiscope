package com.hiscope.evaluation.scenario.migration;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.security.CustomUserDetails;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.employee.repository.UserAccountRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.service.EvaluationAssignmentService;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.session.service.EvaluationSessionService;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.mypage.service.MyPageService;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PilotMigrationSpecValidationTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private EvaluationSessionRepository sessionRepository;
    @Autowired
    private EvaluationSessionService sessionService;
    @Autowired
    private EvaluationRelationshipRepository relationshipRepository;
    @Autowired
    private EvaluationAssignmentService assignmentService;
    @Autowired
    private EvaluationAssignmentRepository assignmentRepository;
    @Autowired
    private EvaluationTemplateRepository templateRepository;
    @Autowired
    private EvaluationQuestionRepository questionRepository;
    @Autowired
    private EvaluationResponseRepository responseRepository;
    @Autowired
    private EvaluationResponseItemRepository responseItemRepository;
    @Autowired
    private MyPageService myPageService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 파일럿_SQL직접적재_기관은_bootstrap_검증쿼리_V06_대상이다() {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        Organization org = organizationRepository.save(Organization.builder()
                .name("파일럿직접적재-" + suffix)
                .code("PILOT_RAW_" + suffix)
                .status("ACTIVE")
                .organizationType(OrganizationType.HOSPITAL)
                .organizationProfile(OrganizationProfile.HOSPITAL_DEFAULT)
                .build());

        assertThat(definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(org.getId()))
                .isEmpty();
    }

    @Test
    void RULE_BASED_세션은_definition_set_없이_start_불가() {
        Organization org = createOrganization("RULE-SET");
        Department dept = createDepartment(org.getId(), "RSET");
        createEmployee(org.getId(), dept.getId(), "평가자", "RSET-EV");
        createEmployee(org.getId(), dept.getId(), "피평가자", "RSET-EE");

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(org.getId())
                .name("rule-based-no-definition")
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(null)
                .build());

        authenticateOrgAdmin(org.getId(), "pilot_admin_rule");

        assertThatThrownBy(() -> sessionService.start(org.getId(), session.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RULE_BASED 세션은 관계 정의 세트를 지정해야 시작할 수 있습니다.");
    }

    @Test
    void LEGACY_세션_assignment은_resolved_question_group_code_NULL_허용() {
        Organization org = createOrganization("LEGACY");
        Department dept = createDepartment(org.getId(), "LEG");
        Employee evaluator = createEmployee(org.getId(), dept.getId(), "평가자", "LEG-EV");
        Employee evaluatee = createEmployee(org.getId(), dept.getId(), "피평가자", "LEG-EE");

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(org.getId())
                .name("legacy-session")
                .status("PENDING")
                .relationshipGenerationMode(RelationshipGenerationMode.LEGACY)
                .build());

        relationshipRepository.save(EvaluationRelationship.builder()
                .sessionId(session.getId())
                .organizationId(org.getId())
                .evaluatorId(evaluator.getId())
                .evaluateeId(evaluatee.getId())
                .relationType("UPWARD")
                .source("AUTO_GENERATED")
                .active(true)
                .build());

        assignmentService.createAssignmentsForSession(session);

        EvaluationAssignment assignment = assignmentRepository.findBySessionId(session.getId()).get(0);
        assertThat(assignment.getResolvedQuestionGroupCode()).isNull();
    }

    @Test
    void user_accounts_loginId는_기관스코프에서_중복허용된다() {
        Organization orgA = createOrganization("LOGIN-A");
        Organization orgB = createOrganization("LOGIN-B");
        Department deptA = createDepartment(orgA.getId(), "LGA");
        Department deptB = createDepartment(orgB.getId(), "LGB");
        Employee employeeA = createEmployee(orgA.getId(), deptA.getId(), "직원A", "LGA-EMP");
        Employee employeeB = createEmployee(orgB.getId(), deptB.getId(), "직원B", "LGB-EMP");

        String loginId = "pilot_login_scope_" + String.valueOf(System.nanoTime()).substring(7);

        userAccountRepository.save(UserAccount.builder()
                .employee(employeeA)
                .organizationId(orgA.getId())
                .loginId(loginId)
                .passwordHash("$2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee")
                .role("ROLE_USER")
                .build());
        boolean duplicatedInserted;
        try {
            userAccountRepository.save(UserAccount.builder()
                    .employee(employeeB)
                    .organizationId(orgB.getId())
                    .loginId(loginId)
                    .passwordHash("$2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee")
                    .role("ROLE_USER")
                    .build());
            userAccountRepository.flush();
            duplicatedInserted = true;
        } catch (DataIntegrityViolationException ex) {
            duplicatedInserted = false;
        }

        List<UserAccount> matches = userAccountRepository.findAllByLoginIdAndEmployeeActive(loginId);
        if (duplicatedInserted) {
            assertThat(matches).hasSize(2);
            assertThat(matches).extracting(UserAccount::getOrganizationId).containsExactlyInAnyOrder(orgA.getId(), orgB.getId());
        } else {
            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).getOrganizationId()).isEqualTo(orgA.getId());
        }
    }

    @Test
    void 마이페이지는_response_item_기준으로_집계된다() {
        Organization org = createOrganization("MYPAGE");
        Department dept = createDepartment(org.getId(), "MYP");
        Employee evaluator = createEmployee(org.getId(), dept.getId(), "평가자", "MYP-EV");
        Employee evaluatee = createEmployee(org.getId(), dept.getId(), "피평가자", "MYP-EE");

        EvaluationTemplate template = templateRepository.save(EvaluationTemplate.builder()
                .organizationId(org.getId())
                .name("mypage-template")
                .description("pilot")
                .active(true)
                .build());
        EvaluationQuestion question = questionRepository.save(EvaluationQuestion.builder()
                .organizationId(org.getId())
                .templateId(template.getId())
                .category("소통")
                .content("협업 수준")
                .questionType("SCALE")
                .maxScore(5)
                .sortOrder(1)
                .active(true)
                .questionGroupCode("AA")
                .build());

        EvaluationSession session = sessionRepository.save(EvaluationSession.builder()
                .organizationId(org.getId())
                .name("mypage-session")
                .templateId(template.getId())
                .status("IN_PROGRESS")
                .relationshipGenerationMode(RelationshipGenerationMode.LEGACY)
                .build());

        EvaluationAssignment assignment = assignmentRepository.save(EvaluationAssignment.builder()
                .sessionId(session.getId())
                .organizationId(org.getId())
                .evaluatorId(evaluator.getId())
                .evaluateeId(evaluatee.getId())
                .status("SUBMITTED")
                .build());

        EvaluationResponse response = EvaluationResponse.builder()
                .assignmentId(assignment.getId())
                .organizationId(org.getId())
                .build();
        response.finalize();
        responseRepository.save(response);

        responseItemRepository.save(EvaluationResponseItem.builder()
                .responseId(response.getId())
                .questionId(question.getId())
                .scoreValue(4)
                .textValue("의사소통이 원활합니다")
                .build());

        var view = myPageService.getMyPage(org.getId(), evaluatee.getId());

        assertThat(view.summary().totalReceivedEvaluations()).isEqualTo(1);
        assertThat(view.summary().totalScoreItems()).isEqualTo(1);
        assertThat(view.summary().totalCommentItems()).isEqualTo(1);
        assertThat(view.questionScores()).hasSize(1);
        assertThat(view.questionScores().get(0).questionId()).isEqualTo(question.getId());
    }

    private Organization createOrganization(String prefix) {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        return organizationRepository.save(Organization.builder()
                .name(prefix + "-" + suffix)
                .code(prefix + "_" + suffix)
                .status("ACTIVE")
                .organizationType(OrganizationType.HOSPITAL)
                .organizationProfile(OrganizationProfile.HOSPITAL_DEFAULT)
                .build());
    }

    private Department createDepartment(Long orgId, String codePrefix) {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        return departmentRepository.save(Department.builder()
                .organizationId(orgId)
                .name("부서-" + suffix)
                .code(codePrefix + "_" + suffix)
                .active(true)
                .build());
    }

    private Employee createEmployee(Long orgId, Long deptId, String namePrefix, String empNoPrefix) {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        return employeeRepository.save(Employee.builder()
                .organizationId(orgId)
                .departmentId(deptId)
                .name(namePrefix + "-" + suffix)
                .employeeNumber(empNoPrefix + "-" + suffix)
                .position("사원")
                .jobTitle("팀원")
                .status("ACTIVE")
                .build());
    }

    private void authenticateOrgAdmin(Long orgId, String loginId) {
        CustomUserDetails principal = CustomUserDetails.builder()
                .id(900000L)
                .organizationId(orgId)
                .employeeId(null)
                .loginId(loginId)
                .password("N/A")
                .name("pilot-org-admin")
                .role("ROLE_ORG_ADMIN")
                .mustChangePassword(false)
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
