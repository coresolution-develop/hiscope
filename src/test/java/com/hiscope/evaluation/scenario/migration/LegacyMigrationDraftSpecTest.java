package com.hiscope.evaluation.scenario.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMigrationDraftSpecTest {

    @Test
    void 레거시_실측_매핑문서에_파일럿기관과_문항키규칙이_포함되어야_한다() throws IOException {
        String content = readProjectFile("docs/migration/LEGACY_REAL_MAPPING.md");

        assertThat(content)
                .contains("효사랑가족요양병원")
                .contains("evaluation(idx, d1, d2, d3, eval_year)")
                .contains("나39")
                .contains("목41")
                .contains("t43")
                .contains("data_ev")
                .contains("data_type")
                .contains("eval_type_code")
                .contains("`A` = 진료팀장 -> 진료부")
                .contains("`G` = 부서원 -> 부서원")
                .contains("`GH_TO_GH` = 경혁팀 -> 경혁팀")
                .contains("`SUB_MEMBER_TO_MEMBER` = 부서원 -> 부서원")
                .contains("`GH` = 평가대상: 경혁팀")
                .contains("`MEDICAL` = 평가대상: 진료부")
                .contains("AA` = 10문항 세트")
                .contains("AB` = 20문항 세트")
                .contains("team_head` = 경혁팀장")
                .contains("team_member` = 경혁팀원")
                .contains("sub_head` = 부서장")
                .contains("sub_member` = 부서원")
                .contains("one_person_sub` = 1인부서")
                .contains("매우우수`=5")
                .contains("매우미흡`=1")
                .contains("admin_default_targets")
                .contains("admin_custom_targets")
                .contains("evaluation_submissions")
                .contains("evaluation_comment_summary")
                .contains("kpi_eval_clinic")
                .contains("kpi_personal_2025")
                .contains("kpi_info_general_2025")
                .contains("notice_v2")
                .contains("확정")
                .contains("가정")
                .contains("미확정");
    }

    @Test
    void 파일럿추출_SQL은_기관명_실측컬럼기준_필터를_포함해야_한다() throws IOException {
        String content = readProjectFile("scripts/migration/extract_pilot_org_from_legacy.sql");

        assertThat(content)
                .contains("효사랑가족요양병원")
                .contains("users_2025")
                .contains("c_name")
                .contains("c_name2")
                .contains("hospital_name")
                .contains("stg_legacy_evaluation")
                .contains("stg_legacy_pilot_user_keys")
                .contains("stg_legacy_evaluation_submissions");
    }

    @Test
    void 제출변환_SQL은_삭제활성버전정책과_문항키중간매핑을_포함해야_한다() throws IOException {
        String content = readProjectFile("scripts/migration/transform_legacy_submissions.sql");

        assertThat(content)
                .contains("UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N'")
                .contains("ROW_NUMBER() OVER")
                .contains("map_legacy_question_key")
                .contains("map_legacy_radio_label_score")
                .contains("stg_legacy_evaluation")
                .contains("WHEN btrim(e.d3) = '섬김' THEN '섬'")
                .contains("WHEN btrim(e.d3) = '목표관리' THEN '목'")
                .contains("WHEN btrim(e.d3) = '주관식' THEN 't'")
                .contains("QUESTION_KEY_NOT_IN_EVALUATION_MASTER")
                .contains("evaluation_responses")
                .contains("evaluation_response_items")
                .contains("ON CONFLICT (response_id, question_id)")
                .contains("A=진료팀장->진료부")
                .contains("G=부서원->부서원")
                .contains("GH_TO_GH")
                .contains("SUB_MEMBER_TO_MEMBER");
    }

    @Test
    void 코드사전요청_문서는_미확정코드와_반영대상_SQL을_명시해야_한다() throws IOException {
        String content = readProjectFile("docs/migration/CODE_DICTIONARY_REQUIRED.md");

        assertThat(content)
                .contains("data_ev")
                .contains("eval_type_code")
                .contains("A=진료팀장->진료부")
                .contains("G=부서원->부서원")
                .contains("GH_TO_GH=경혁팀->경혁팀")
                .contains("SUB_MEMBER_TO_MEMBER=부서원->부서원")
                .contains("AA=10문항 세트")
                .contains("AB=20문항 세트")
                .contains("평가 관계 코드")
                .contains("평가자->평가대상자 관계 의미 코드")
                .contains("매우우수=5")
                .contains("매우미흡=1")
                .contains("team_head=경혁팀장")
                .contains("sub_head=부서장")
                .contains("Radio label -> raw score")
                .contains("user_roles_2025.role")
                .contains("확정 필요")
                .contains("현재 아는 것")
                .contains("미확정")
                .contains("결정 시 반영 대상 SQL")
                .contains("create_radio_score_mapping_template.sql")
                .contains("validate_assignment_linking_candidates.sql");
    }

    @Test
    void assignment_linking_정책문서와_검증_SQL이_핵심키와_파일럿필터를_포함해야_한다() throws IOException {
        String policyContent = readProjectFile("docs/migration/ASSIGNMENT_LINKING_POLICY.md");
        String radioTemplateContent = readProjectFile("scripts/migration/create_radio_score_mapping_template.sql");
        String linkingValidationContent = readProjectFile("scripts/migration/validate_assignment_linking_candidates.sql");

        assertThat(policyContent)
                .contains("eval_year + evaluator_id + target_id + data_ev + data_type")
                .contains("data_ev")
                .contains("eval_type_code")
                .contains("A` = 진료팀장 -> 진료부")
                .contains("G` = 부서원 -> 부서원")
                .contains("GH_TO_GH` = 경혁팀 -> 경혁팀")
                .contains("SUB_MEMBER_TO_MEMBER` = 부서원 -> 부서원")
                .contains("세션 분리 운영")
                .contains("validate_assignment_linking_candidates.sql")
                .contains("효사랑가족요양병원");

        assertThat(radioTemplateContent)
                .contains("map_legacy_radio_label_score")
                .contains("'AA', '매우우수', 5")
                .contains("'AA', '매우미흡', 1")
                .contains("'AB', '매우우수', 5")
                .contains("'AB', '매우미흡', 1")
                .contains("raw Likert score; AA/AB conversion handled separately")
                .contains("radio_label")
                .contains("효사랑가족요양병원");

        assertThat(linkingValidationContent)
                .contains("eval_year, evaluator_id, target_id, data_ev, data_type")
                .contains("unique_groups")
                .contains("duplicate_groups")
                .contains("active_conflict_groups")
                .contains("unknown_data_ev_groups")
                .contains("GH_TO_GH")
                .contains("SUB_MEMBER_TO_MEMBER")
                .contains("효사랑가족요양병원");

        String transformContent = readProjectFile("scripts/migration/transform_legacy_submissions.sql");
        assertThat(transformContent)
                .contains("score_value 는 raw Likert score(1~5)")
                .contains("'AA', '매우우수', 5")
                .contains("'AB', '매우미흡', 1")
                .contains("raw_score_value")
                .contains("converted_item_score_value");
    }

    @Test
    void gap_분석문서는_data_ev_eval_type_code를_확정으로_반영하고_잔여미확정을_두가지로_제한해야_한다() throws IOException {
        String content = readProjectFile("docs/migration/LEGACY_GAP_ANALYSIS.md");

        assertThat(content)
                .contains("data_ev=A~G` 코드표 | 확정")
                .contains("eval_type_code` 코드표 | 확정")
                .contains("A=진료팀장->진료부")
                .contains("G=부서원->부서원")
                .contains("GH_TO_GH")
                .contains("SUB_MEMBER_TO_MEMBER")
                .contains("`evaluation_submissions` -> `sessions/assignments` 추가 보강키 | 미확정")
                .contains("`user_roles_2025.role` -> 신규 속성키 완전 매핑 | 미확정")
                .doesNotContain("data_ev=A~G` 상세 semantics | 미확정")
                .doesNotContain("eval_type_code` 전체 사전 | 미확정");
    }

    @Test
    void 파일럿_실행_SQL_초안은_조직역할관계제출_단계를_모두_포함해야_한다() throws IOException {
        String masterSql = readProjectFile("scripts/migration/migrate_pilot_org_master.sql");
        String roleSql = readProjectFile("scripts/migration/migrate_pilot_roles_attributes.sql");
        String relationshipSql = readProjectFile("scripts/migration/migrate_pilot_relationships.sql");
        String submissionSql = readProjectFile("scripts/migration/migrate_pilot_submissions_to_responses.sql");

        assertThat(masterSql)
                .contains("효사랑가족요양병원")
                .contains("stg_legacy_users_2025")
                .contains("stg_legacy_sub_management")
                .contains("map_employee")
                .contains("map_question")
                .contains("stg_pilot_master_unresolved");

        assertThat(roleSql)
                .contains("map_legacy_role_attribute_policy")
                .contains("team_head")
                .contains("sub_head")
                .contains("one_person_sub")
                .contains("medical_leader")
                .contains("stg_pilot_role_mapping_todo")
                .contains("stg_pilot_roles_unresolved");

        assertThat(relationshipSql)
                .contains("A', '진료팀장 -> 진료부")
                .contains("G', '부서원 -> 부서원")
                .contains("GH_TO_GH")
                .contains("SUB_MEMBER_TO_MEMBER")
                .contains("ADMIN_DEFAULT_TARGETS_KEY_COLUMNS_MISSING")
                .contains("SUBMISSION_FALLBACK")
                .contains("stg_legacy_admin_default_targets")
                .contains("eval_year")
                .contains("data_ev")
                .contains("data_type")
                .contains("map_assignment")
                .contains("stg_pilot_relationships_unresolved");

        assertThat(submissionSql)
                .contains("evaluation_submissions")
                .contains("map_legacy_radio_label_score")
                .contains("'AA', '매우우수', 5")
                .contains("'AB', '매우미흡', 1")
                .contains("score_value")
                .contains("raw_score_value")
                .contains("converted_item_score_value")
                .contains("try_parse_jsonb")
                .contains("ANSWERS_JSON_PARSE_FAILED")
                .contains("stg_pilot_submission_item_unresolved");
    }

    @Test
    void CSV_적재스크립트는_실헤더기반_copy와_staging정규화를_포함해야_한다() throws IOException {
        String content = readProjectFile("scripts/migration/load_pilot_csv_to_staging.sql");

        assertThat(content)
                .contains("효사랑가족요양병원")
                .contains("raw_legacy_admin_default_targets")
                .contains("eval_type_code TEXT")
                .contains("\\copy migration_staging.raw_legacy_users_2025 FROM '/Users/leesumin/users_2025_202603191619.csv'")
                .contains("\\copy migration_staging.raw_legacy_evaluation_submissions FROM '/Users/leesumin/evaluation_submissions_202603191651.csv'")
                .contains("NULL ''")
                .contains("stg_legacy_admin_default_targets")
                .contains("stg_legacy_admin_custom_targets")
                .contains("stg_legacy_evaluation_submissions")
                .contains("try_parse_jsonb")
                .contains("invalid_answers_json_rows")
                .contains("kpi_info_general_2025 CSV는 현재 미제공");
    }

    private String readProjectFile(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        assertThat(Files.exists(path)).as("missing file: %s", relativePath).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
