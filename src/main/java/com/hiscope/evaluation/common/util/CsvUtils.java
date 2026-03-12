package com.hiscope.evaluation.common.util;

public final class CsvUtils {

    private CsvUtils() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
