package com.hiscope.evaluation.config;

import com.hiscope.evaluation.common.util.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 앱 시작 시 엑셀 업로드용 템플릿 파일 생성
 * src/main/resources/static/excel-templates/ 에 생성
 */
@Slf4j
@Component
@Order(1)
public class ExcelTemplateGenerator implements ApplicationRunner {

    private static final String TEMPLATE_PATH = "src/main/resources/static/excel-templates/";

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path dir = Paths.get(TEMPLATE_PATH);
            if (!Files.exists(dir)) Files.createDirectories(dir);

            createDeptTemplate();
            createEmpTemplate();
            createAffiliateEmpTemplate();
            createQuestionTemplate();
            log.info("=== 엑셀 업로드 템플릿 파일 생성 완료 ===");
        } catch (IOException e) {
            log.warn("엑셀 템플릿 생성 실패 (무시 가능): {}", e.getMessage());
        }
    }

    private void createDeptTemplate() throws IOException {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("부서");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {"부서코드(필수)", "부서명(필수)", "상위부서코드(선택)"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }
            // 예시 데이터
            Row ex1 = sheet.createRow(1);
            ex1.createCell(0).setCellValue("MGMT"); ex1.createCell(1).setCellValue("경영지원본부");
            Row ex2 = sheet.createRow(2);
            ex2.createCell(0).setCellValue("HR"); ex2.createCell(1).setCellValue("인사팀"); ex2.createCell(2).setCellValue("MGMT");

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_PATH + "dept_template.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void createEmpTemplate() throws IOException {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("직원");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {
                    "사원번호(필수)", "이름(필수)", "부서코드(필수)", "직위", "직책", "이메일", "로그인ID(필수)", "상태(ACTIVE/INACTIVE/LEAVE)",
                    "기관장(Y/N)", "소속장(Y/N)", "부서장(Y/N)", "평가제외(Y/N)",
                    "경혁팀(Y/N)", "경혁팀장(Y/N)", "1인부서(Y/N)", "진료팀장(Y/N)", "의료리더(Y/N)",
                    "평가대상여부(검증용)", "이전부서명(보조)", "입사일자(보조)", "퇴사일자(보조)",
                    "attr:clinical_track(선택)"
            };
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("E099");
            valid.createCell(1).setCellValue("홍길동");
            valid.createCell(2).setCellValue("HR");
            valid.createCell(3).setCellValue("대리");
            valid.createCell(4).setCellValue("팀원");
            valid.createCell(5).setCellValue("hong@example.com");
            valid.createCell(6).setCellValue("hong99");
            valid.createCell(7).setCellValue("ACTIVE");
            valid.createCell(8).setCellValue("N");
            valid.createCell(9).setCellValue("N");
            valid.createCell(10).setCellValue("N");
            valid.createCell(11).setCellValue("N");
            valid.createCell(12).setCellValue("Y");
            valid.createCell(13).setCellValue("Y");
            valid.createCell(14).setCellValue("N");
            valid.createCell(15).setCellValue("N");
            valid.createCell(16).setCellValue("Y");
            valid.createCell(17).setCellValue("Y");
            valid.createCell(18).setCellValue("Y");
            valid.createCell(19).setCellValue("원무팀");
            valid.createCell(20).setCellValue("2023-01-02");
            valid.createCell(21).setCellValue("");
            valid.createCell(22).setCellValue("수술계");

            Row invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("E100");
            invalid.createCell(1).setCellValue("김오류");
            invalid.createCell(2).setCellValue("HR");
            invalid.createCell(3).setCellValue("사원");
            invalid.createCell(4).setCellValue("팀원");
            invalid.createCell(5).setCellValue("error@example.com");
            invalid.createCell(6).setCellValue("bad id");
            invalid.createCell(7).setCellValue("WORKING");
            invalid.createCell(8).setCellValue("MAYBE");
            invalid.createCell(22).setCellValue(" ");

            Sheet guide = wb.createSheet("가이드");
            guide.setColumnWidth(0, 9000);
            guide.setColumnWidth(1, 18000);
            guide.setColumnWidth(2, 18000);
            guide.setColumnWidth(3, 24000);
            Row gHeader = guide.createRow(0);
            gHeader.createCell(0).setCellValue("항목");
            gHeader.createCell(1).setCellValue("올바른 입력 예시");
            gHeader.createCell(2).setCellValue("잘못된 입력 예시");
            gHeader.createCell(3).setCellValue("설명");
            for (int i = 0; i < 4; i++) {
                gHeader.getCell(i).setCellStyle(headerStyle);
            }
            addGuideRow(guide, 1, "로그인ID", "nurse001", "nurse 001", "4~50자, 영문/숫자/._- 만 허용");
            addGuideRow(guide, 2, "상태", "ACTIVE", "WORKING", "ACTIVE/INACTIVE/LEAVE만 허용");
            addGuideRow(guide, 3, "boolean 속성", "Y, N, true, false, 1, 0", "MAYBE", "병원형 boolean 속성은 Y/N 계열만 허용");
            addGuideRow(guide, 4, "독립 속성 원칙", "경혁팀=Y, 경혁팀장=Y, 부서장=N", "경혁팀장=Y 이므로 부서장=Y로 추론", "병원형 속성은 각각 독립 관리하며 상호 추론하지 않음");
            addGuideRow(guide, 5, "자유 속성 컬럼명", "attr:clinical_track", "clinical_track", "반드시 attr: 접두사 또는 속성: 접두사 사용");
            addGuideRow(guide, 6, "평가대상여부", "Y", "MAYBE", "v1에서는 검증용 컬럼으로만 사용");
            addGuideRow(guide, 7, "이전부서명/입퇴사일자", "원무팀, 2023-01-02", "-", "v1에서는 보조 정보로만 사용");
            addGuideRow(guide, 8, "자유 속성 값", "A군, 수술계", "(공백만 입력)", "공백만 입력 시 미입력으로 처리");

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_PATH + "emp_template.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void createAffiliateEmpTemplate() throws IOException {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("직원");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {
                    "사원번호(필수)", "이름(필수)", "부서코드(필수)", "직위", "직책", "이메일", "로그인ID(필수)", "상태(ACTIVE/INACTIVE/LEAVE)",
                    "기관장(Y/N)", "소속장(Y/N)", "부서장(Y/N)", "평가제외(Y/N)",
                    "평가대상여부(검증용)", "입사일자(보조)", "퇴사일자(보조)", "비고(보조)",
                    "attr:affiliate_policy_group(선택)"
            };
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("A001");
            valid.createCell(1).setCellValue("김계열");
            valid.createCell(2).setCellValue("ADMIN");
            valid.createCell(3).setCellValue("차장");
            valid.createCell(4).setCellValue("부서장");
            valid.createCell(5).setCellValue("affiliate@example.com");
            valid.createCell(6).setCellValue("aff01");
            valid.createCell(7).setCellValue("ACTIVE");
            valid.createCell(8).setCellValue("N");
            valid.createCell(9).setCellValue("Y");
            valid.createCell(10).setCellValue("Y");
            valid.createCell(11).setCellValue("N");
            valid.createCell(12).setCellValue("Y");
            valid.createCell(13).setCellValue("2022-03-01");
            valid.createCell(14).setCellValue("");
            valid.createCell(15).setCellValue("신규법인");
            valid.createCell(16).setCellValue("HQ");

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_PATH + "emp_template_affiliate.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void addGuideRow(Sheet guide, int rowIndex, String item, String good, String bad, String desc) {
        Row row = guide.createRow(rowIndex);
        row.createCell(0).setCellValue(item);
        row.createCell(1).setCellValue(good);
        row.createCell(2).setCellValue(bad);
        row.createCell(3).setCellValue(desc);
    }

    private void createQuestionTemplate() throws IOException {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("평가문항");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {"카테고리", "문항내용(필수)", "문항유형(SCALE/DESCRIPTIVE)(필수)", "문항군코드(선택)", "최대점수(SCALE필수)", "정렬순서"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 7000);
            }
            Row ex = sheet.createRow(1);
            ex.createCell(0).setCellValue("공통역량"); ex.createCell(1).setCellValue("업무 전문성을 평가해주세요.");
            ex.createCell(2).setCellValue("SCALE"); ex.createCell(3).setCellValue("AB"); ex.createCell(4).setCellValue("5"); ex.createCell(5).setCellValue("1");

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_PATH + "question_template.xlsx")) {
                wb.write(fos);
            }
        }
    }
}
