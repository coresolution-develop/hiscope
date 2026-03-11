package com.hiscope.evaluation.common.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Optional;

public class ExcelUtils {

    private ExcelUtils() {}

    public static String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                long longVal = (long) cell.getNumericCellValue();
                yield String.valueOf(longVal);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    public static Optional<Integer> getCellInteger(Row row, int colIndex) {
        String val = getCellString(row, colIndex);
        if (val.isBlank()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(val));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isRowEmpty(Row row, int lastColIndex) {
        if (row == null) return true;
        for (int i = 0; i <= lastColIndex; i++) {
            if (!getCellString(row, i).isBlank()) return false;
        }
        return true;
    }

    public static Workbook createWorkbook() {
        return new XSSFWorkbook();
    }

    public static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
