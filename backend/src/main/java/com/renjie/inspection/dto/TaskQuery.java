package com.renjie.inspection.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检任务列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskQuery extends PageQuery {

    /** 模糊匹配：任务编号 / 任务名称 */
    private String keyword;

    private String status;

    private String priority;

    private Long equipmentId;

    private Long inspectorId;

    /** 只看未结束（PENDING / DOING），首页待办用 */
    private Boolean openOnly;
}
