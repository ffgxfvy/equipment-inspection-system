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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账。
 *
 * <p>设备状态用字符串常量而不是数字枚举，是为了让数据库里直接可读
 * （排查问题时不用查字典表）。取值见 {@link com.renjie.inspection.entity.EquipmentStatus}。
 */
@Data
@TableName("equipment")
public class Equipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编号，业务唯一键 */
    private String code;

    private String name;

    /** 类目：压力表 / 温度计 / 流量计 / 阀门 / 配电柜 / 水泵 ... */
    private String category;

    private String model;

    private String manufacturer;

    private String location;

    /** RUNNING / STOPPED / FAULT / MAINTENANCE */
    private String status;

    /** 巡检周期（天） */
    private Integer inspectCycleDays;

    private LocalDateTime lastInspectTime;

    private LocalDateTime nextInspectTime;

    private LocalDate installDate;

    /**
     * 最近温度读数。
     * <p>这两个字段是为"对接现场采集"预留的：现场仪表或网关上报后写入这里，
     * 前端在设备详情里能直接看到最新值，不必去查巡检记录表。
     */
    private BigDecimal lastTemperature;

    private BigDecimal lastPressure;

    private String remark;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
