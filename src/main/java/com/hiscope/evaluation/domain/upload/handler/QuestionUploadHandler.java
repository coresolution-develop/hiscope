package com.hiscope.evaluation.domain.upload.handler;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.util.ExcelUtils;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.upload.dto.UploadError;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 평가 문항 엑셀 업로드 핸들러
 * 컬럼: 카테고리(0), 문항내용(1), 문항유형(2:SCALE/DESCRIPTIVE), 최대점수(3), 정렬순서(4)
 */
@Component
@RequiredArgsConstructor
public class QuestionUploadHandler {

    private final EvaluationQuestionRepository questionRepository;
    private final EvaluationTemplateRepository templateRepository;

    @Transactional
    public UploadResult handle(Long orgId, Long templateId, MultipartFile file) {
        // templateId 소유권 검증 — orgId 소속이 아닌 템플릿에 문항 추가 시도 차단
        templateRepository.findByOrganizationIdAndId(orgId, templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));

        String fileName = file.getOriginalFilename();
        List<UploadError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelUtils.isRowEmpty(row, 4)) continue;
                totalRows++;

                String category  = ExcelUtils.getCellString(row, 0);
                String content   = ExcelUtils.getCellString(row, 1);
                String typeStr   = ExcelUtils.getCellString(row, 2).toUpperCase();
                String maxScoreStr = ExcelUtils.getCellString(row, 3);
                String sortStr   = ExcelUtils.getCellString(row, 4);

                boolean rowError = false;
                if (content.isBlank()) {
                    errors.add(new UploadError(i + 1, "문항내용", "필수 항목 누락")); rowError = true;
                }
                if (!typeStr.equals("SCALE") && !typeStr.equals("DESCRIPTIVE")) {
                    errors.add(new UploadError(i + 1, "문항유형", "허용값: SCALE 또는 DESCRIPTIVE")); rowError = true;
                }
                Integer maxScore = null;
                if (typeStr.equals("SCALE")) {
                    if (maxScoreStr.isBlank()) {
                        errors.add(new UploadError(i + 1, "최대점수", "SCALE 유형은 최대점수 필수")); rowError = true;
                    } else {
                        try { maxScore = Integer.parseInt(maxScoreStr); }
                        catch (NumberFormatException e) {
                            errors.add(new UploadError(i + 1, "최대점수", "숫자 형식이 아닙니다.")); rowError = true;
                        }
                    }
                }
                int sortOrder = 0;
                if (!sortStr.isBlank()) {
                    try { sortOrder = Integer.parseInt(sortStr); }
                    catch (NumberFormatException e) {
                        errors.add(new UploadError(i + 1, "정렬순서", "숫자 형식이 아닙니다.")); rowError = true;
                    }
                }
                if (rowError) continue;

                EvaluationQuestion q = EvaluationQuestion.builder()
                        .templateId(templateId).organizationId(orgId)
                        .category(category.isBlank() ? "공통" : category)
                        .content(content).questionType(typeStr)
                        .maxScore(maxScore).sortOrder(sortOrder).active(true)
                        .build();
                questionRepository.save(q);
                successRows++;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR);
        }

        if (totalRows == 0) return UploadResult.failed("QUESTION", fileName, List.of(
                new UploadError(0, "-", "유효한 데이터 행이 없습니다.")));
        if (errors.isEmpty()) return UploadResult.success("QUESTION", fileName, totalRows);
        if (successRows > 0) return UploadResult.partial("QUESTION", fileName, totalRows, successRows, errors);
        return UploadResult.failed("QUESTION", fileName, errors);
    }
}
