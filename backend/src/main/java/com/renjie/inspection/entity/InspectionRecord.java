package com.renjie.inspection.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 巡检记录。
 *
 * <p>一条记录对应"某个巡检员在某个时刻对某台设备的一次检查"。
 * 结论为异常时，会联动把设备状态置为 FAULT 并生成待处理项（见 InspectionRecordService）。
 */
@Data
@TableName("inspection_record")
public class InspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的巡检任务；现场临时巡检可以为空 */
    private Long taskId;

    private Long equipmentId;

    private Long inspectorId;

    private LocalDateTime inspectTime;

    /** NORMAL / ABNORMAL */
    private String result;

    private String abnormalDesc;

    /** UNHANDLED / HANDLING / HANDLED */
    private String handleStatus;

    private String handleRemark;

    /** 现场照片的相对路径，例如 /files/2026/10/xxx.jpg */
    private String photoUrl;

    private BigDecimal temperature;

    private BigDecimal pressure;

    private String remark;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---- 关联展示字段（不落库）----

    @TableField(exist = false)
    private String equipmentName;

    @TableField(exist = false)
    private String equipmentCode;

    @TableField(exist = false)
    private String inspectorName;
}
