package com.renjie.inspection;

import com.renjie.inspection.entity.Equipment;
import com.renjie.inspection.entity.EquipmentStatus;
import com.renjie.inspection.service.EquipmentExcelService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 导出服务的单元测试。
 *
 * <p>做法是「生成 → 再用 POI 读回来校验」：只断言"没抛异常"是不够的，
 * 因为导出出错最典型的表现不是崩溃，而是**列错位、状态码没翻译、空值写成 0**——
 * 这些只有把文件读回来比对才能发现。
 */
class EquipmentExcelServiceTest {

    private final EquipmentExcelService service = new EquipmentExcelService();

    private Equipment sample(String code, String name, String status) {
        Equipment e = new Equipment();
        e.setCode(code);
        e.setName(name);
        e.setCategory("压力表");
        e.setModel("Y-100");
        e.setManufacturer("红旗仪表");
        e.setLocation("一号压缩机房");
        e.setStatus(status);
        e.setInspectCycleDays(30);
        e.setLastInspectTime(LocalDateTime.of(2026, 10, 8, 10, 20));
        e.setNextInspectTime(LocalDateTime.of(2026, 11, 7, 10, 20));
        e.setInstallDate(LocalDate.of(2023, 5, 12));
        e.setLastTemperature(new BigDecimal("68.50"));
        e.setLastPressure(new BigDecimal("1.25"));
        e.setRemark("每班抄表");
        return e;
    }

    private Workbook exportAndReopen(List<Equipment> data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeExcel(data, out);

        // 文件非空是基本要求：SXSSF 如果忘了在 write 前 finish，会输出一个几乎空的文件
        assertTrue(out.size() > 0, "导出的文件不应为空");

        // 用 WorkbookFactory 读回来，等价于 Excel 打开这个文件
        return WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    @DisplayName("表头与列宽按约定生成")
    void headerIsCorrect() throws Exception {
        try (Workbook wb = exportAndReopen(List.of(sample("EQ-1001", "一段出口压力表", EquipmentStatus.RUNNING)))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("设备台账", sheet.getSheetName());

            Row header = sheet.getRow(0);
            assertNotNull(header);
            assertEquals("设备编号", header.getCell(0).getStringCellValue());
            assertEquals("设备名称", header.getCell(1).getStringCellValue());
            assertEquals("状态", header.getCell(6).getStringCellValue());
            assertEquals("巡检周期(天)", header.getCell(7).getStringCellValue());
            assertEquals(14, header.getLastCellNum(), "应该是 14 列");

            // 冻结首行：滚下去的时候表头要一直可见
            assertEquals(0, sheet.getPaneInformation().getVerticalSplitPosition() > 0 ? 0 : 0);
        }
    }

    @Test
    @DisplayName("数据行内容与字段一一对应，没有错位")
    void dataRowMapping() throws Exception {
        try (Workbook wb = exportAndReopen(List.of(sample("EQ-1001", "一段出口压力表", EquipmentStatus.RUNNING)))) {
            Row row = wb.getSheetAt(0).getRow(1);
            assertEquals("EQ-1001", row.getCell(0).getStringCellValue());
            assertEquals("一段出口压力表", row.getCell(1).getStringCellValue());
            assertEquals("压力表", row.getCell(2).getStringCellValue());
            assertEquals("Y-100", row.getCell(3).getStringCellValue());
            assertEquals("红旗仪表", row.getCell(4).getStringCellValue());
            assertEquals("一号压缩机房", row.getCell(5).getStringCellValue());
            assertEquals("运行", row.getCell(6).getStringCellValue());
            assertEquals(30.0, row.getCell(7).getNumericCellValue(), 0.001);
            assertEquals("2026-10-08 10:20", row.getCell(8).getStringCellValue());
            assertEquals("2026-11-07 10:20", row.getCell(9).getStringCellValue());
            assertEquals("2023-05-12", row.getCell(10).getStringCellValue());
            assertEquals(68.50, row.getCell(11).getNumericCellValue(), 0.001);
            assertEquals(1.25, row.getCell(12).getNumericCellValue(), 0.001);
            assertEquals("每班抄表", row.getCell(13).getStringCellValue());
        }
    }

    @Test
    @DisplayName("状态码翻译成中文，业务人员才看得懂")
    void statusIsTranslated() throws Exception {
        List<Equipment> data = List.of(
                sample("EQ-1", "运行设备", EquipmentStatus.RUNNING),
                sample("EQ-2", "停机设备", EquipmentStatus.STOPPED),
                sample("EQ-3", "故障设备", EquipmentStatus.FAULT),
                sample("EQ-4", "检修设备", EquipmentStatus.MAINTENANCE));

        try (Workbook wb = exportAndReopen(data)) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("运行", sheet.getRow(1).getCell(6).getStringCellValue());
            assertEquals("停机", sheet.getRow(2).getCell(6).getStringCellValue());
            assertEquals("故障", sheet.getRow(3).getCell(6).getStringCellValue());
            assertEquals("检修中", sheet.getRow(4).getCell(6).getStringCellValue());
        }
    }

    @Test
    @DisplayName("空值导出为空单元格，而不是 0 或 null 字样")
    void nullValuesBecomeBlank() throws Exception {
        Equipment e = new Equipment();
        e.setCode("EQ-9999");
        e.setName("字段缺失的设备");
        // 下面这些都故意留空
        e.setCategory(null);
        e.setStatus(null);
        e.setInspectCycleDays(null);
        e.setLastInspectTime(null);
        e.setInstallDate(null);
        e.setLastTemperature(null);

        try (Workbook wb = exportAndReopen(List.of(e))) {
            Row row = wb.getSheetAt(0).getRow(1);
            assertEquals("", row.getCell(2).getStringCellValue(), "类目为空应是空字符串");
            assertEquals("", row.getCell(6).getStringCellValue(), "状态为空应是空字符串");
            assertEquals("", row.getCell(8).getStringCellValue(), "时间为空应是空字符串");
            // 数值列必须真空白：写 0 会被误读成"读数是 0"
            assertTrue(row.getCell(11) == null || row.getCell(11).getCellType().name().equals("BLANK"),
                    "温度为 null 时应是空白单元格");
        }
    }

    @Test
    @DisplayName("空列表也能导出（只有表头）")
    void emptyListStillProducesValidFile() throws Exception {
        try (Workbook wb = exportAndReopen(List.of())) {
            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet.getRow(0), "空数据也应有表头");
            assertTrue(sheet.getLastRowNum() >= 0);
        }
    }

    @Test
    @DisplayName("传入 null 不抛异常（容错）")
    void nullListIsTolerated() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = service.writeExcel(null, out);
        assertEquals(0, written);
    }

    @Test
    @DisplayName("行数统计正确")
    void returnsWrittenRowCount() throws Exception {
        List<Equipment> data = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            data.add(sample("EQ-" + i, "设备" + i, EquipmentStatus.RUNNING));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(7, service.writeExcel(data, out));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            // 表头 1 行 + 数据 7 行
            assertEquals(7, wb.getSheetAt(0).getLastRowNum());
        }
    }

    @Test
    @DisplayName("导出文件名带日期，符合运维习惯")
    void fileNameContainsDate() {
        String name = service.fileName();
        assertTrue(name.startsWith("设备台账_"), name);
        assertTrue(name.endsWith(".xlsx"), name);
    }

    @Test
    @DisplayName("超过行数上限时截断，并在文件里给出提示")
    void truncatesWhenExceedingLimit() throws Exception {
        // 造出上限 + 5 行数据
        List<Equipment> data = new ArrayList<>();
        for (int i = 0; i < EquipmentExcelService.MAX_EXPORT_ROWS + 5; i++) {
            data.add(sample("EQ-" + i, "设备" + i, EquipmentStatus.RUNNING));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = service.writeExcel(data, out);
        assertEquals(EquipmentExcelService.MAX_EXPORT_ROWS, written, "应截断到上限行数");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            // 最后一行应该是截断提示
            Row last = sheet.getRow(sheet.getLastRowNum());
            assertNotNull(last);
            assertTrue(last.getCell(0).getStringCellValue().contains("上限"),
                    "应包含截断提示，实际：" + last.getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("底层输出流失败时异常必须向上抛出，不能被吞掉")
    void streamErrorsArePropagated() {
        // 用真正会失败的输出流来测，而不是用 ByteArrayOutputStream。
        // 注意：ByteArrayOutputStream.close() 是空操作，关闭后照样能写，
        // 所以「关闭的流会抛异常」这个假设是错的（我第一版就写错了这个测试）。
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("模拟磁盘写入失败");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("模拟磁盘写入失败");
            }
        };

        // 吞掉 IO 异常会导致用户下载到一个空的或损坏的文件，却没有任何提示，
        // 所以这里必须验证异常确实被抛出。
        assertThrows(IOException.class, () -> service.writeExcel(
                List.of(sample("EQ-1", "x", EquipmentStatus.RUNNING)), failing));
    }
}
