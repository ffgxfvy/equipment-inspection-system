package com.renjie.inspection.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建 / 修改巡检任务的请求体。
 */
@Data
public class TaskRequest {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称最多 100 字")
    private String name;

    @NotNull(message = "必须选择设备")
    private Long equipmentId;

    /** 可为空，表示先建任务后分派 */
    private Long inspectorId;

    @NotNull(message = "必须填写计划巡检时间")
    @FutureOrPresent(message = "计划时间不能早于当前时间")
    private LocalDateTime planTime;

    /** LOW / NORMAL / HIGH / URGENT；不传默认 NORMAL */
    private String priority;

    @Size(max = 500, message = "备注最多 500 字")
    private String remark;
}
