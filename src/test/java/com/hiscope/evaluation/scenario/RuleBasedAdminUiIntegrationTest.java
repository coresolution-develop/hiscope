package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionRule;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipRuleMatcher;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipMatcherType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleOperator;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipRuleType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipSubjectType;
import com.hiscope.evaluation.domain.evaluation.rule.enums.SessionRelationshipOverrideAction;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipRuleMatcherRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionRelationshipOverrideRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleBasedAdminUiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;

    @Autowired
    private RelationshipDefinitionRuleRepository definitionRuleRepository;

    @Autowired
    private RelationshipRuleMatcherRepository matcherRepository;

    @Autowired
    private EvaluationSessionRepository sessionRepository;

    @Autowired
    private SessionRelationshipOverrideRepository overrideRepository;

    @Autowired
    private EvaluationRelationshipRepository relationshipRepository;

    @Test
    void 관계정의_화면_권한_테스트() throws Exception {
        MockHttpSession userSession = loginAs("emp001", "password123");

        mockMvc.perform(get("/admin/settings/relationships").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관계정의_세트_생성_csrf_검증_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        mockMvc.perform(post("/admin/settings/relationships/sets")
                        .session(adminSession)
                        .param("name", "csrf-fail-set"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/settings/relationships/sets")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "ui-set-" + System.nanoTime())
                        .param("active", "true")
                        .param("defaultSet", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/settings/relationships?setId=*"));

        assertThat(definitionSetRepository.findByOrganizationIdOrderByNameAsc(1L))
                .anyMatch(RelationshipDefinitionSet::isDefault);
    }

    @Test
    void 관계정의_룰_매처_CRUD_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");
        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(1L)
                .name("crud-set-" + System.nanoTime())
                .active(true)
                .isDefault(false)
                .createdBy(2L)
                .build());

        mockMvc.perform(post("/admin/settings/relationships/sets/{setId}/rules", set.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("ruleName", "crud-rule")
                        .param("relationType", "PEER")
                        .param("priority", "12")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/settings/relationships?setId=*ruleId=*"));

        RelationshipDefinitionRule rule = definitionRuleRepository.findBySetIdOrderByPriorityAscIdAsc(set.getId()).get(0);

        mockMvc.perform(post("/admin/settings/relationships/sets/{setId}/rules/{ruleId}", set.getId(), rule.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("ruleName", "crud-rule-updated")
                        .param("relationType", "CROSS_DEPT")
                        .param("priority", "30")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/settings/relationships/sets/{setId}/rules/{ruleId}/matchers", set.getId(), rule.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("subjectType", "EVALUATOR")
                        .param("matcherType", "DEPARTMENT")
                        .param("operator", "IN")
                        .param("valueText", "3"))
                .andExpect(status().is3xxRedirection());

        RelationshipRuleMatcher matcher = matcherRepository.findByRuleIdOrderByIdAsc(rule.getId()).get(0);

        mockMvc.perform(post("/admin/settings/relationships/sets/{setId}/rules/{ruleId}/matchers/{matcherId}", set.getId(), rule.getId(), matcher.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("subjectType", "EVALUATOR")
                        .param("matcherType", "DEPARTMENT")
                        .param("operator", "NOT_IN")
                        .param("valueText", "4"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/settings/relationships/sets/{setId}/rules/{ruleId}/matchers/{matcherId}/delete", set.getId(), rule.getId(), matcher.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/settings/relationships/sets/{setId}/rules/{ruleId}/delete", set.getId(), rule.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings/relationships?setId=" + set.getId()));

        assertThat(definitionRuleRepository.findBySetIdOrderByPriorityAscIdAsc(set.getId())).isEmpty();
    }

    @Test
    void RULE_BASED_세션_생성_수정_UI_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        mockMvc.perform(post("/admin/evaluation/sessions")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", "invalid-rule-based-" + System.nanoTime())
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().plusDays(7).toString())
                        .param("templateId", "1")
                        .param("relationshipGenerationMode", "RULE_BASED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions?page=0&size=20&sortBy=createdAt&sortDir=desc"));

        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(1L)
                .name("session-set-" + System.nanoTime())
                .active(true)
                .isDefault(false)
                .createdBy(2L)
                .build());

        String sessionName = "rule-based-session-" + System.nanoTime();
        mockMvc.perform(post("/admin/evaluation/sessions")
                        .session(adminSession)
                        .with(csrf())
                        .param("name", sessionName)
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().plusDays(7).toString())
                        .param("templateId", "1")
                        .param("relationshipGenerationMode", "RULE_BASED")
                        .param("relationshipDefinitionSetId", String.valueOf(set.getId())))
                .andExpect(status().is3xxRedirection());

        EvaluationSession created = sessionRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).stream()
                .filter(s -> sessionName.equals(s.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(created.getRelationshipGenerationMode()).isEqualTo(RelationshipGenerationMode.RULE_BASED);
        assertThat(created.getRelationshipDefinitionSetId()).isEqualTo(set.getId());

        mockMvc.perform(post("/admin/evaluation/sessions/{id}/update", created.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("name", created.getName())
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().plusDays(10).toString())
                        .param("templateId", "1")
                        .param("relationshipGenerationMode", "LEGACY")
                        .param("allowResubmit", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/evaluation/sessions/" + created.getId()));

        EvaluationSession updated = sessionRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getRelationshipGenerationMode()).isEqualTo(RelationshipGenerationMode.LEGACY);
        assertThat(updated.getRelationshipDefinitionSetId()).isNull();
    }

    @Test
    void override_화면_동작_테스트() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "password123");

        RelationshipDefinitionSet set = definitionSetRepository.save(RelationshipDefinitionSet.builder()
                .organizationId(1L)
                .name("override-set-" + System.nanoTime())
                .active(true)
                .isDefault(false)
                .createdBy(2L)
                .build());

        RelationshipDefinitionRule rule = definitionRuleRepository.save(RelationshipDefinitionRule.builder()
                .setId(set.getId())
                .ruleName("override-rule")
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
                .name("override-session-" + System.nanoTime())
                .templateId(1L)
                .createdBy(2L)
                .relationshipGenerationMode(RelationshipGenerationMode.RULE_BASED)
                .relationshipDefinitionSetId(set.getId())
                .status("PENDING")
                .build());

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/auto-generate", session.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/evaluation/sessions/" + session.getId() + "/relationships**"));

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/manual", session.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("evaluatorId", "2")
                        .param("evaluateeId", "3"))
                .andExpect(status().is3xxRedirection());

        Long relId = relationshipRepository.findBySessionIdOrderByRelationTypeAscEvaluatorIdAsc(session.getId()).stream()
                .filter(r -> r.getEvaluatorId().equals(1L) && r.getEvaluateeId().equals(2L))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/admin/evaluation/sessions/{sessionId}/relationships/{id}/delete", session.getId(), relId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(overrideRepository.findBySessionIdOrderByIdAsc(session.getId()))
                .extracting("action")
                .contains(SessionRelationshipOverrideAction.ADD, SessionRelationshipOverrideAction.REMOVE);
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
}
