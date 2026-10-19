package com.renjie.inspection.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备列表查询条件。
 *
 * <p>所有条件都是"可选"语义：前端传了才拼进 where，不传就不限制。
 * 这类动态条件用 MyBatis-Plus 的 QueryWrapper 组装即可，不必写 XML。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentQuery extends PageQuery {

    /** 模糊匹配：设备编号 / 名称 */
    private String keyword;

    private String category;

    private String status;

    private String location;

    /** 只看"到期未巡检"的设备（next_inspect_time <= 当前时间） */
    private Boolean overdueOnly;
}
