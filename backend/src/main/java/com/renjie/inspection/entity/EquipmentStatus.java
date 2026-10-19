package com.renjie.inspection.entity;

import java.util.Set;

/**
 * 设备状态取值。
 *
 * <p>用常量类而不是 enum 是因为 MyBatis-Plus 默认按字符串读写，
 * 而实体字段是 String——保持两边类型一致，避免额外的 TypeHandler。
 */
public final class EquipmentStatus {

    /** 运行中 */
    public static final String RUNNING = "RUNNING";
    /** 已停机 */
    public static final String STOPPED = "STOPPED";
    /** 故障 */
    public static final String FAULT = "FAULT";
    /** 检修中 */
    public static final String MAINTENANCE = "MAINTENANCE";

    public static final Set<String> ALL = Set.of(RUNNING, STOPPED, FAULT, MAINTENANCE);

    /** 这个状态是否属于"需要关注"（用于首页统计） */
    public static boolean needsAttention(String status) {
        return FAULT.equals(status) || MAINTENANCE.equals(status);
    }

    private EquipmentStatus() {
    }
}
