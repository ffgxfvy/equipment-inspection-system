package com.renjie.inspection.entity;

import java.util.Set;

/**
 * 巡检任务优先级。
 */
public final class TaskPriority {

    public static final String LOW = "LOW";
    public static final String NORMAL = "NORMAL";
    public static final String HIGH = "HIGH";
    public static final String URGENT = "URGENT";

    public static final Set<String> ALL = Set.of(LOW, NORMAL, HIGH, URGENT);

    /** 数值越大越紧急，用于排序 */
    public static int weight(String priority) {
        return switch (priority == null ? NORMAL : priority) {
            case URGENT -> 3;
            case HIGH -> 2;
            case LOW -> 0;
            default -> 1;
        };
    }

    private TaskPriority() {
    }
}
