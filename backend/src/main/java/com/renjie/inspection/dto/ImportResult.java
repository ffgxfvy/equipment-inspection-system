package com.renjie.inspection.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入结果。
 *
 * <p>刻意返回「部分成功」的统计而不是一句"导入成功/失败"：
 * 业务方导入一份 200 行的表，如果有 3 行格式不对，他真正想知道的是
 * <b>哪 3 行、为什么</b>，而不是"整批失败了，你自己去看"。
 */
@Data
public class ImportResult implements Serializable {

    /** 从 Excel 里解析出的有效数据行数（不含空行、不含错误行） */
    private int total;

    /** 成功写入数据库的行数 */
    private int successCount;

    /** 失败行数 */
    private int failCount;

    /** 每条失败的说明，格式：第 12 行：设备编号已存在：EQ-1001 */
    private List<String> errors = new ArrayList<>();

    public void addError(int rowNum, String message) {
        this.errors.add("第 " + rowNum + " 行：" + message);
        this.failCount++;
    }

    public void addSuccess() {
        this.successCount++;
    }
}
