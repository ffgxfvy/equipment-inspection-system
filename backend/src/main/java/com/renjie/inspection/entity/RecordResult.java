package com.renjie.inspection.entity;

import java.util.Set;

/**
 * 巡检结论与异常处理状态取值。
 */
public final class RecordResult {

    public static final String NORMAL = "NORMAL";
    public static final String ABNORMAL = "ABNORMAL";

    public static final String HANDLE_UNHANDLED = "UNHANDLED";
    public static final String HANDLE_HANDLING = "HANDLING";
    public static final String HANDLE_HANDLED = "HANDLED";

    public static final Set<String> ALL_HANDLE_STATUS =
            Set.of(HANDLE_UNHANDLED, HANDLE_HANDLING, HANDLE_HANDLED);

    public static boolean isAbnormal(String result) {
        return ABNORMAL.equals(result);
    }

    private RecordResult() {
    }
}
