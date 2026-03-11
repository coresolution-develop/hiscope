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
 * src/main/resources/static/templates/ 에 생성
 */
@Slf4j
@Component
@Order(1)
public class ExcelTemplateGenerator implements ApplicationRunner {

    private static final String TEMPLATE_PATH = "src/main/resources/static/templates/";

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path dir = Paths.get(TEMPLATE_PATH);
            if (!Files.exists(dir)) Files.createDirectories(dir);

            createDeptTemplate();
            createEmpTemplate();
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
            String[] cols = {"사원번호(필수)", "이름(필수)", "부서코드(필수)", "직위", "직책", "이메일", "로그인ID(필수)", "상태(ACTIVE/INACTIVE/LEAVE)"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }
            Row ex = sheet.createRow(1);
            ex.createCell(0).setCellValue("E099"); ex.createCell(1).setCellValue("홍길동");
            ex.createCell(2).setCellValue("HR"); ex.createCell(3).setCellValue("대리");
            ex.createCell(4).setCellValue("팀원"); ex.createCell(5).setCellValue("hong@example.com");
            ex.createCell(6).setCellValue("hong99"); ex.createCell(7).setCellValue("ACTIVE");

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_PATH + "emp_template.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void createQuestionTemplate() throws IOException {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("평가문항");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {"카테고리", "문항내용(필수)", "문항유형(SCALE/DESCRIPTIVE)(필수)", "최대점수(SCALE필수)", "정렬순서"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 7000);
            }
            Row ex = sheet.createRow(1);
            ex.createCell(0).setCellValue("공통역량"); ex.createCell(1).setCellValue("업무 전문성을 평가해주세요.");
            ex.createCell(2).setCellValue("SCALE"); ex.createCell(3).setCellValue("5"); ex.createCell(4).setCellValue("1");

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_PATH + "question_template.xlsx")) {
                wb.write(fos);
            }
        }
    }
}
