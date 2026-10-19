package com.renjie.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.renjie.inspection.common.BizException;
import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.dto.HandleRequest;
import com.renjie.inspection.dto.RecordQuery;
import com.renjie.inspection.dto.RecordRequest;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.InspectionRecord;
import com.renjie.inspection.entity.EquipmentStatus;
import com.renjie.inspection.entity.InspectionTask;
import com.renjie.inspection.entity.RecordResult;
import com.renjie.inspection.entity.SysUser;
import com.renjie.inspection.entity.TaskStatus;
import com.renjie.inspection.mapper.EquipmentMapper;
import com.renjie.inspection.mapper.InspectionRecordMapper;
import com.renjie.inspection.mapper.InspectionTaskMapper;
import com.renjie.inspection.mapper.SysUserMapper;
import com.renjie.inspection.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 巡检记录服务。
 *
 * <p>这里承载了本系统最主要的一条业务规则链：
 * <pre>
 *   提交记录
 *     ├─ 结论正常 → 回写设备巡检时间、设备状态恢复运行
 *     └─ 结论异常 → 设备置为「故障」、记录进入「待处理」，并要求必须填写异常描述
 *   处理异常 → 处理状态推进（待处理 → 处理中 → 已处理）
 *     └─ 推进到「已处理」时：如果该设备已无未处理异常，设备恢复运行
 * </pre>
 * 这条链路跨三张表，因此统一加 {@code @Transactional}，任一步失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionRecordService {

    private final InspectionRecordMapper recordMapper;
    private final InspectionTaskMapper taskMapper;
    private final EquipmentMapper equipmentMapper;
    private final SysUserMapper userMapper;

    /** 分页查询（含关联名称回填） */
    public PageResult<InspectionRecord> page(RecordQuery query) {
        LambdaQueryWrapper<InspectionRecord> wrapper = Wrappers.<InspectionRecord>lambdaQuery()
                .eq(query.getEquipmentId() != null, InspectionRecord::getEquipmentId, query.getEquipmentId())
                .eq(query.getInspectorId() != null, InspectionRecord::getInspectorId, query.getInspectorId())
                .eq(StringUtils.hasText(query.getResult()), InspectionRecord::getResult, query.getResult())
                .eq(StringUtils.hasText(query.getHandleStatus()),
                        InspectionRecord::getHandleStatus, query.getHandleStatus())
                .ge(StringUtils.hasText(query.getStartDate()),
                        InspectionRecord::getInspectTime, parseStart(query.getStartDate()))
                .le(StringUtils.hasText(query.getEndDate()),
                        InspectionRecord::getInspectTime, parseEnd(query.getEndDate()))
                .orderByDesc(InspectionRecord::getInspectTime);

        Page<InspectionRecord> page = recordMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        fillNames(page.getRecords());
        return PageResult.of(page);
    }

    public InspectionRecord getById(Long id) {
        InspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("巡检记录");
        }
        fillNames(List.of(record));
        return record;
    }

    /**
     * 提交巡检记录。
     *
     * <p>异常结论必须写清楚异常描述——这是跨字段的业务约束，
     * 用注解表达不了，所以放在这里判断。
     */
    @Transactional(rollbackFor = Exception.class)
    public InspectionRecord submit(RecordRequest request) {
        Equipment equipment = equipmentMapper.selectById(request.getEquipmentId());
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        if (RecordResult.isAbnormal(request.getResult())
                && !StringUtils.hasText(request.getAbnormalDesc())) {
            throw BizException.badRequest("结论为异常时，必须填写异常描述");
        }

        // 关联任务：校验存在性，并把任务推进为已完成
        InspectionTask task = null;
        if (request.getTaskId() != null) {
            task = taskMapper.selectById(request.getTaskId());
            if (task == null) {
                throw BizException.notFound("巡检任务");
            }
        }

        Long inspectorId = task != null && task.getInspectorId() != null
                ? task.getInspectorId()
                : UserContext.requireUserId();

        InspectionRecord record = new InspectionRecord();
        record.setTaskId(request.getTaskId());
        record.setEquipmentId(request.getEquipmentId());
        record.setInspectorId(inspectorId);
        record.setInspectTime(request.getInspectTime() == null
                ? LocalDateTime.now() : request.getInspectTime());
        record.setResult(request.getResult());
        record.setAbnormalDesc(request.getAbnormalDesc());
        record.setTemperature(request.getTemperature());
        record.setPressure(request.getPressure());
        record.setPhotoUrl(request.getPhotoUrl());
        record.setRemark(request.getRemark());
        record.setHandleStatus(RecordResult.isAbnormal(request.getResult())
                ? RecordResult.HANDLE_UNHANDLED : RecordResult.HANDLE_HANDLED);
        recordMapper.insert(record);

        // ---- 联动 1：回写设备 ----
        Equipment patch = new Equipment();
        patch.setId(equipment.getId());
        patch.setLastInspectTime(record.getInspectTime());
        int cycle = equipment.getInspectCycleDays() == null || equipment.getInspectCycleDays() < 1
                ? 30 : equipment.getInspectCycleDays();
        patch.setNextInspectTime(record.getInspectTime().plusDays(cycle));
        if (request.getTemperature() != null) {
            patch.setLastTemperature(request.getTemperature());
        }
        if (request.getPressure() != null) {
            patch.setLastPressure(request.getPressure());
        }
        if (RecordResult.isAbnormal(request.getResult())) {
            patch.setStatus(EquipmentStatus.FAULT);
        } else if (!EquipmentStatus.MAINTENANCE.equals(equipment.getStatus())) {
            patch.setStatus(EquipmentStatus.RUNNING);
        }
        equipmentMapper.updateById(patch);

        // ---- 联动 2：把关联任务置为已完成 ----
        if (task != null && TaskStatus.isOpen(task.getStatus())) {
            InspectionTask taskPatch = new InspectionTask();
            taskPatch.setId(task.getId());
            taskPatch.setStatus(TaskStatus.DONE);
            taskPatch.setFinishTime(record.getInspectTime());
            taskMapper.updateById(taskPatch);
        }

        log.info("提交巡检记录 id={}, equipment={}, result={}",
                record.getId(), equipment.getCode(), record.getResult());
        fillNames(List.of(record));
        return record;
    }

    /** 处理异常记录 */
    @Transactional(rollbackFor = Exception.class)
    public InspectionRecord handle(Long id, HandleRequest request) {
        InspectionRecord record = getById(id);
        if (!RecordResult.isAbnormal(record.getResult())) {
            throw BizException.badRequest("结论为正常的记录不需要处理");
        }
        if (RecordResult.HANDLE_HANDLED.equals(record.getHandleStatus())) {
            throw BizException.conflict("该异常已处理完毕，不能重复处理");
        }
        if (RecordResult.HANDLE_HANDLED.equals(request.getHandleStatus())
                && !StringUtils.hasText(request.getHandleRemark())) {
            throw BizException.badRequest("标记为已处理时必须填写处理说明");
        }

        InspectionRecord patch = new InspectionRecord();
        patch.setId(id);
        patch.setHandleStatus(request.getHandleStatus());
        patch.setHandleRemark(request.getHandleRemark());
        recordMapper.updateById(patch);

        // 该设备已无未处理异常时，把设备状态恢复为运行
        if (RecordResult.HANDLE_HANDLED.equals(request.getHandleStatus())) {
            Long remaining = recordMapper.selectCount(Wrappers.<InspectionRecord>lambdaQuery()
                    .eq(InspectionRecord::getEquipmentId, record.getEquipmentId())
                    .eq(InspectionRecord::getResult, RecordResult.ABNORMAL)
                    .ne(InspectionRecord::getHandleStatus, RecordResult.HANDLE_HANDLED));
            if (remaining == null || remaining == 0) {
                Equipment equipment = equipmentMapper.selectById(record.getEquipmentId());
                if (equipment != null && !EquipmentStatus.MAINTENANCE.equals(equipment.getStatus())) {
                    Equipment equipmentPatch = new Equipment();
                    equipmentPatch.setId(equipment.getId());
                    equipmentPatch.setStatus(EquipmentStatus.RUNNING);
                    equipmentMapper.updateById(equipmentPatch);
                }
            }
        }
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        recordMapper.deleteById(id);
    }

    /** 未处理的异常数（首页告警用） */
    public long countUnhandled() {
        Long count = recordMapper.selectCount(Wrappers.<InspectionRecord>lambdaQuery()
                .eq(InspectionRecord::getResult, RecordResult.ABNORMAL)
                .ne(InspectionRecord::getHandleStatus, RecordResult.HANDLE_HANDLED));
        return count == null ? 0L : count;
    }

    /** 某台设备的历史记录，设备详情页用 */
    public List<InspectionRecord> listByEquipment(Long equipmentId, int limit) {
        List<InspectionRecord> list = recordMapper.selectList(Wrappers.<InspectionRecord>lambdaQuery()
                .eq(InspectionRecord::getEquipmentId, equipmentId)
                .orderByDesc(InspectionRecord::getInspectTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        fillNames(list);
        return list;
    }

    // ---------------- 私有方法 ----------------

    /** 批量回填设备名/巡检员名。对 List 与 Collection 都可用，供看板复用。 */
    public List<InspectionRecord> fillNames(Collection<InspectionRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> equipmentIds = records.stream()
                .map(InspectionRecord::getEquipmentId).filter(Objects::nonNull).distinct().toList();
        List<Long> inspectorIds = records.stream()
                .map(InspectionRecord::getInspectorId).filter(Objects::nonNull).distinct().toList();

        Map<Long, Equipment> equipmentMap = equipmentIds.isEmpty() ? Map.of()
                : equipmentMapper.selectBatchIds(equipmentIds).stream()
                        .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        Map<Long, SysUser> userMap = inspectorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(inspectorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        for (InspectionRecord record : records) {
            Equipment equipment = equipmentMap.get(record.getEquipmentId());
            if (equipment != null) {
                record.setEquipmentName(equipment.getName());
                record.setEquipmentCode(equipment.getCode());
            }
            SysUser user = userMap.get(record.getInspectorId());
            if (user != null) {
                record.setInspectorName(user.getRealName());
            }
        }
        return List.copyOf(records);
    }

    private LocalDateTime parseStart(String date) {
        try {
            return LocalDate.parse(date).atStartOfDay();
        } catch (Exception ex) {
            throw BizException.badRequest("开始日期格式应为 yyyy-MM-dd：" + date);
        }
    }

    private LocalDateTime parseEnd(String date) {
        try {
            return LocalDate.parse(date).atTime(LocalTime.MAX);
        } catch (Exception ex) {
            throw BizException.badRequest("结束日期格式应为 yyyy-MM-dd：" + date);
        }
    }
}
