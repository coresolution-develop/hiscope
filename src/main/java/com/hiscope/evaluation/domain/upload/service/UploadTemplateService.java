package com.hiscope.evaluation.domain.upload.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.util.ExcelUtils;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadTemplateService {

    private final ResourceLoader resourceLoader;

    @Value("${app.upload.template.department-path:classpath:static/excel-templates/dept_template.xlsx}")
    private String departmentTemplatePath;

    @Value("${app.upload.template.employee-path:classpath:static/excel-templates/emp_template.xlsx}")
    private String employeeTemplatePath;

    @Value("${app.upload.template.employee-hospital-path:classpath:static/excel-templates/emp_template_hospital.xlsx}")
    private String employeeHospitalTemplatePath;

    @Value("${app.upload.template.employee-affiliate-path:classpath:static/excel-templates/emp_template_affiliate.xlsx}")
    private String employeeAffiliateTemplatePath;

    @Value("${app.upload.template.question-path:classpath:static/excel-templates/question_template.xlsx}")
    private String questionTemplatePath;

    public Resource loadDepartmentTemplate() {
        return loadTemplateOrFallback(
                departmentTemplatePath,
                "부서 업로드 템플릿",
                () -> buildDepartmentTemplateResource()
        );
    }

    public Resource loadEmployeeTemplate() {
        return loadTemplateOrFallback(
                employeeTemplatePath,
                "직원 업로드 템플릿",
                () -> buildEmployeeTemplateResource(buildHospitalEmployeeColumns(), "직원 업로드 템플릿")
        );
    }

    public Resource loadEmployeeTemplateByType(OrganizationType organizationType) {
        if (organizationType == OrganizationType.AFFILIATE) {
            return buildEmployeeTemplateResource(buildAffiliateEmployeeColumns(), "계열사 직원 업로드 템플릿");
        }
        return buildEmployeeTemplateResource(buildHospitalEmployeeColumns(), "병원 직원 업로드 템플릿");
    }

    public Resource loadEmployeeHospitalTemplate() {
        return loadTemplateOrFallback(
                employeeHospitalTemplatePath,
                "병원 직원 업로드 템플릿",
                () -> buildEmployeeTemplateResource(buildHospitalEmployeeColumns(), "병원 직원 업로드 템플릿")
        );
    }

    public Resource loadEmployeeAffiliateTemplate() {
        return loadTemplateOrFallback(
                employeeAffiliateTemplatePath,
                "계열사 직원 업로드 템플릿",
                () -> buildEmployeeTemplateResource(buildAffiliateEmployeeColumns(), "계열사 직원 업로드 템플릿")
        );
    }

    public Resource loadQuestionTemplate() {
        return loadTemplateOrFallback(
                questionTemplatePath,
                "평가문항 업로드 템플릿",
                () -> buildQuestionTemplateResource()
        );
    }

    private Resource loadTemplateOrFallback(String location, String templateName, TemplateFallbackSupplier fallbackSupplier) {
        Resource resource = resourceLoader.getResource(location);
        try {
            if (!resource.exists() || !resource.isReadable() || resource.contentLength() <= 0) {
                log.warn("Upload template missing or unreadable. name={}, location={}, fallback=dynamic",
                        templateName, location);
                return fallbackSupplier.get();
            }
            return resource;
        } catch (IOException e) {
            log.warn("Failed to read upload template. name={}, location={}, fallback=dynamic",
                    templateName, location, e);
            return fallbackSupplier.get();
        } catch (RuntimeException e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                    templateName + " 파일을 읽을 수 없습니다. 관리자에게 문의해주세요.");
        }
    }

    private Resource buildEmployeeTemplateResource(String[] columns, String templateName) {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("직원");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("E001");
            sample.createCell(1).setCellValue("홍길동");
            sample.createCell(2).setCellValue("HR");
            sample.createCell(3).setCellValue("대리");
            sample.createCell(4).setCellValue("팀원");
            sample.createCell(5).setCellValue("hong@example.com");
            sample.createCell(6).setCellValue("hong01");
            sample.createCell(7).setCellValue("ACTIVE");
            for (int i = 8; i < columns.length; i++) {
                sample.createCell(i).setCellValue(i % 2 == 0 ? "Y" : "N");
            }

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
            addGuideRow(guide, 3, "boolean 속성", "Y/N", "MAYBE", "boolean 속성은 Y/N 계열만 허용");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            byte[] bytes = baos.toByteArray();
            return new ByteArrayResource(bytes);
        } catch (IOException e) {
            log.error("Failed to build employee template. name={}", templateName, e);
            throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                    templateName + " 파일을 생성할 수 없습니다. 관리자에게 문의해주세요.");
        }
    }

    private Resource buildDepartmentTemplateResource() {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("부서");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            String[] cols = {"부서코드(필수)", "부서명(필수)", "상위부서코드(선택)"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("HR");
            sample.createCell(1).setCellValue("인사팀");
            sample.createCell(2).setCellValue("");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return new ByteArrayResource(baos.toByteArray());
        } catch (IOException e) {
            log.error("Failed to build department template", e);
            throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                    "부서 업로드 템플릿 파일을 생성할 수 없습니다. 관리자에게 문의해주세요.");
        }
    }

    private Resource buildQuestionTemplateResource() {
        try (Workbook wb = ExcelUtils.createWorkbook()) {
            Sheet sheet = wb.createSheet("평가문항");
            CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
            String[] cols = {"카테고리", "문항내용(필수)", "문항유형(SCALE/DESCRIPTIVE)(필수)", "문항군코드(선택)", "최대점수(SCALE필수)", "정렬순서"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 7000);
            }
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("공통역량");
            sample.createCell(1).setCellValue("업무 전문성을 평가해주세요.");
            sample.createCell(2).setCellValue("SCALE");
            sample.createCell(3).setCellValue("AB");
            sample.createCell(4).setCellValue("5");
            sample.createCell(5).setCellValue("1");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return new ByteArrayResource(baos.toByteArray());
        } catch (IOException e) {
            log.error("Failed to build question template", e);
            throw new BusinessException(ErrorCode.EXCEL_TEMPLATE_NOT_FOUND,
                    "평가문항 업로드 템플릿 파일을 생성할 수 없습니다. 관리자에게 문의해주세요.");
        }
    }

    private String[] buildHospitalEmployeeColumns() {
        return new String[]{
                "사원번호(필수)", "이름(필수)", "부서코드(필수)", "직위", "직책", "이메일", "로그인ID(필수)", "상태(ACTIVE/INACTIVE/LEAVE)",
                "기관장(Y/N)", "소속장(Y/N)", "부서장(Y/N)", "평가제외(Y/N)",
                "경혁팀(Y/N)", "경혁팀장(Y/N)", "1인부서(Y/N)", "진료팀장(Y/N)", "의료리더(Y/N)",
                "평가대상여부(검증용)", "이전부서명(보조)", "입사일자(보조)", "퇴사일자(보조)",
                "attr:clinical_track(선택)"
        };
    }

    private String[] buildAffiliateEmployeeColumns() {
        return new String[]{
                "사원번호(필수)", "이름(필수)", "부서코드(필수)", "직위", "직책", "이메일", "로그인ID(필수)", "상태(ACTIVE/INACTIVE/LEAVE)",
                "기관장(Y/N)", "소속장(Y/N)", "부서장(Y/N)", "평가제외(Y/N)",
                "평가대상여부(검증용)", "입사일자(보조)", "퇴사일자(보조)", "비고(보조)",
                "attr:affiliate_policy_group(선택)"
        };
    }

    private void addGuideRow(Sheet guide, int rowIndex, String item, String good, String bad, String desc) {
        Row row = guide.createRow(rowIndex);
        row.createCell(0).setCellValue(item);
        row.createCell(1).setCellValue(good);
        row.createCell(2).setCellValue(bad);
        row.createCell(3).setCellValue(desc);
    }

    @FunctionalInterface
    private interface TemplateFallbackSupplier {
        Resource get();
    }
}
