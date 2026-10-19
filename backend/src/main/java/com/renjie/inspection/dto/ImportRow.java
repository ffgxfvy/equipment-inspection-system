package com.renjie.inspection.dto;

import com.renjie.inspection.entity.Equipment;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Excel 中的一行数据。
 *
 * <p>解析失败时不抛异常，而是把原因放进 {@code error} 字段——
 * 这样一行格式错误不会中断整个文件的读取，最终能一次性把所有问题都报给用户。
 */
@Data
@AllArgsConstructor
public class ImportRow {

    /** Excel 中的行号（从 1 开始，表头是第 1 行）。报错时用它对应用户看到的行。 */
    private int rowNum;

    /** 解析成功时非空 */
    private Equipment equipment;

    /** 解析失败的原因；成功时为 null */
    private String error;

    public boolean hasError() {
        return error != null;
    }
}
