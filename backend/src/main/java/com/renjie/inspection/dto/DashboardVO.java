package com.renjie.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 首页看板数据。
 *
 * <p>把首页需要的所有统计一次性返回，而不是让前端并发调 6 个接口——
 * 首页是访问最频繁的页面，少 5 次往返对体验的影响很明显。
 */
@Data
public class DashboardVO {

    /** 顶部的四个数字卡片 */
    private Summary summary;

    /** 设备状态分布（饼图） */
    private List<NameValue> equipmentStatus;

    /** 设备类目分布（柱状图） */
    private List<NameValue> equipmentCategory;

    /** 近 7 天巡检完成趋势（折线图） */
    private List<TrendPoint> inspectTrend;

    /** 未处理的异常列表（表格） */
    private List<AbnormalItem> unhandledAbnormal;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Summary {
        private long equipmentTotal;
        private long equipmentFault;
        private long taskOpen;
        private long taskOverdue;
        private long abnormalUnhandled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NameValue {
        private String name;
        private long value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendPoint {
        /** yyyy-MM-dd */
        private String date;
        /** 当天巡检次数 */
        private long total;
        /** 当天发现异常次数 */
        private long abnormal;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AbnormalItem {
        private Long recordId;
        private String equipmentCode;
        private String equipmentName;
        private String abnormalDesc;
        private String handleStatus;
        private String inspectTime;
        private String inspectorName;
    }
}
