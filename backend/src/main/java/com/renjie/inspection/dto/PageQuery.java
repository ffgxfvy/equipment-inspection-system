package com.renjie.inspection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>所有列表接口都继承它，避免每个 Query 对象重复定义分页字段。
 * 默认值刻意设置成"保守的小值"，防止前端忘记传参时一次拉全表。
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    @Max(value = 200, message = "每页最多 200 条")
    private Integer pageSize = 10;

    public Integer getPageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public Integer getPageSize() {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 200);
    }
}
