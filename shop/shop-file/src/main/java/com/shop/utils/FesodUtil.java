package com.shop.utils;

import com.shop.config.fesod.CustomConverter;
import com.shop.config.fesod.CustomReadDataListener;
import lombok.Data;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.annotation.ExcelIgnore;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.format.DateTimeFormat;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;
import org.apache.fesod.sheet.annotation.write.style.ContentRowHeight;

import java.io.File;
import java.util.*;


/**
 * @author xxj
 * @title FesodUtil
 * @date 2026/8/26 10:40
 * @description TODO
 */
public class FesodUtil {

    // 示例数据类
    @Data
    @ContentRowHeight(100)
    public static class DemoData {
        private static String readKey = "readKey";

        @ExcelProperty(value = "字符串标题", converter = CustomConverter.StringConverter.class)
        @ColumnWidth(18)  // 宽度设为20个字符
        @ExcelIgnore
        private String string;

        @ExcelProperty(value = "日期标题")
        @DateTimeFormat("yyyy-MM-dd")
        @ExcelIgnore
        @ColumnWidth(20)  // 宽度设为20个字符
        private Date date;

        @ExcelProperty(value = "数字", converter = CustomConverter.DoubleConverter.class)
        @ColumnWidth(10)  // 宽度设为20个字符
//        @ExcelIgnore
        private Double doubleData;

        @ExcelProperty(value = "long数字", converter = CustomConverter.LongConverter.class)
        @ColumnWidth(15)  // 宽度设为20个字符
//        @ExcelIgnore
        private Long longData;

        @ExcelProperty(value = "部门", converter = CustomConverter.ListConverter.class)
//        @DictFormat(isList = true)
        @ColumnWidth(25)
        private List<String> listData;

        @ExcelProperty(value = "图片")
        @ColumnWidth(25)  // 宽度设为20个字符
        @ExcelIgnore
        private File image;

        @ExcelIgnore
        private String ignore;
    }

    // 填充要写入的数据
    private static List<DemoData> data() {
        List<DemoData> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DemoData data = new DemoData();
            data.setString("字符串" + i);
            data.setDate(new Date());
            data.setDoubleData(0.56);
            data.setLongData(i + 1L);
            data.setImage(new File("C:\\Users\\Lenovo\\Desktop\\IMG_20260625_195948.jpg"));
            data.setListData(Arrays.asList("1", "2", "3"));
            list.add(data);
        }
        return list;
    }

    public static void main(String[] args) {
//        String fileName = "demo.xlsx";
        // 创建一个名为“模板”的 sheet 页，并写入数据
//        FesodSheet.write(fileName, DemoData.class)
//                .registerWriteHandler(new CustomCellStyleWriteHandler())
//                .sheet("模板").doWrite(data());

        String fileName = "demo.xlsx";
        CustomReadDataListener<DemoData> demoDataListener = null;
        demoDataListener = new CustomReadDataListener<>("demo");

        FesodSheet.read(fileName, DemoData.class, demoDataListener).sheet().doRead();
        System.out.println(demoDataListener.getDatas("demo"));
//
//        System.out.println("demo2数据");
//
//        fileName = "demo2.xlsx";
//        demoDataListener.setKey("demo2");
//        FesodSheet.read(fileName, DemoData.class, demoDataListener).sheet().doRead();
//        System.out.println(demoDataListener.getDatas("demo2", false));
//
//        System.out.println("\n"+demoDataListener.getFieldClassMap());

    }
}
