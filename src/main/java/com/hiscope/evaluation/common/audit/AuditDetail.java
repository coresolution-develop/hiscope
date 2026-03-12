package com.hiscope.evaluation.common.audit;

import java.util.StringJoiner;

public final class AuditDetail {

    private AuditDetail() {
    }

    public static String of(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = (i + 1 < keyValues.length) ? keyValues[i + 1] : "";
            joiner.add(String.valueOf(key) + "=" + String.valueOf(value));
        }
        return joiner.toString();
    }
}
