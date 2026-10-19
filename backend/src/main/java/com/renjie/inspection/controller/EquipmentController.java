package com.renjie.inspection.controller;

import com.renjie.inspection.common.BizException;
import com.renjie.inspection.common.ErrorCode;
import com.renjie.inspection.common.PageResult;
import com.renjie.inspection.common.R;
import com.renjie.inspection.dto.EquipmentQuery;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.InspectionRecord;
import com.renjie.inspection.security.RequireRole;
import com.renjie.inspection.dto.ImportResult;
import com.renjie.inspection.service.EquipmentExcelService;
import com.renjie.inspection.service.EquipmentImportService;
import com.renjie.inspection.service.EquipmentService;
import com.renjie.inspection.service.InspectionRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.List;

/**
 * 设备台账接口。
 *
 * <p>权限约定：查询所有登录用户可用；新增/修改/删除限管理员。
 * 这样巡检员能查设备但改不了台账，符合现场职责划分。
 */
@Tag(name = "02. 设备台账", description = "设备的增删改查与类目")
@Slf4j
@RestController
@RequestMapping("/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final InspectionRecordService recordService;
    private final EquipmentExcelService equipmentExcelService;
    private final EquipmentImportService equipmentImportService;

    @Operation(summary = "分页查询设备")
    @GetMapping
    public R<PageResult<Equipment>> page(@Valid EquipmentQuery query) {
        return R.ok(equipmentService.page(query));
    }

    @Operation(summary = "设备详情")
    @GetMapping("/{id}")
    public R<Equipment> detail(@PathVariable Long id) {
        return R.ok(equipmentService.getById(id));
    }

    @Operation(summary = "导出设备台账为 Excel",
            description = "筛选条件与列表接口完全一致，导出的是当前筛选结果。"
                    + "单次最多 " + EquipmentExcelService.MAX_EXPORT_ROWS + " 行，超出会截断并在文件末尾标注。")
    @GetMapping("/export")
    public void export(@Valid EquipmentQuery query, HttpServletResponse response) throws IOException {
        // 先取数据，避免写了一半响应才发现数据有问题，导致前端拿到损坏的文件
        List<Equipment> equipments = equipmentService.listForExport(query);

        String fileName = equipmentExcelService.fileName();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        // 中文文件名必须同时给 filename 和 filename*：
        //   filename  : 老浏览器兜底（需要 URL 编码，否则中文会被 HTTP 头截断）
        //   filename* : RFC 5987 标准写法，现代浏览器优先用它，能正确显示中文
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        // 顺便把行数放在响应头里，前端可以直接读到
        int written = equipmentExcelService.writeExcel(equipments, response.getOutputStream());
        log.info("导出设备台账完成，共 {} 行", written);
    }

    @Operation(summary = "从 Excel 导入设备台账",
            description = "表头需与导出的模板一致（至少包含：设备编号、设备名称、类目、安装位置）。"
                    + "逐行独立处理：格式正确的行会写入，错误行会返回行号与原因。"
                    + "单次最多 " + EquipmentExcelService.MAX_IMPORT_ROWS + " 行。"
                    + "注意：编号已存在的行会报错而不是覆盖。")
    @RequireRole("ADMIN")
    @PostMapping("/import")
    public R<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择要导入的文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            // 只支持 xlsx：.xls 是另一种二进制格式，需要 HSSFWorkbook，本项目不引入
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "只支持 .xlsx 格式（Excel 2007 及以上）。如果是 .xls，请先另存为 .xlsx。");
        }
        return R.ok("导入完成", equipmentImportService.importFrom(file.getInputStream()));
    }

    @Operation(summary = "设备类目列表（下拉框用）")
    @GetMapping("/categories")
    public R<List<String>> categories() {
        return R.ok(equipmentService.listCategories());
    }

    @Operation(summary = "设备的历史巡检记录")
    @GetMapping("/{id}/records")
    public R<List<InspectionRecord>> records(@PathVariable Long id,
                                             @RequestParam(defaultValue = "20") int limit) {
        return R.ok(recordService.listByEquipment(id, limit));
    }

    @Operation(summary = "新增设备（仅管理员）")
    @RequireRole("ADMIN")
    @PostMapping
    public R<Equipment> create(@Valid @RequestBody Equipment equipment) {
        return R.ok("新增成功", equipmentService.create(equipment));
    }

    @Operation(summary = "修改设备（仅管理员）")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public R<Equipment> update(@PathVariable Long id, @Valid @RequestBody Equipment equipment) {
        return R.ok("修改成功", equipmentService.update(id, equipment));
    }

    @Operation(summary = "删除设备（仅管理员，逻辑删除）")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return R.ok("删除成功", null);
    }
}
