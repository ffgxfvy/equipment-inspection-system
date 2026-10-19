package com.renjie.inspection.service;

import com.renjie.inspection.common.BizException;
import com.renjie.inspection.dto.ImportRow;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.EquipmentStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备台账 Excel 导出。
 *
 * <p><b>为什么用 SXSSFWorkbook 而不是 XSSFWorkbook</b>
 *
 * <p>POI 提供两套写 .xlsx 的实现：
 * <ul>
 *   <li>{@code XSSFWorkbook}：把整个工作簿放在 JVM 内存里，最后一次性写出。
 *       代码简单，但导 10 万行时会吃掉几百 MB 内存，很容易 OOM；</li>
 *   <li>{@code SXSSFWorkbook}（本类使用）：<b>滑动窗口</b>式写入——
 *       内存里只保留最近的 N 行，更早的行已经刷到临时文件里。
 *       内存占用基本恒定，导出量再大也不会打爆 JVM。</li>
 * </ul>
 *
 * <p>SXSSF 的代价有两个，都能处理：
 * <ol>
 *   <li>它会创建临时文件，必须调用 {@link SXSSFWorkbook#dispose()} 清理，
 *       否则磁盘上会堆一堆 poi 临时文件（本类用 try/finally 保证执行）；</li>
 *   <li>不支持 {@code autoSizeColumn()}（因为列已经写出去了，无法再回头测量），
 *       所以要<b>预设列宽</b>。这其实也更稳：autosize 依赖字体度量，
 *       在不同环境下算出来的宽度可能不一样。</li>
 * </ol>
 *
 * <p>另外导出一定要有<b>行数上限</b>——见 {@link #MAX_EXPORT_ROWS}。
 * 不加限制的话，前端传个空条件触发全表导出，就是一次自我 DDoS。
 * 真要做无限量导出，应该走「异步任务 + 导出完成后通知下载」的路子，而不是让 HTTP 请求挂着。
 */
@Slf4j
@Service
public class EquipmentExcelService {

    /** 单次导出行数上限。超过就截断并在末尾标注，避免一个大查询把服务拖死。 */
    public static final int MAX_EXPORT_ROWS = 5000;

    /** SXSSF 内存中保留的行数，超过就刷到临时文件 */
    private static final int ROW_WINDOW = 200;

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 表头与字段的对应关系。用数组而不是散落的 setCellValue 调用，增删列时改一处即可。 */
    private static final String[] HEADERS = {
            "设备编号", "设备名称", "类目", "规格型号", "生产厂商", "安装位置",
            "状态", "巡检周期(天)", "上次巡检", "下次巡检", "投运日期",
            "最近温度", "最近压力", "备注"
    };

    /** 各列宽度（字符数）。预设而不是自动计算，见类注释里的说明。 */
    private static final int[] COLUMN_WIDTHS = {
            14, 22, 12, 16, 16, 26,
            10, 13, 18, 18, 14,
            11, 11, 30
    };

    /**
     * 把设备列表写成 Excel 并输出到给定流。
     *
     * <p>做成「接收 List + 输出流」而不是「接收 HttpServletResponse」，
     * 是为了能用 ByteArrayOutputStream 做单元测试，不依赖 Web 容器。
     *
     * @return 实际写入的数据行数（不含表头）
     */
    public int writeExcel(List<Equipment> equipments, OutputStream out) throws IOException {
        List<Equipment> rows = equipments == null ? List.of() : equipments;
        boolean truncated = rows.size() > MAX_EXPORT_ROWS;
        if (truncated) {
            rows = rows.subList(0, MAX_EXPORT_ROWS);
            log.warn("导出数据量超过上限，已截断为 {} 行", MAX_EXPORT_ROWS);
        }

        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
        try {
            workbook.setCompressTempFiles(true);   // 临时文件压缩，减少磁盘占用
            Sheet sheet = workbook.createSheet("设备台账");

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);

            // ---------- 表头 ----------
            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);   // 宽度单位是 1/256 字符
            }
            // 冻结首行：滚下去的时候表头一直在，这是导出的基本体验
            sheet.createFreezePane(0, 1);

            // ---------- 数据 ----------
            int rowIndex = 1;
            for (Equipment e : rows) {
                Row row = sheet.createRow(rowIndex++);
                int c = 0;
                writeText(row, c++, bodyStyle, e.getCode());
                writeText(row, c++, bodyStyle, e.getName());
                writeText(row, c++, bodyStyle, e.getCategory());
                writeText(row, c++, bodyStyle, e.getModel());
                writeText(row, c++, bodyStyle, e.getManufacturer());
                writeText(row, c++, bodyStyle, e.getLocation());
                writeText(row, c++, bodyStyle, statusLabel(e.getStatus()));
                writeNumber(row, c++, bodyStyle, e.getInspectCycleDays());
                writeText(row, c++, bodyStyle, formatDateTime(e.getLastInspectTime()));
                writeText(row, c++, bodyStyle, formatDateTime(e.getNextInspectTime()));
                writeText(row, c++, bodyStyle, formatDate(e.getInstallDate()));
                writeNumber(row, c++, bodyStyle, e.getLastTemperature());
                writeNumber(row, c++, bodyStyle, e.getLastPressure());
                writeText(row, c++, bodyStyle, e.getRemark());
            }

            // ---------- 截断提示 ----------
            if (truncated) {
                Row note = sheet.createRow(rowIndex + 1);
                Cell cell = note.createCell(0);
                cell.setCellValue("注意：数据超过 " + MAX_EXPORT_ROWS + " 行上限，本次仅导出前 " + MAX_EXPORT_ROWS + " 行。请缩小筛选条件后重试。");
            }

            // 注意：必须在 write 之前 finish 掉 sheet，否则最后几行窗口内的数据不会被刷出
            workbook.write(out);
            log.info("导出设备台账 {} 行", rowIndex - 1);
            return rowIndex - 1;
        } finally {
            // dispose 会删掉 SXSSF 在磁盘上创建的临时文件。
            // 漏掉这一步，长时间运行的服务磁盘会被临时文件占满——这是 SXSSF 最常见的坑。
            workbook.dispose();
            workbook.close();
        }
    }

    public String fileName() {
        return "设备台账_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
    }

    // ==================== 读取（导入） ====================

    /** 单次导入行数上限，与导出上限分开——导入要逐行落库，成本更高 */
    public static final int MAX_IMPORT_ROWS = 2000;

    /** 表头里必须有这几列，否则说明拿错了文件 */
    private static final Set<String> REQUIRED_HEADERS = Set.of("设备编号", "设备名称", "类目", "安装位置");

    /** 状态的中英文映射：导出的是中文，人手工填的可能是英文，两种都接受 */
    private static final Map<String, String> STATUS_ALIASES = Map.of(
            "运行", EquipmentStatus.RUNNING,
            "停机", EquipmentStatus.STOPPED,
            "故障", EquipmentStatus.FAULT,
            "检修中", EquipmentStatus.MAINTENANCE);

    /**
     * 解析上传的 Excel。
     *
     * <p>三条设计原则：
     * <ol>
     *   <li><b>按表头名定位列，不按列序号</b>——用户可能删掉"备注"列，
     *       或调换顺序。按序号读会在这种情况下把"安装位置"读进"类目"里，
     *       而且不会报错，只是数据全错，最难排查；</li>
     *   <li><b>单行解析失败不中断</b>——错误记到 {@link ImportRow#getError()}，
     *       最终一次性汇总给用户；</li>
     *   <li><b>只读用户该填的字段</b>——上次/下次巡检时间、最近读数由系统维护，
     *       即使表里有这些列也不导入，避免绕过业务规则。</li>
     * </ol>
     */
    public List<ImportRow> parse(InputStream in) throws IOException {
        List<ImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                throw BizException.badRequest("文件里没有工作表");
            }
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BizException.badRequest("第一行必须是表头");
            }

            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : headerRow) {
                String name = readString(cell, formatter).trim();
                if (!name.isEmpty()) {
                    columns.putIfAbsent(name, cell.getColumnIndex());
                }
            }
            for (String required : REQUIRED_HEADERS) {
                if (!columns.containsKey(required)) {
                    throw BizException.badRequest(
                            "表头缺少必需列：" + required + "。请使用导出的模板填写。");
                }
            }

            int lastRow = sheet.getLastRowNum();
            if (lastRow > MAX_IMPORT_ROWS) {
                throw BizException.badRequest("单次最多导入 " + MAX_IMPORT_ROWS + " 行，当前 " + lastRow + " 行");
            }

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (isBlankRow(row, formatter)) {
                    continue;   // 跳过空行，不算错误
                }
                int rowNum = i + 1;   // Excel 行号从 1 开始，表头占第 1 行
                try {
                    rows.add(new ImportRow(rowNum, toEquipment(row, columns, formatter), null));
                } catch (BizException ex) {
                    rows.add(new ImportRow(rowNum, null, ex.getMessage()));
                }
            }
        }
        return rows;
    }

    /** 把一行单元格转成实体；任何字段不合法都抛 BizException，由上层记为该行错误 */
    private Equipment toEquipment(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        Equipment e = new Equipment();
        e.setCode(requiredText(row, columns, formatter, "设备编号", 50));
        e.setName(requiredText(row, columns, formatter, "设备名称", 100));
        e.setCategory(requiredText(row, columns, formatter, "类目", 50));
        e.setLocation(requiredText(row, columns, formatter, "安装位置", 200));

        e.setModel(optionalText(row, columns, formatter, "规格型号", 100));
        e.setManufacturer(optionalText(row, columns, formatter, "生产厂商", 100));
        e.setRemark(optionalText(row, columns, formatter, "备注", 500));

        // 状态：中英文都接受；为空时交给 Service 的 normalize 填默认值
        String status = optionalText(row, columns, formatter, "状态", 20);
        if (status != null && !status.isEmpty()) {
            String normalized = STATUS_ALIASES.getOrDefault(status, status.toUpperCase());
            if (!EquipmentStatus.ALL.contains(normalized)) {
                throw BizException.badRequest("状态取值非法：" + status + "（可选：运行/停机/故障/检修中）");
            }
            e.setStatus(normalized);
        }

        // 巡检周期
        String cycle = optionalText(row, columns, formatter, "巡检周期(天)", 10);
        if (cycle == null || cycle.isEmpty()) {
            cycle = optionalText(row, columns, formatter, "巡检周期", 10);
        }
        if (cycle != null && !cycle.isEmpty()) {
            try {
                int days = Integer.parseInt(cycle.replace(".0", ""));
                if (days < 1 || days > 365) {
                    throw BizException.badRequest("巡检周期应在 1–365 天之间，当前：" + cycle);
                }
                e.setInspectCycleDays(days);
            } catch (NumberFormatException ex) {
                throw BizException.badRequest("巡检周期不是整数：" + cycle);
            }
        }

        // 投运日期
        String installDate = optionalText(row, columns, formatter, "投运日期", 20);
        if (installDate != null && !installDate.isEmpty()) {
            e.setInstallDate(parseDate(installDate));
        }
        return e;
    }

    /** 必填文本字段：为空直接报错，并带上列名方便用户定位 */
    private String requiredText(Row row, Map<String, Integer> columns, DataFormatter formatter,
                                String column, int maxLength) {
        String value = optionalText(row, columns, formatter, column, maxLength);
        if (value == null || value.isEmpty()) {
            throw BizException.badRequest(column + "不能为空");
        }
        return value;
    }

    /**
     * 可选文本字段：列不存在或单元格为空时返回 null。
     *
     * <p>列不存在返回 null 而不是报错，是为了兼容"用户删掉了某个可选列"的情况。
     */
    private String optionalText(Row row, Map<String, Integer> columns, DataFormatter formatter,
                                String column, int maxLength) {
        Integer index = columns.get(column);
        if (index == null || row == null) {
            return null;
        }
        String value = readString(row.getCell(index), formatter).trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw BizException.badRequest(column + "超过 " + maxLength + " 个字符");
        }
        return value;
    }

    /** 用 DataFormatter 读单元格：数字 30.0 会变成 "30"，日期会按显示格式输出 */
    private String readString(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (!readString(row.getCell(i), formatter).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 日期兼容三种写法：2026-10-08、2026/10/8、2026.10.08 */
    private java.time.LocalDate parseDate(String text) {
        String normalized = text.replace('/', '-').replace('.', '-').trim();
        String[] parts = normalized.split("-");
        if (parts.length != 3) {
            throw BizException.badRequest("投运日期格式应为 yyyy-MM-dd，当前：" + text);
        }
        try {
            return java.time.LocalDate.of(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw BizException.badRequest("投运日期不是合法日期：" + text);
        }
    }

    // ==================== 样式 ====================

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildBodyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        // 自动换行：备注列可能很长，不换行会被旁边的单元格截断
        style.setWrapText(true);
        applyThinBorder(style);
        return style;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    // ==================== 单元格写入（全部做 null 保护）====================

    private void writeText(Row row, int index, CellStyle style, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int index, CellStyle style, Number value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            // 数值缺失时留空，而不是写 0——写 0 会被误读成"读数是 0"
            cell.setBlank();
        } else {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "" : time.format(DATETIME);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    /** 把数据库里的英文状态码翻译成中文，否则业务人员看不懂导出的表 */
    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case EquipmentStatus.RUNNING -> "运行";
            case EquipmentStatus.STOPPED -> "停机";
            case EquipmentStatus.FAULT -> "故障";
            case EquipmentStatus.MAINTENANCE -> "检修中";
            default -> status;
        };
    }
}
