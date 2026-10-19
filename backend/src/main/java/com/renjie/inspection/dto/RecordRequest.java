package com.renjie.inspection.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提交巡检记录的请求体。
 *
 * <p>校验的重点在"异常时必须写清楚异常描述"——这个约束在 Service 层做
 * （因为需要跨字段判断，注解做不了），DTO 上只做单字段校验。
 */
@Data
public class RecordRequest {

    /** 关联任务，可为空（现场临时巡检） */
    private Long taskId;

    @NotNull(message = "必须选择设备")
    private Long equipmentId;

    private LocalDateTime inspectTime;

    @NotNull(message = "必须填写巡检结论")
    @Pattern(regexp = "NORMAL|ABNORMAL", message = "巡检结论只能是 NORMAL 或 ABNORMAL")
    private String result;

    @Size(max = 500, message = "异常描述最多 500 字")
    private String abnormalDesc;

    @DecimalMin(value = "0", message = "温度不能为负数")
    private BigDecimal temperature;

    @DecimalMin(value = "0", message = "压力不能为负数")
    private BigDecimal pressure;

    /** 现场照片路径，由上传接口返回 */
    private String photoUrl;

    @Size(max = 500, message = "备注最多 500 字")
    private String remark;
}
