package com.renjie.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 异常处理（把待处理项推进到处理中/已处理）的请求体。
 */
@Data
public class HandleRequest {

    @NotBlank(message = "必须选择处理状态")
    @Pattern(regexp = "HANDLING|HANDLED", message = "处理状态只能是 HANDLING 或 HANDLED")
    private String handleStatus;

    @Size(max = 500, message = "处理说明最多 500 字")
    private String handleRemark;
}
