package com.shop.config.fesod;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.poi.ss.usermodel.*;

/**
 * @author xxj
 * @title CustomCellStyleWriteHandler
 * @date 2026/8/26 11:22
 * @description TODO
 */
@Slf4j
public class CustomCellStyleWriteHandler implements CellWriteHandler {

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        // 仅设置内容单元格的样式
        if (BooleanUtils.isNotTrue(context.getHead())) {
            WriteCellData<?> cellData = context.getFirstCellData();
            WriteCellStyle writeCellStyle = cellData.getOrCreateStyle();

            // 设置背景颜色为黄色
//            writeCellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
//            writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);

            // 设置字体为蓝色
            WriteFont writeFont = new WriteFont();
//            writeFont.setColor(IndexedColors.BLUE.getIndex());
            writeFont.setFontHeightInPoints((short) 12); // 字体大小为14
            writeCellStyle.setWriteFont(writeFont);

            writeCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER); // 水平居中
            writeCellStyle.setVerticalAlignment(VerticalAlignment.CENTER); // 垂直居中


//            log.info("已自定义单元格样式: 行 {}, 列 {}", context.getRowIndex(), context.getColumnIndex());
        }
    }
}
