package com.renjie.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.renjie.inspection.dto.DashboardVO;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.InspectionRecord;
import com.renjie.inspection.entity.EquipmentStatus;
import com.renjie.inspection.entity.RecordResult;
import com.renjie.inspection.mapper.EquipmentMapper;
import com.renjie.inspection.mapper.InspectionRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页看板服务。
 *
 * <p>趋势数据在 Java 侧做分组统计，而不是写复杂的 SQL：数据量在千级以内时
 * 一次查询 + 内存分组比多写几条 group by 更好维护。
 * 如果数据量上到百万级，应该改成按天预聚合的统计表（README 里列为优化方向）。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EquipmentMapper equipmentMapper;
    private final InspectionRecordMapper recordMapper;
    private final InspectionTaskService taskService;
    private final InspectionRecordService recordService;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TREND_DAYS = 7;

    public DashboardVO build() {
        DashboardVO vo = new DashboardVO();

        List<Equipment> equipmentList = equipmentMapper.selectList(
                Wrappers.<Equipment>lambdaQuery().orderByAsc(Equipment::getCode));

        // ---- 四个数字卡片 ----
        long faultCount = equipmentList.stream()
                .filter(e -> EquipmentStatus.needsAttention(e.getStatus()))
                .count();
        long abnormalUnhandled = recordService.countUnhandled();
        DashboardVO.Summary summary = new DashboardVO.Summary(
                equipmentList.size(), faultCount,
                taskService.countOpen(), taskService.countOverdue(), abnormalUnhandled);
        vo.setSummary(summary);

        // ---- 设备状态分布 ----
        Map<String, Long> statusCount = equipmentList.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getStatus() == null ? "UNKNOWN" : e.getStatus(),
                        LinkedHashMap::new, Collectors.counting()));
        vo.setEquipmentStatus(statusCount.entrySet().stream()
                .map(e -> new DashboardVO.NameValue(statusLabel(e.getKey()), e.getValue()))
                .toList());

        // ---- 设备类目分布 ----
        Map<String, Long> categoryCount = equipmentList.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() == null ? "未分类" : e.getCategory(),
                        Collectors.counting()));
        vo.setEquipmentCategory(categoryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new DashboardVO.NameValue(e.getKey(), e.getValue()))
                .toList());

        // ---- 近 7 天巡检趋势 ----
        LocalDateTime from = LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
        List<InspectionRecord> recent = recordMapper.selectList(
                Wrappers.<InspectionRecord>lambdaQuery()
                        .ge(InspectionRecord::getInspectTime, from)
                        .orderByAsc(InspectionRecord::getInspectTime));

        Map<String, List<InspectionRecord>> byDate = recent.stream()
                .filter(r -> r.getInspectTime() != null)
                .collect(Collectors.groupingBy(r -> r.getInspectTime().format(DATE)));

        List<DashboardVO.TrendPoint> trend = new ArrayList<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(DATE);
            List<InspectionRecord> dayRecords = byDate.getOrDefault(day, List.of());
            long abnormal = dayRecords.stream()
                    .filter(r -> RecordResult.isAbnormal(r.getResult()))
                    .count();
            trend.add(new DashboardVO.TrendPoint(day, dayRecords.size(), abnormal));
        }
        vo.setInspectTrend(trend);

        // ---- 未处理异常（最多 10 条）----
        List<InspectionRecord> unhandled = recordMapper.selectList(
                Wrappers.<InspectionRecord>lambdaQuery()
                        .eq(InspectionRecord::getResult, RecordResult.ABNORMAL)
                        .ne(InspectionRecord::getHandleStatus, RecordResult.HANDLE_HANDLED)
                        .orderByAsc(InspectionRecord::getHandleStatus)
                        .orderByDesc(InspectionRecord::getInspectTime)
                        .last("LIMIT 10"));
        List<InspectionRecord> filled = recordService.fillNames(unhandled);
        vo.setUnhandledAbnormal(filled.stream()
                .map(r -> new DashboardVO.AbnormalItem(
                        r.getId(), r.getEquipmentCode(), r.getEquipmentName(),
                        r.getAbnormalDesc(), r.getHandleStatus(),
                        r.getInspectTime() == null ? "" : r.getInspectTime().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        r.getInspectorName()))
                .sorted(Comparator.comparing(DashboardVO.AbnormalItem::getInspectTime).reversed())
                .toList());

        return vo;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case EquipmentStatus.RUNNING -> "运行";
            case EquipmentStatus.STOPPED -> "停机";
            case EquipmentStatus.FAULT -> "故障";
            case EquipmentStatus.MAINTENANCE -> "检修中";
            default -> "未知";
        };
    }
}
