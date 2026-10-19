package com.renjie.inspection.controller;

import com.renjie.inspection.common.R;
import com.renjie.inspection.dto.DashboardVO;
import com.renjie.inspection.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页看板接口。
 */
@Tag(name = "05. 首页看板", description = "统计卡片、图表数据与待处理异常")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "看板数据", description = "一次返回首页所需的全部统计数据")
    @GetMapping
    public R<DashboardVO> dashboard() {
        return R.ok(dashboardService.build());
    }
}
