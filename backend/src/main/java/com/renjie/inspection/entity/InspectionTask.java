package com.renjie.inspection.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检任务。
 *
 * <p>状态流转：PENDING → DOING → DONE，或从 PENDING/DOING → CANCELLED。
 * 状态判断集中在 {@link TaskStatus}，避免各处散落字符串比较。
 */
@Data
@TableName("inspection_task")
public class InspectionTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号，形如 TK-20261001 */
    private String taskNo;

    private String name;

    private Long equipmentId;

    /** 巡检员 ID，为空表示尚未分派 */
    private Long inspectorId;

    private LocalDateTime planTime;

    private LocalDateTime finishTime;

    /** PENDING / DOING / DONE / CANCELLED */
    private String status;

    /** LOW / NORMAL / HIGH / URGENT */
    private String priority;

    private String remark;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---------------------------------------------------------------
    // 下面两个字段不属于 inspection_task 表，由 Service 层关联查询后填充。
    // 这样前端一次请求就能拿到设备名与巡检员姓名，省掉一次接口调用。
    // ---------------------------------------------------------------

    @TableField(exist = false)
    private String equipmentName;

    @TableField(exist = false)
    private String equipmentCode;

    @TableField(exist = false)
    private String inspectorName;
}
