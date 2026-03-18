package com.hiscope.evaluation.domain.upload.handler;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.util.ExcelUtils;
import com.hiscope.evaluation.config.properties.UploadPolicyProperties;
import com.hiscope.evaluation.domain.evaluation.assignment.service.QuestionGroupResolver;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.repository.OrganizationRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 문항 엑셀 업로드 핸들러
 * 컬럼: 카테고리(0), 문항내용(1), 문항유형(2:SCALE/DESCRIPTIVE), 최대점수(3), 정렬순서(4)
 */
@Component
@RequiredArgsConstructor
public class QuestionUploadHandler {

    private final EvaluationQuestionRepository questionRepository;
    private final EvaluationTemplateRepository templateRepository;
    private final OrganizationRepository organizationRepository;
    private final QuestionGroupResolver questionGroupResolver;
    private final UploadPolicyProperties uploadPolicyProperties;

    @Transactional
    public UploadResult handle(Long orgId, Long templateId, MultipartFile file) {
        // templateId 소유권 검증 — orgId 소속이 아닌 템플릿에 문항 추가 시도 차단
        templateRepository.findByOrganizationIdAndId(orgId, templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        OrganizationProfile organizationProfile = organizationRepository.findById(orgId)
                .map(org -> org.getOrganizationProfile())
                .orElse(OrganizationProfile.HOSPITAL_DEFAULT);

        String fileName = file.getOriginalFilename();
        List<UploadError> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            QuestionImportProfile profile = detectProfile(sheet);
            int sequence = 1;

            for (int i = profile.dataStartRowIndex(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelUtils.isRowEmpty(row, profile.maxColumnIndex())) continue;
                totalRows++;
                validateMaxRows(totalRows);

                ParsedQuestionRow parsed = parseRow(profile, row, sequence++);
                String category = parsed.category();
                String content = parsed.content();
                String typeStr = parsed.questionType();
                String groupCode = parsed.questionGroupCode().isBlank() ? null : parsed.questionGroupCode();
                String maxScoreStr = parsed.maxScoreText();
                String sortStr = parsed.sortOrderText();

                boolean rowError = false;
                if (content.isBlank()) {
                    errors.add(new UploadError(i + 1, "문항내용", "필수 항목 누락")); rowError = true;
                }
                if (!typeStr.equals("SCALE") && !typeStr.equals("DESCRIPTIVE")) {
                    errors.add(new UploadError(i + 1, "문항유형", "허용값: SCALE 또는 DESCRIPTIVE")); rowError = true;
                }
                if (!questionGroupResolver.isAllowed(organizationProfile, groupCode)) {
                    errors.add(new UploadError(i + 1, "문항군코드",
                            "이 기관 프로파일에서 허용되지 않는 문항군 코드입니다. 허용값: "
                                    + String.join("/", questionGroupResolver.allowedQuestionGroupCodes(organizationProfile))));
                    rowError = true;
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
                        .questionGroupCode(groupCode)
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

    private void validateMaxRows(int totalRows) {
        if (totalRows > uploadPolicyProperties.getMaxRows()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "업로드 가능한 최대 행 수(" + uploadPolicyProperties.getMaxRows() + "행)를 초과했습니다."
            );
        }
    }

    private QuestionImportProfile detectProfile(Sheet sheet) {
        // 운영 문제은행 파일(문제/문제유형/구분) 탐지
        for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Map<String, Integer> byName = headerMap(row);
            if (byName.containsKey("문제") && byName.containsKey("문제유형") && byName.containsKey("구분")) {
                return new QuestionImportProfile(
                        QuestionImportProfileType.OPS_BANK,
                        i + 1,
                        Math.max(byName.get("문제"), Math.max(byName.get("문제유형"), byName.get("구분"))),
                        byName
                );
            }
            if (byName.containsKey("문항내용(필수)") && byName.containsKey("문항유형(SCALE/DESCRIPTIVE)(필수)")) {
                return new QuestionImportProfile(
                        QuestionImportProfileType.SYSTEM_TEMPLATE,
                        i + 1,
                        Math.max(4, row.getLastCellNum() - 1),
                        byName
                );
            }
        }
        return new QuestionImportProfile(QuestionImportProfileType.SYSTEM_TEMPLATE, 1, 4, Map.of());
    }

    private ParsedQuestionRow parseRow(QuestionImportProfile profile, Row row, int defaultSortOrder) {
        if (profile.type() == QuestionImportProfileType.OPS_BANK) {
            String content = ExcelUtils.getCellString(row, profile.headerByName().getOrDefault("문제", 1));
            String groupCode = ExcelUtils.getCellString(row, profile.headerByName().getOrDefault("문제유형", 2)).toUpperCase();
            String category = ExcelUtils.getCellString(row, profile.headerByName().getOrDefault("구분", 3));
            boolean descriptive = "주관식".equals(category) || content.contains("기재");
            String questionType = descriptive ? "DESCRIPTIVE" : "SCALE";
            String maxScore = descriptive ? "" : "5";
            return new ParsedQuestionRow(category, content, questionType, groupCode, maxScore, String.valueOf(defaultSortOrder));
        }
        Map<String, Integer> header = profile.headerByName();
        int categoryCol = header.getOrDefault("카테고리", 0);
        int contentCol = header.getOrDefault("문항내용(필수)", 1);
        int typeCol = header.getOrDefault("문항유형(SCALE/DESCRIPTIVE)(필수)", 2);
        int groupCol = header.getOrDefault("문항군코드(선택)",
                header.getOrDefault("문항군코드(선택:AA/AB)", -1));
        int maxScoreCol = header.getOrDefault("최대점수(SCALE필수)", 3);
        int sortCol = header.getOrDefault("정렬순서", 4);
        return new ParsedQuestionRow(
                ExcelUtils.getCellString(row, categoryCol),
                ExcelUtils.getCellString(row, contentCol),
                ExcelUtils.getCellString(row, typeCol).toUpperCase(),
                groupCol >= 0 ? ExcelUtils.getCellString(row, groupCol).toUpperCase() : "",
                ExcelUtils.getCellString(row, maxScoreCol),
                ExcelUtils.getCellString(row, sortCol)
        );
    }

    private Map<String, Integer> headerMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) {
            return map;
        }
        int last = Math.max(0, headerRow.getLastCellNum() - 1);
        for (int col = 0; col <= last; col++) {
            String name = ExcelUtils.getCellString(headerRow, col);
            if (!name.isBlank()) {
                map.put(name.trim(), col);
            }
        }
        return map;
    }

    private enum QuestionImportProfileType {
        SYSTEM_TEMPLATE,
        OPS_BANK
    }

    private record QuestionImportProfile(QuestionImportProfileType type,
                                         int dataStartRowIndex,
                                         int maxColumnIndex,
                                         Map<String, Integer> headerByName) {
    }

    private record ParsedQuestionRow(String category,
                                     String content,
                                     String questionType,
                                     String questionGroupCode,
                                     String maxScoreText,
                                     String sortOrderText) {
    }
}
