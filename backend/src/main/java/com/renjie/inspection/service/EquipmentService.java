package com.renjie.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.renjie.inspection.common.BizException;
import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.dto.EquipmentQuery;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.EquipmentStatus;
import com.renjie.inspection.mapper.EquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账服务。
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentMapper equipmentMapper;

    /** 分页查询 */
    public PageResult<Equipment> page(EquipmentQuery query) {
        Page<Equipment> page = equipmentMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query));
        return PageResult.of(page);
    }

    /**
     * 导出用：按**同样的筛选条件**取数据，但不分页。
     *
     * <p>刻意复用 {@link #buildWrapper}，而不是另写一套条件——
     * 否则「列表里筛出来的结果」和「导出的结果」迟早会对不上，
     * 这种不一致排查起来非常费劲（业务方会说"我明明筛选了，导出来却不对"）。
     *
     * <p>多取一行是为了让上层判断是否被截断，配合 LIMIT 防止一次拉全表。
     */
    public List<Equipment> listForExport(EquipmentQuery query) {
        int limit = EquipmentExcelService.MAX_EXPORT_ROWS + 1;
        return equipmentMapper.selectList(buildWrapper(query).last("LIMIT " + limit));
    }

    /**
     * 组装查询条件。
     *
     * <p>所有条件都是「可选」语义：前端传了才拼进 where，不传就不限制。
     */
    private LambdaQueryWrapper<Equipment> buildWrapper(EquipmentQuery query) {
        return Wrappers.<Equipment>lambdaQuery()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(Equipment::getCode, query.getKeyword())
                        .or()
                        .like(Equipment::getName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getCategory()), Equipment::getCategory, query.getCategory())
                .eq(StringUtils.hasText(query.getStatus()), Equipment::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getLocation()), Equipment::getLocation, query.getLocation())
                // 到期未巡检：有下次巡检时间，且已经过期
                .le(Boolean.TRUE.equals(query.getOverdueOnly()),
                        Equipment::getNextInspectTime, LocalDateTime.now())
                .orderByDesc(Equipment::getUpdateTime);
    }

    public Equipment getById(Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        return equipment;
    }

    /** 全部类目，前端下拉框用 */
    public List<String> listCategories() {
        return equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                        .select(Equipment::getCategory)
                        .groupBy(Equipment::getCategory))
                .stream()
                .map(Equipment::getCategory)
                .filter(StringUtils::hasText)
                .sorted()
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Equipment create(Equipment equipment) {
        validateStatus(equipment.getStatus());
        ensureCodeUnique(equipment.getCode(), null);
        normalize(equipment);
        equipment.setId(null);
        equipmentMapper.insert(equipment);
        return equipment;
    }

    @Transactional(rollbackFor = Exception.class)
    public Equipment update(Long id, Equipment payload) {
        Equipment existing = getById(id);
        validateStatus(payload.getStatus());
        ensureCodeUnique(payload.getCode(), id);
        normalize(payload);

        payload.setId(id);
        // 这些字段由系统维护，不接受前端传入：
        // 上次/下次巡检时间由"完成任务"或"提交记录"驱动，避免被手工改乱。
        payload.setLastInspectTime(existing.getLastInspectTime());
        payload.setNextInspectTime(existing.getNextInspectTime());
        payload.setLastTemperature(existing.getLastTemperature());
        payload.setLastPressure(existing.getLastPressure());
        equipmentMapper.updateById(payload);
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        equipmentMapper.deleteById(id);   // 逻辑删除
    }

    /** 统计各状态设备数量，首页饼图用 */
    public List<Equipment> listAll() {
        return equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                .orderByAsc(Equipment::getCode));
    }

    /**
     * 完成一次巡检后回写设备。
     *
     * @param cycleDays 巡检周期；为空时沿用设备原有周期
     */
    @Transactional(rollbackFor = Exception.class)
    public void markInspected(Long equipmentId, LocalDateTime inspectTime, Integer cycleDays) {
        Equipment equipment = getById(equipmentId);
        int cycle = cycleDays != null ? cycleDays
                : (equipment.getInspectCycleDays() == null ? 30 : equipment.getInspectCycleDays());

        Equipment patch = new Equipment();
        patch.setId(equipmentId);
        patch.setLastInspectTime(inspectTime);
        patch.setNextInspectTime(inspectTime.plusDays(cycle));
        // 巡检完成说明设备当前可读，把"检修中"之外的状态恢复为运行
        if (!EquipmentStatus.MAINTENANCE.equals(equipment.getStatus())) {
            patch.setStatus(EquipmentStatus.RUNNING);
        }
        equipmentMapper.updateById(patch);
    }

    /** 发现异常时把设备置为故障 */
    @Transactional(rollbackFor = Exception.class)
    public void markFault(Long equipmentId) {
        Equipment patch = new Equipment();
        patch.setId(equipmentId);
        patch.setStatus(EquipmentStatus.FAULT);
        equipmentMapper.updateById(patch);
    }

    // ---------------- 私有方法 ----------------

    private void validateStatus(String status) {
        if (StringUtils.hasText(status) && !EquipmentStatus.ALL.contains(status)) {
            throw BizException.badRequest("设备状态取值非法：" + status);
        }
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            throw BizException.badRequest("设备编号不能为空");
        }
        Long count = equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getCode, code)
                .ne(excludeId != null, Equipment::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("设备编号已存在：" + code);
        }
    }

    private void normalize(Equipment equipment) {
        if (!StringUtils.hasText(equipment.getStatus())) {
            equipment.setStatus(EquipmentStatus.RUNNING);
        }
        if (equipment.getInspectCycleDays() == null || equipment.getInspectCycleDays() < 1) {
            equipment.setInspectCycleDays(30);
        }
    }
}
