package com.renjie.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.renjie.inspection.common.BizException;
import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.dto.TaskQuery;
import com.renjie.inspection.dto.TaskRequest;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.InspectionTask;
import com.renjie.inspection.entity.SysUser;
import com.renjie.inspection.entity.TaskPriority;
import com.renjie.inspection.entity.TaskStatus;
import com.renjie.inspection.mapper.EquipmentMapper;
import com.renjie.inspection.mapper.InspectionTaskMapper;
import com.renjie.inspection.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 巡检任务服务。
 *
 * <p>本类里有三处刻意的设计，面试时可以直接讲：
 * <ol>
 *   <li><b>状态机集中管理</b>：所有状态流转都过 {@link TaskStatus#canTransfer}，
 *       不在各个方法里散落 if 判断；</li>
 *   <li><b>批量回填关联字段</b>：列表查询后用一次 IN 查询把设备名/巡检员名补齐，
 *       避免 N+1（如果逐条查，20 条数据就是 41 次 SQL）；</li>
 *   <li><b>任务编号按日期生成</b>：TK-yyyyMMdd-NNN，当天顺序递增。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionTaskService {

    private final InspectionTaskMapper taskMapper;
    private final EquipmentMapper equipmentMapper;
    private final SysUserMapper userMapper;

    private static final DateTimeFormatter TASK_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 分页查询（含关联名称回填） */
    public PageResult<InspectionTask> page(TaskQuery query) {
        LambdaQueryWrapper<InspectionTask> wrapper = Wrappers.<InspectionTask>lambdaQuery()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(InspectionTask::getTaskNo, query.getKeyword())
                        .or()
                        .like(InspectionTask::getName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), InspectionTask::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getPriority()), InspectionTask::getPriority, query.getPriority())
                .eq(query.getEquipmentId() != null, InspectionTask::getEquipmentId, query.getEquipmentId())
                .eq(query.getInspectorId() != null, InspectionTask::getInspectorId, query.getInspectorId())
                .in(Boolean.TRUE.equals(query.getOpenOnly()), InspectionTask::getStatus, TaskStatus.OPEN)
                .orderByDesc(InspectionTask::getCreateTime);

        Page<InspectionTask> page = taskMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        fillRelatedNames(page.getRecords());
        return PageResult.of(page);
    }

    public InspectionTask getById(Long id) {
        InspectionTask task = taskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("巡检任务");
        }
        fillRelatedNames(List.of(task));
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionTask create(TaskRequest request) {
        Equipment equipment = equipmentMapper.selectById(request.getEquipmentId());
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        if (request.getInspectorId() != null) {
            requireInspector(request.getInspectorId());
        }

        InspectionTask task = new InspectionTask();
        task.setTaskNo(nextTaskNo());
        task.setName(request.getName());
        task.setEquipmentId(request.getEquipmentId());
        task.setInspectorId(request.getInspectorId());
        task.setPlanTime(request.getPlanTime());
        task.setPriority(StringUtils.hasText(request.getPriority()) ? request.getPriority() : TaskPriority.NORMAL);
        task.setRemark(request.getRemark());
        task.setStatus(TaskStatus.PENDING);

        if (!TaskPriority.ALL.contains(task.getPriority())) {
            throw BizException.badRequest("优先级取值非法：" + task.getPriority());
        }

        taskMapper.insert(task);
        fillRelatedNames(List.of(task));
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionTask update(Long id, TaskRequest request) {
        InspectionTask existing = getById(id);
        if (!TaskStatus.isOpen(existing.getStatus())) {
            throw BizException.conflict("任务已结束（" + existing.getStatus() + "），不能再修改");
        }
        if (request.getInspectorId() != null) {
            requireInspector(request.getInspectorId());
        }

        InspectionTask patch = new InspectionTask();
        patch.setId(id);
        patch.setName(request.getName());
        patch.setEquipmentId(request.getEquipmentId());
        patch.setInspectorId(request.getInspectorId());
        patch.setPlanTime(request.getPlanTime());
        patch.setPriority(request.getPriority());
        patch.setRemark(request.getRemark());
        taskMapper.updateById(patch);
        return getById(id);
    }

    /** 分派巡检员 */
    @Transactional(rollbackFor = Exception.class)
    public InspectionTask assign(Long id, Long inspectorId) {
        InspectionTask task = getById(id);
        if (!TaskStatus.isOpen(task.getStatus())) {
            throw BizException.conflict("任务已结束，不能再分派");
        }
        requireInspector(inspectorId);

        InspectionTask patch = new InspectionTask();
        patch.setId(id);
        patch.setInspectorId(inspectorId);
        taskMapper.updateById(patch);
        return getById(id);
    }

    /**
     * 推进任务状态。
     *
     * <p>状态流转必须合法（见 {@link TaskStatus#canTransfer}），
     * 且推进到 DONE 时回写设备的巡检时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public InspectionTask changeStatus(Long id, String targetStatus) {
        InspectionTask task = getById(id);
        if (!TaskStatus.ALL.contains(targetStatus)) {
            throw BizException.badRequest("任务状态取值非法：" + targetStatus);
        }
        if (!TaskStatus.canTransfer(task.getStatus(), targetStatus)) {
            throw BizException.conflict(
                    "不允许的状态流转：" + task.getStatus() + " → " + targetStatus);
        }
        if (TaskStatus.DOING.equals(targetStatus) && task.getInspectorId() == null) {
            throw BizException.badRequest("任务尚未分派巡检员，不能开始执行");
        }

        InspectionTask patch = new InspectionTask();
        patch.setId(id);
        patch.setStatus(targetStatus);
        if (TaskStatus.DONE.equals(targetStatus)) {
            patch.setFinishTime(LocalDateTime.now());
        }
        taskMapper.updateById(patch);

        if (TaskStatus.DONE.equals(targetStatus)) {
            // 完成后回写设备：上次巡检时间 = 计划完成时间，下次巡检时间 = 加一个周期
            Equipment equipment = equipmentMapper.selectById(task.getEquipmentId());
            Integer cycle = equipment == null ? null : equipment.getInspectCycleDays();
            markEquipmentInspected(task.getEquipmentId(), patch.getFinishTime(), cycle);
        }
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InspectionTask task = getById(id);
        if (TaskStatus.DOING.equals(task.getStatus())) {
            throw BizException.conflict("执行中的任务不能删除，请先取消");
        }
        taskMapper.deleteById(id);
    }

    /** 首页待办数量 */
    public long countOpen() {
        Long count = taskMapper.selectCount(Wrappers.<InspectionTask>lambdaQuery()
                .in(InspectionTask::getStatus, TaskStatus.OPEN));
        return count == null ? 0L : count;
    }

    /** 逾期未完成：计划时间已过但状态仍是未结束 */
    public long countOverdue() {
        Long count = taskMapper.selectCount(Wrappers.<InspectionTask>lambdaQuery()
                .in(InspectionTask::getStatus, TaskStatus.OPEN)
                .lt(InspectionTask::getPlanTime, LocalDateTime.now()));
        return count == null ? 0L : count;
    }

    // ---------------- 私有方法 ----------------

    /** 关联查询设备与巡检员的名称，一次 IN 查询解决，避免 N+1 */
    private void fillRelatedNames(Collection<InspectionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        List<Long> equipmentIds = tasks.stream()
                .map(InspectionTask::getEquipmentId).filter(java.util.Objects::nonNull).distinct().toList();
        List<Long> inspectorIds = tasks.stream()
                .map(InspectionTask::getInspectorId).filter(java.util.Objects::nonNull).distinct().toList();

        Map<Long, Equipment> equipmentMap = equipmentIds.isEmpty() ? Map.of()
                : equipmentMapper.selectBatchIds(equipmentIds).stream()
                        .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        Map<Long, SysUser> userMap = inspectorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(inspectorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        for (InspectionTask task : tasks) {
            Equipment equipment = equipmentMap.get(task.getEquipmentId());
            if (equipment != null) {
                task.setEquipmentName(equipment.getName());
                task.setEquipmentCode(equipment.getCode());
            }
            SysUser user = userMap.get(task.getInspectorId());
            if (user != null) {
                task.setInspectorName(user.getRealName());
            }
        }
    }

    private void requireInspector(Long inspectorId) {
        SysUser user = userMapper.selectById(inspectorId);
        if (user == null) {
            throw BizException.notFound("巡检员");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.badRequest("该巡检员已停用，不能分派任务");
        }
    }

    /** 生成任务编号：TK-20261015-001 */
    private String nextTaskNo() {
        String datePart = LocalDate.now().format(TASK_NO_DATE);
        String prefix = "TK-" + datePart + "-";
        Long todayCount = taskMapper.selectCount(Wrappers.<InspectionTask>lambdaQuery()
                .likeRight(InspectionTask::getTaskNo, prefix));
        long seq = (todayCount == null ? 0L : todayCount) + 1;
        return prefix + String.format("%03d", seq);
    }

    private void markEquipmentInspected(Long equipmentId, LocalDateTime time, Integer cycleDays) {
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            return;
        }
        int cycle = cycleDays == null || cycleDays < 1 ? 30 : cycleDays;
        Equipment patch = new Equipment();
        patch.setId(equipmentId);
        patch.setLastInspectTime(time);
        patch.setNextInspectTime(time.plusDays(cycle));
        equipmentMapper.updateById(patch);
        log.debug("设备 {} 巡检时间已回写，下次巡检 {}", equipmentId, patch.getNextInspectTime());
    }
}
