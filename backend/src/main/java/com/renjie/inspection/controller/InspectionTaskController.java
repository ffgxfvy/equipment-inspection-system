package com.renjie.inspection.controller;

import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.common.R;
import com.renjie.inspection.dto.TaskQuery;
import com.renjie.inspection.dto.TaskRequest;
import com.renjie.inspection.entity.InspectionTask;
import com.renjie.inspection.security.RequireRole;
import com.renjie.inspection.service.InspectionTaskService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 巡检任务接口。
 */
@Tag(name = "03. 巡检任务", description = "任务创建、分派与状态流转")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class InspectionTaskController {

    private final InspectionTaskService taskService;

    @Operation(summary = "分页查询任务")
    @GetMapping
    public R<PageResult<InspectionTask>> page(@Valid TaskQuery query) {
        return R.ok(taskService.page(query));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public R<InspectionTask> detail(@PathVariable Long id) {
        return R.ok(taskService.getById(id));
    }

    @Operation(summary = "创建任务")
    @RequireRole("ADMIN")
    @PostMapping
    public R<InspectionTask> create(@Valid @RequestBody TaskRequest request) {
        return R.ok("创建成功", taskService.create(request));
    }

    @Operation(summary = "修改任务")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public R<InspectionTask> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return R.ok("修改成功", taskService.update(id, request));
    }

    @Operation(summary = "分派巡检员")
    @RequireRole("ADMIN")
    @PutMapping("/{id}/assign")
    public R<InspectionTask> assign(@PathVariable Long id, @RequestParam Long inspectorId) {
        return R.ok("分派成功", taskService.assign(id, inspectorId));
    }

    @Operation(summary = "流转任务状态",
            description = "合法流转：PENDING→DOING/DONE/CANCELLED，DOING→DONE/CANCELLED。"
                    + "推进到 DONE 会自动回写设备的巡检时间。")
    @PutMapping("/{id}/status")
    public R<InspectionTask> changeStatus(@PathVariable Long id, @RequestParam String status) {
        return R.ok("状态已更新", taskService.changeStatus(id, status));
    }

    @Operation(summary = "删除任务")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return R.ok("删除成功", null);
    }
}
