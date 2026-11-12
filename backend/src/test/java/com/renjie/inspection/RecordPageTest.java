package com.renjie.inspection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.renjie.inspection.common.BizException;
import com.renjie.inspection.common.ErrorCode;
import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.dto.RecordQuery;
import com.renjie.inspection.entity.InspectionRecord;
import com.renjie.inspection.mapper.EquipmentMapper;
import com.renjie.inspection.mapper.InspectionRecordMapper;
import com.renjie.inspection.mapper.InspectionTaskMapper;
import com.renjie.inspection.mapper.SysUserMapper;
import com.renjie.inspection.service.InspectionRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 巡检记录分页日期参数边界的单元测试（防 MyBatis-Plus 条件下参数急切求值回归）。
 */
@ExtendWith(MockitoExtension.class)
class RecordPageTest {

    @Mock
    private InspectionRecordMapper recordMapper;

    @Mock
    private InspectionTaskMapper taskMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private InspectionRecordService service;

    @Test
    @DisplayName("startDate 与 endDate 为空时不触发日期解析异常")
    void allowsNullOrBlankDateRange() {
        Page<InspectionRecord> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(recordMapper.selectPage(any(), any())).thenReturn(emptyPage);

        RecordQuery q = new RecordQuery();
        q.setStartDate(null);
        q.setEndDate("  ");

        PageResult<InspectionRecord> res = service.page(q);
        assertEquals(0L, res.getTotal());
    }

    @Test
    @DisplayName("合法 yyyy-MM-dd 日期范围正常分页查询")
    void acceptsValidDateRange() {
        Page<InspectionRecord> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(recordMapper.selectPage(any(), any())).thenReturn(emptyPage);

        RecordQuery q = new RecordQuery();
        q.setStartDate("2026-10-01");
        q.setEndDate("2026-10-31");

        PageResult<InspectionRecord> res = service.page(q);
        assertEquals(0L, res.getTotal());
    }

    @Test
    @DisplayName("非法日期格式返回 400 业务异常")
    void rejectsInvalidDateFormat() {
        RecordQuery q = new RecordQuery();
        q.setStartDate("2026/10/01");

        BizException ex = assertThrows(BizException.class, () -> service.page(q));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
