package com.shop.config.deepoove;

import com.deepoove.poi.data.RowRenderData;
import com.deepoove.poi.data.TableRenderData;
import com.deepoove.poi.data.style.BorderStyle;
import com.deepoove.poi.policy.AbstractRenderPolicy;
import com.deepoove.poi.policy.TableRenderPolicy;
import com.deepoove.poi.render.RenderContext;
import com.deepoove.poi.util.TableTools;
import com.deepoove.poi.util.UnitUtils;
import com.deepoove.poi.xwpf.BodyContainer;
import com.deepoove.poi.xwpf.BodyContainerFactory;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.util.List;

/**
 * @author xxj
 * @title CustomTableRenderPolicy
 * @date 2026/9/1 14:51
 * @description TODO
 */
public class CustomTableRenderPolicy<T> extends AbstractRenderPolicy<T> {
    @Override
    public void doRender(RenderContext<T> context) throws Exception {
        XWPFRun run = context.getRun();
        BodyContainer bodyContainer = BodyContainerFactory.getBodyContainer(run);
        TableRenderData data = (TableRenderData)context.getData();
        List<RowRenderData> rows = data.getRows();
        if(rows.isEmpty()){
            return;
        }
        // 定义行列
        int row = rows.size(), col = rows.get(0).getCells().size();
        // 插入表格
        XWPFTable table = bodyContainer.insertNewTable(run, row, col);

        // 表格宽度
        TableTools.setWidth(table, UnitUtils.cm2Twips(14.63f) + "", null);
        // 边框和样式
//        BorderStyle build = BorderStyle.builder().withColor("000000").withSize(20).withSpace(5).build();
//        TableTools.borderTable(table, build);
        TableTools.borderTable(table, BorderStyle.DEFAULT);
        // 1) 调用XWPFTable API操作表格
        // 2) 调用TableRenderPolicy.Helper.renderRow方法快速方便的渲染一行数据
        if (!rows.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                TableRenderPolicy.Helper.renderRow(table.getRow(i), rows.get(i));
            }
        }
//        RowRenderData row0 = Rows.of("姓名", "学历").textColor("FFFFFF")
//                .bgColor("4472C4").center().create();
//        XWPFTableRow row1 = table.getRow(4);
//        TableRenderPolicy.Helper.renderRow(row1, row0);
        // 3) 调用TableTools类方法操作表格，比如合并单元格
        // ......
//        TableTools.borderTable(table, 4);
//        TableTools.mergeCellsHorizonal(table, 0, 0, 7);
//        TableTools.mergeCellsVertically(table, 0, 1, 9);
    }

    @Override
    protected void afterRender(RenderContext<T> context) {
        // 清空标签
        clearPlaceholder(context, true);
    }
}
