package com.renjie.inspection;

import com.renjie.inspection.common.BizException;
import com.renjie.inspection.dto.ImportRow;
import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.service.EquipmentExcelService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 导入解析的测试。
 *
 * <p>重点验证三件事：
 * ① 按<b>表头名</b>定位列（列顺序打乱也能读对）；
 * ② 单行出错不影响其它行；
 * ③ 空值、中文状态、日期格式等边界。
 */
class EquipmentImportParseTest {

    private final EquipmentExcelService service = new EquipmentExcelService();

    private static final String[] HEADERS = {
            "设备编号", "设备名称", "类目", "规格型号", "生产厂商", "安装位置",
            "状态", "巡检周期(天)", "投运日期", "备注"
    };

    /** 用给定的表头与数据行构造一个 xlsx */
    private ByteArrayInputStream buildSheet(String[] headers, String[][] dataRows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("设备台账");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    String v = dataRows[r][c];
                    if (v != null) {
                        row.createCell(c).setCellValue(v);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.close();
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private String[] fullRow(String code, String name, String category, String location,
                             String status, String cycle, String installDate) {
        return new String[]{code, name, category, "Y-100", "红旗仪表", location,
                status, cycle, installDate, "备注"};
    }

    @Test
    @DisplayName("正常行解析出实体，字段一一对应")
    void parsesValidRow() throws Exception {
        ByteArrayInputStream in = buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2001", "新增压力表", "压力表", "三号机房", "运行", "45", "2024-06-01")
        });

        List<ImportRow> rows = service.parse(in);
        assertEquals(1, rows.size());
        ImportRow row = rows.get(0);
        assertEquals(2, row.getRowNum(), "数据从第 2 行开始");
        assertNull(row.getError());
        assertNotNull(row.getEquipment());

        Equipment e = row.getEquipment();
        assertEquals("EQ-2001", e.getCode());
        assertEquals("新增压力表", e.getName());
        assertEquals("三号机房", e.getLocation());
        assertEquals("RUNNING", e.getStatus(), "中文状态应转成英文");
        assertEquals(45, e.getInspectCycleDays());
        assertEquals(2024, e.getInstallDate().getYear());
        assertEquals(6, e.getInstallDate().getMonthValue());
    }

    @Test
    @DisplayName("列顺序打乱也能按表头名读对（这是按序号读会出错的地方）")
    void readsByHeaderNameNotIndex() throws Exception {
        String[] shuffled = {"安装位置", "设备编号", "状态", "设备名称", "类目", "巡检周期(天)"};
        String[] data = {"二号机房", "EQ-2002", "故障", "乱序压力表", "压力表", "30"};

        List<ImportRow> rows = service.parse(buildSheet(shuffled, new String[][]{data}));
        Equipment e = rows.get(0).getEquipment();
        assertEquals("EQ-2002", e.getCode());
        assertEquals("乱序压力表", e.getName());
        assertEquals("二号机房", e.getLocation());
        assertEquals("FAULT", e.getStatus());
    }

    @Test
    @DisplayName("缺少必需列时整体报错，并指出缺哪一列")
    void missingRequiredHeaderFails() throws Exception {
        String[] bad = {"设备编号", "设备名称", "类目"};   // 少了「安装位置」
        BizException ex = assertThrows(BizException.class,
                () -> service.parse(buildSheet(bad, new String[][]{{"EQ-1", "x", "y"}})));
        assertTrue(ex.getMessage().contains("安装位置"), ex.getMessage());
    }

    @Test
    @DisplayName("单行缺必填字段只影响该行，其它行照常解析")
    void badRowDoesNotAbortOthers() throws Exception {
        ByteArrayInputStream in = buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2003", "正常行", "压力表", "A 区", "运行", "30", "2024-01-01"),
                fullRow("", "缺编号", "压力表", "B 区", "运行", "30", "2024-01-01"),
                fullRow("EQ-2004", "正常行2", "压力表", "C 区", "运行", "30", "2024-01-01"),
        });

        List<ImportRow> rows = service.parse(in);
        assertEquals(3, rows.size());
        assertNull(rows.get(0).getError());
        assertNotNull(rows.get(1).getError());
        assertTrue(rows.get(1).getError().contains("设备编号"), rows.get(1).getError());
        assertNull(rows.get(2).getError(), "第 3 行不应被第 2 行的错误影响");
    }

    @Test
    @DisplayName("空行跳过，不计入错误")
    void blankRowsAreSkipped() throws Exception {
        ByteArrayInputStream in = buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2005", "有效行", "压力表", "A 区", "运行", "30", "2024-01-01"),
                new String[]{null, null, null, null, null, null, null, null, null, null},
                fullRow("EQ-2006", "有效行2", "压力表", "B 区", "运行", "30", "2024-01-01"),
        });
        assertEquals(2, service.parse(in).size());
    }

    @Test
    @DisplayName("英文状态也接受（人手工填表时可能写 RUNNING）")
    void acceptsEnglishStatus() throws Exception {
        List<ImportRow> rows = service.parse(buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2007", "英文状态", "压力表", "A 区", "maintenance", "30", "2024-01-01")
        }));
        assertEquals("MAINTENANCE", rows.get(0).getEquipment().getStatus());
    }

    @Test
    @DisplayName("非法状态给出可选值提示")
    void rejectsInvalidStatus() throws Exception {
        List<ImportRow> rows = service.parse(buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2008", "非法状态", "压力表", "A 区", "已报废", "30", "2024-01-01")
        }));
        assertTrue(rows.get(0).getError().contains("状态取值非法"), rows.get(0).getError());
    }

    @Test
    @DisplayName("巡检周期越界被拒")
    void rejectsOutOfRangeCycle() throws Exception {
        List<ImportRow> rows = service.parse(buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2009", "周期越界", "压力表", "A 区", "运行", "999", "2024-01-01")
        }));
        assertTrue(rows.get(0).getError().contains("1–365"), rows.get(0).getError());
    }

    @Test
    @DisplayName("日期兼容 yyyy/MM/dd 与 yyyy.MM.dd")
    void acceptsSeveralDateFormats() throws Exception {
        List<ImportRow> rows = service.parse(buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2010", "斜杠日期", "压力表", "A 区", "运行", "30", "2024/7/9"),
                fullRow("EQ-2011", "点号日期", "压力表", "B 区", "运行", "30", "2024.7.9"),
        }));
        assertEquals(7, rows.get(0).getEquipment().getInstallDate().getMonthValue());
        assertEquals(9, rows.get(1).getEquipment().getInstallDate().getDayOfMonth());
    }

    @Test
    @DisplayName("非法日期给出明确提示")
    void rejectsInvalidDate() throws Exception {
        List<ImportRow> rows = service.parse(buildSheet(HEADERS, new String[][]{
                fullRow("EQ-2012", "坏日期", "压力表", "A 区", "运行", "30", "2024-13-45")
        }));
        assertTrue(rows.get(0).getError().contains("日期"), rows.get(0).getError());
    }

    @Test
    @DisplayName("可选的列不存在时不报错（兼容用户删列）")
    void missingOptionalColumnIsFine() throws Exception {
        String[] minimal = {"设备编号", "设备名称", "类目", "安装位置"};
        List<ImportRow> rows = service.parse(buildSheet(minimal, new String[][]{
                {"EQ-2013", "最简行", "压力表", "A 区"}
        }));
        assertNull(rows.get(0).getError());
        assertNull(rows.get(0).getEquipment().getStatus(), "未填状态应留给 Service 填默认值");
        assertNull(rows.get(0).getEquipment().getInspectCycleDays());
    }

    @Test
    @DisplayName("第一行不是表头时报错")
    void emptySheetFails() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("空表");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.close();
            assertThrows(BizException.class,
                    () -> service.parse(new ByteArrayInputStream(out.toByteArray())));
        }
    }
}
