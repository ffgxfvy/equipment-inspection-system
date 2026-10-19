package com.renjie.inspection.service;

import com.renjie.inspection.common.BizException;
import com.renjie.inspection.dto.ImportResult;
import com.renjie.inspection.dto.ImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 设备台账 Excel 导入。
 *
 * <p><b>为什么是「部分成功」而不是「全成功或全失败」</b>
 *
 * <p>导入一份 200 行的表，如果第 37 行格式不对，两种做法的体验差别很大：
 * <ul>
 *   <li>全量事务：整批回滚，用户改完第 37 行再导一次——如果还有第 89 行也错了，
 *       又要来一轮。200 行可能要试十几次；</li>
 *   <li><b>逐行独立（本项目采用）</b>：能导的先导进去，返回"成功 197 行，失败 3 行，
 *       第 37/89/152 行分别是……"，用户改这三行单独再导即可。</li>
 * </ul>
 *
 * <p>代价是「数据库里已经写进去一部分」，所以接口必须返回明确的成功/失败统计，
 * 不能让调用方以为整批都成功了。
 *
 * <p>这里逐行调用 {@link EquipmentService#create}，而不是自己拼 insert：
 * 这样<b>导入和手工新增走的是同一套校验规则</b>。如果各写一套，
 * 迟早出现"页面上不允许的编号，导入却能成功"这类不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentImportService {

    private final EquipmentExcelService excelService;
    private final EquipmentService equipmentService;

    public ImportResult importFrom(InputStream in) throws IOException {
        List<ImportRow> rows = excelService.parse(in);

        ImportResult result = new ImportResult();
        result.setTotal(rows.size());

        for (ImportRow row : rows) {
            if (row.hasError()) {
                result.addError(row.getRowNum(), row.getError());
                continue;
            }
            try {
                equipmentService.create(row.getEquipment());
                result.addSuccess();
            } catch (BizException ex) {
                // 业务校验失败（编号重复、状态非法等）→ 记为该行错误，继续处理下一行
                result.addError(row.getRowNum(), ex.getMessage());
            } catch (Exception ex) {
                // 非预期异常也记录行号，否则用户只知道"导入失败"却不知道是哪一行
                log.error("导入第 {} 行时发生异常", row.getRowNum(), ex);
                result.addError(row.getRowNum(), "写入失败，请联系管理员");
            }
        }

        log.info("导入完成：总计 {} 行，成功 {}，失败 {}", result.getTotal(),
                result.getSuccessCount(), result.getFailCount());
        return result;
    }
}
