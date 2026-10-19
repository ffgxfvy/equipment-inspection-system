package com.renjie.inspection.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检记录列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecordQuery extends PageQuery {

    private Long equipmentId;

    private Long inspectorId;

    private String result;

    private String handleStatus;

    /** 时间范围：yyyy-MM-dd */
    private String startDate;

    private String endDate;
}
