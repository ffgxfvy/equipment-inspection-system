package com.renjie.inspection.entity;

import java.util.Set;

/**
 * 巡检任务状态与优先级取值。
 */
public final class TaskStatus {

    /** 待执行 */
    public static final String PENDING = "PENDING";
    /** 执行中 */
    public static final String DOING = "DOING";
    /** 已完成 */
    public static final String DONE = "DONE";
    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";

    public static final Set<String> ALL = Set.of(PENDING, DOING, DONE, CANCELLED);

    /** 未结束的任务（首页"待办"统计用） */
    public static final Set<String> OPEN = Set.of(PENDING, DOING);

    public static boolean isOpen(String status) {
        return OPEN.contains(status);
    }

    /** 允许的状态流转 */
    public static boolean canTransfer(String from, String to) {
        if (from == null || from.equals(to)) {
            return false;
        }
        return switch (from) {
            case PENDING -> DOING.equals(to) || DONE.equals(to) || CANCELLED.equals(to);
            case DOING -> DONE.equals(to) || CANCELLED.equals(to);
            default -> false;   // DONE / CANCELLED 是终态
        };
    }

    private TaskStatus() {
    }
}
