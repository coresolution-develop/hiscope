package com.hiscope.evaluation.scenario;

import com.hiscope.evaluation.domain.account.entity.Account;
import com.hiscope.evaluation.domain.account.repository.AccountRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionRuleRepository;
import com.hiscope.evaluation.domain.evaluation.rule.repository.RelationshipDefinitionSetRepository;
import com.hiscope.evaluation.domain.organization.dto.OrganizationCreateRequest;
import com.hiscope.evaluation.domain.organization.entity.Organization;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
import com.hiscope.evaluation.domain.organization.service.OrganizationService;
import com.hiscope.evaluation.common.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MigrationGuardrailsIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private RelationshipDefinitionSetRepository definitionSetRepository;

    @Autowired
    private RelationshipDefinitionRuleRepository definitionRuleRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void migration_insert_기관은_bootstrap_누락_탐지대상이다() {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        Organization raw = organizationRepository.save(Organization.builder()
                .name("직접적재-" + suffix)
                .code("RAW_" + suffix)
                .status("ACTIVE")
                .organizationType(OrganizationType.HOSPITAL)
                .organizationProfile(OrganizationProfile.HOSPITAL_DEFAULT)
                .build());

        assertThat(definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(raw.getId()))
                .isEmpty();
    }

    @Test
    void affiliate_hospital_프로파일_부트스트랩은_현재_계열사룰_3개다() {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        OrganizationCreateRequest request = new OrganizationCreateRequest();
        request.setName("병원계열-" + suffix);
        request.setCode("AFH_" + suffix);
        request.setOrganizationType(OrganizationType.AFFILIATE);
        request.setOrganizationProfile(OrganizationProfile.AFFILIATE_HOSPITAL);

        var created = organizationService.create(request);
        Long orgId = created.getId();
        Long setId = definitionSetRepository.findByOrganizationIdAndIsDefaultTrueAndActiveTrue(orgId)
                .orElseThrow()
                .getId();

        var rules = definitionRuleRepository.findBySetIdAndActiveTrueOrderByPriorityAscIdAsc(setId);
        assertThat(rules).hasSize(3);
        assertThat(rules).extracting("ruleName")
                .containsExactly("기관장 전사 평가", "소속장 하향 평가", "부서장 하향 평가");
    }

    @Test
    void null_org_super_admin_login_id_중복은_로그인시_명시적으로_차단된다() {
        String suffix = String.valueOf(System.nanoTime()).substring(7);
        String loginId = "superdup_" + suffix;

        accountRepository.save(Account.builder()
                .organizationId(null)
                .loginId(loginId)
                .passwordHash("$2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee")
                .name("슈퍼A")
                .email("a-" + suffix + "@test.local")
                .role("ROLE_SUPER_ADMIN")
                .status("ACTIVE")
                .build());
        accountRepository.flush();

        boolean duplicateInserted;
        try {
            accountRepository.save(Account.builder()
                    .organizationId(null)
                    .loginId(loginId)
                    .passwordHash("$2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee")
                    .name("슈퍼B")
                    .email("b-" + suffix + "@test.local")
                    .role("ROLE_SUPER_ADMIN")
                    .status("ACTIVE")
                    .build());
            accountRepository.flush();
            duplicateInserted = true;
        } catch (DataIntegrityViolationException ex) {
            duplicateInserted = false;
        }

        if (duplicateInserted) {
            assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(loginId))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("중복 슈퍼관리자 로그인ID");
        } else {
            assertThat(accountRepository.findAllByLoginIdAndStatus(loginId, "ACTIVE")).hasSize(1);
        }
    }
}
