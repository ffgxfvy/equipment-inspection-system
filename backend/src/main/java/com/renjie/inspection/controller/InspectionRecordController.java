package com.renjie.inspection.controller;

import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.common.R;
import com.renjie.inspection.dto.HandleRequest;
import com.renjie.inspection.dto.RecordQuery;
import com.renjie.inspection.dto.RecordRequest;
import com.renjie.inspection.entity.InspectionRecord;
import com.renjie.inspection.service.InspectionRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 巡检记录接口。
 */
@Tag(name = "04. 巡检记录", description = "记录提交与异常处理")
@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class InspectionRecordController {

    private final InspectionRecordService recordService;

    @Operation(summary = "分页查询记录")
    @GetMapping
    public R<PageResult<InspectionRecord>> page(@Valid RecordQuery query) {
        return R.ok(recordService.page(query));
    }

    @Operation(summary = "记录详情")
    @GetMapping("/{id}")
    public R<InspectionRecord> detail(@PathVariable Long id) {
        return R.ok(recordService.getById(id));
    }

    @Operation(summary = "提交巡检记录",
            description = "结论为 ABNORMAL 时必须填写 abnormalDesc。"
                    + "提交后会联动：回写设备巡检时间、异常时把设备置为故障、"
                    + "若带 taskId 则把任务置为已完成。")
    @PostMapping
    public R<InspectionRecord> submit(@Valid @RequestBody RecordRequest request) {
        return R.ok("提交成功", recordService.submit(request));
    }

    @Operation(summary = "处理异常记录",
            description = "handleStatus 取 HANDLING 或 HANDLED；"
                    + "标记为 HANDLED 时必须填写 handleRemark，"
                    + "且当该设备已无未处理异常时自动把设备恢复为运行。")
    @PutMapping("/{id}/handle")
    public R<InspectionRecord> handle(@PathVariable Long id,
                                      @Valid @RequestBody HandleRequest request) {
        return R.ok("处理成功", recordService.handle(id, request));
    }

    @Operation(summary = "删除记录")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return R.ok("删除成功", null);
    }
}
