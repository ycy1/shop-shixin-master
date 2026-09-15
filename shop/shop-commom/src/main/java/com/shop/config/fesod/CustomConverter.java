package com.shop.config.fesod;

import com.shop.annotation.DictFormat;
import com.shop.entity.SysDept;
import com.shop.entity.SysDictData;
import com.shop.mapper.SysDeptMapper;
import com.shop.service.SysDictDataCommService;
import com.shop.utils.ConvertUtil;
import com.shop.utils.SpringUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.converters.WriteConverterContext;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.metadata.GlobalConfiguration;
import org.apache.fesod.sheet.metadata.data.ReadCellData;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.metadata.property.ExcelContentProperty;
import org.apache.fesod.sheet.util.DateUtils;
import org.apache.fesod.sheet.util.NumberUtils;
import org.apache.fesod.sheet.util.WorkBookUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author xxj
 * @title CustomStringStringConverter
 * @date 2026/8/26 11:19
 * @description 自定义转换器
 * 导入读取数据时 按表头名称匹配（确保表头与实体类属性名一致）
 */
//@Component
//@AllArgsConstructor
public class CustomConverter<T> implements Converter<T> {

    private final Class<T> classType;

    public CustomConverter(Class<T> elementType) {
        this.classType = elementType;
    }
    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public T convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        return readConvertToT(this.classType, cellData, contentProperty);
    }

    @SuppressWarnings("unchecked")
    protected T readConvertToT(Class<T> classType,  ReadCellData<?>  context, ExcelContentProperty contentProperty){
        if (classType == String.class){
            return (T) (context.getStringValue());
        } else if (classType == Long.class) {
            return (T) Long.valueOf(context.getNumberValue().longValue());
        } else if (classType == Integer.class) {
            return (T) Integer.valueOf(context.getNumberValue().intValue());
        }else if (classType == BigInteger.class) {
            return (T) context.getNumberValue().toBigInteger();
        } else if (classType == Double.class) {
            return (T) Double.valueOf(context.getNumberValue().doubleValue());
        } else if (classType == Short.class) {
            return (T) Short.valueOf(context.getNumberValue().shortValue());
        } else if (classType == Byte.class) {
            return (T) Byte.valueOf(context.getNumberValue().byteValue());
        } else if (classType == Float.class) {
            return (T) Float.valueOf(context.getNumberValue().floatValue());
        } else if (classType == Boolean.class) {
            return (T) context.getBooleanValue();
        } else if (classType == Date.class) {
            return (T) DateUtils.getJavaDate(context.getNumberValue().doubleValue(), false);
        } else if (classType == List.class){
            List collect = Arrays.stream(context.getStringValue().split( ",")).toList();
            return (T) collect;
        }
        else {
            throw new IllegalArgumentException("不支持的类型: " + classType);
        }
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<T> context) throws Exception {
        return writeConvertToT(context);
    }

    protected WriteCellData<?> writeConvertToT(WriteConverterContext<T> context) throws Exception {
        if (classType == String.class){
            return new WriteCellData<>((String) context.getValue());
        } else if (classType == Integer.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == Long.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == Double.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == Short.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == Byte.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == Float.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == BigInteger.class) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        } else if (classType == Boolean.class) {
            return new WriteCellData<>(context.getValue().toString());
        } else if (classType == Date.class) {
            WriteCellData<?> cellData = new WriteCellData<>((Date) context.getValue());
            WorkBookUtil.fillDataFormat(cellData, DateUtils.DATE_FORMAT_19, DateUtils.defaultDateFormat);
            return cellData;
        }else if (classType == List.class){
            String join = String.join(",", ((List) context.getValue()).stream().map(Object::toString).toList());
            return new WriteCellData<>(join);
        } else if (classType == Object.class){
            return new WriteCellData<>(context.getValue().toString());
        }else {
            return new WriteCellData<>(CellDataTypeEnum.ERROR, context.getValue().toString());
//            throw new IllegalArgumentException("不支持的类型: " + classType);
        }
    }


    /**
     * 转换器子类，用于指定转换的类类型
     * 可参照上面的转换器类实现
     * readConvertToT 读取的数据转换成指定类型
     * writeConvertToT 写入的数据转换成指定内容
     */

    // Long 转换器
    public static class LongConverter extends CustomConverter<Long> {
        public LongConverter() {super(Long.class);}

        @Override
        public Long readConvertToT(Class<Long> classType,ReadCellData<?> cellData, ExcelContentProperty contentProperty) {
            return cellData.getNumberValue().longValue();
        }

        @Override
        public WriteCellData<?> writeConvertToT(WriteConverterContext<Long> context) {
            return NumberUtils.formatToCellData((Number)context.getValue(), context.getContentProperty());
        }


    }

    // String 转换器
    public static class StringConverter extends CustomConverter<String> {
        public StringConverter() {super(String.class);}

        @Override
        public String readConvertToT(Class<String> classType,ReadCellData<?> cellData, ExcelContentProperty contentProperty) {
            return cellData.getStringValue();
        }
    }

    // List 转换器
    public static class ListConverter extends CustomConverter<List> {


        public ListConverter() {
            super(List.class);
        }

        @Override
        public List readConvertToT(Class<List> classType,ReadCellData<?> cellData, ExcelContentProperty contentProperty) {
            return Arrays.stream(cellData.getStringValue().split( ",")).toList();
        }
        @Override
        public WriteCellData<?> writeConvertToT(WriteConverterContext<List> context) {
            List value = context.getValue();

            DictFormat dictFormat = context.getContentProperty().getField().getAnnotation(DictFormat.class);
            if (!value.isEmpty() && dictFormat != null && dictFormat.dbClazz()!=null && dictFormat.dbClazz()!= Void.class) {
                if(dictFormat.dbClazz() == SysDept.class){
                    SysDeptMapper deptMapper = SpringUtil.getBean(SysDeptMapper.class);
                    List<SysDept> sysDepts = deptMapper.selectBatchIds(value);
                    return new WriteCellData<>(String.join(",",sysDepts.stream().map(SysDept::getName).toList()));
                }
            }
            return new WriteCellData<>(String.join(",", context.getValue().stream().map(Object::toString).toList()));
        }
    }

    // Double 转换器
    public static class DoubleConverter extends CustomConverter<Double> {
        public DoubleConverter() {super(Double.class);}
    }

    // Date 转换器
    public static class DateConverter extends CustomConverter<Date> {
        public DateConverter() {super(Date.class);}
    }

    // DicToInt 转换器
    public static class DicConverterToInt extends CustomConverter<Integer> {

        public DicConverterToInt() {
            super(Integer.class);
        }

        @Override
        public Integer readConvertToT(Class<Integer> classType,ReadCellData<?> cellData, ExcelContentProperty contentProperty) {
            String cellValue = cellData.getStringValue();
            DictFormat dictFormat = contentProperty.getField().getAnnotation(DictFormat.class);
            // 字典code
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.code())) {
                String dictType = dictFormat.code();
                // 通过字典服务，根据类型和文本获取编码
                SysDictDataCommService dictService = SpringUtil.getBean(SysDictDataCommService.class);
                SysDictData sysDictData = dictService.selectDataByDictTypeAndDictLabelCache(dictType, cellValue);
                if (sysDictData != null) {
                    return Integer.valueOf(sysDictData.getValue());
                }
            }

            // 自定义data
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.data())){
                String dictData = dictFormat.data();
                Map<Integer, String> map = ConvertUtil.StrToMap(dictData, Integer.class);
                for (Integer key : map.keySet()){
                    if (cellValue.equals(map.get(key))){
                        return key;
                    }
                }
            }
            // 枚举字典
            if (dictFormat != null && dictFormat.enumType() != DictFormat.None.class){
                Enum<?>[] enumConstants = dictFormat.enumType().getEnumConstants();
                for (Enum<?> enumConstant : enumConstants){
                    try {
                        Object lable = enumConstant.getClass().getMethod("getLable").invoke(enumConstant);
                        Object value = enumConstant.getClass().getMethod("getValue").invoke(enumConstant);
                        if (cellValue.equals(lable)){
                            return (Integer) value;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }

            return super.readConvertToT(classType, cellData, contentProperty);
        }

        @Override
        public WriteCellData<?> writeConvertToT(WriteConverterContext<Integer> context) throws Exception {
            Integer rowValue = context.getValue();
            DictFormat dictFormat = context.getContentProperty().getField().getAnnotation(DictFormat.class);
            // 字典code
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.code())) {
                String dictType = dictFormat.code();
                // 通过字典服务，根据类型和文本获取编码
                SysDictDataCommService dictService = SpringUtil.getBean(SysDictDataCommService.class);
                SysDictData sysDictData = dictService.selectDataByDictTypeAndDictValueCache(dictType, rowValue.toString());
                if (sysDictData != null) {
                    return new WriteCellData<>(sysDictData.getLabel());
                }
            }
            // 自定义data
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.data())){
                String dictData = dictFormat.data();
                Map<Integer, String> map = ConvertUtil.StrToMap(dictData, Integer.class);
                for (Integer key : map.keySet()){
                    if (rowValue.equals(key)){
                        return new WriteCellData<>(map.get(key));
                    }
                }
            }
            // 枚举字典
            if (dictFormat != null && dictFormat.enumType() != DictFormat.None.class){
                Enum<?>[] enumConstants = dictFormat.enumType().getEnumConstants();
                for (Enum<?> enumConstant : enumConstants){
                    try {
                        Object lable = enumConstant.getClass().getMethod("getLable").invoke(enumConstant);
                        Object value = enumConstant.getClass().getMethod("getValue").invoke(enumConstant);
                        if (rowValue.equals(value)){
                            return new WriteCellData<>(lable.toString());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
            return super.writeConvertToT(context);
        }
    }

    // DicToStr 转换器
    public static class DicConverterToStr extends CustomConverter<String> {

        public DicConverterToStr() {
            super(String.class);
        }

        @Override
        public String readConvertToT(Class<String> classType,ReadCellData<?> cellData, ExcelContentProperty contentProperty) {
            String cellValue = cellData.getStringValue();
            DictFormat dictFormat = contentProperty.getField().getAnnotation(DictFormat.class);
            // 字典code
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.code())) {
                String dictType = dictFormat.code();
                // 通过字典服务，根据类型和文本获取编码
                SysDictDataCommService dictService = SpringUtil.getBean(SysDictDataCommService.class);
                SysDictData sysDictData = dictService.selectDataByDictTypeAndDictLabelCache(dictType, cellValue);
                if (sysDictData != null) {
                    return sysDictData.getValue();
                }
            }

            // 自定义data
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.data())){
                String dictData = dictFormat.data();
                Map<String, String> map = ConvertUtil.StrToMap(dictData, String.class);
                for (String key : map.keySet()){
                    if (cellValue.equals(map.get(key))){
                        return key;
                    }
                }
            }

            // 枚举字典
            if (dictFormat != null && dictFormat.enumType() != DictFormat.None.class){
                Enum<?>[] enumConstants = dictFormat.enumType().getEnumConstants();
                for (Enum<?> enumConstant : enumConstants){
                    try {
                        Object lable = enumConstant.getClass().getMethod("getLable").invoke(enumConstant);
                        Object value = enumConstant.getClass().getMethod("getValue").invoke(enumConstant);
                        if (cellValue.equals(lable)){
                            return value.toString();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }

            return super.readConvertToT(classType, cellData, contentProperty);
        }

        @Override
        public WriteCellData<?> writeConvertToT(WriteConverterContext<String> context) throws Exception {
            String rowValue = context.getValue();
            DictFormat dictFormat = context.getContentProperty().getField().getAnnotation(DictFormat.class);
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.code())) {
                String dictType = dictFormat.code();
                // 通过字典服务，根据类型和文本获取编码
                SysDictDataCommService dictService = SpringUtil.getBean(SysDictDataCommService.class);
                SysDictData sysDictData = dictService.selectDataByDictTypeAndDictValueCache(dictType, rowValue);
                if (sysDictData != null) {
                    return new WriteCellData<>(sysDictData.getLabel());
                }
            }

            // 自定义data
            if (dictFormat != null && !StringUtils.isEmpty(dictFormat.data())){
                String dictData = dictFormat.data();
                Map<String, String> map = ConvertUtil.StrToMap(dictData, String.class);
                for (String key : map.keySet()){
                    if (rowValue.equals(key)){
                        return new WriteCellData<>(map.get(key));
                    }
                }
            }

            // 枚举字典
            if (dictFormat != null && dictFormat.enumType() != DictFormat.None.class){
                Enum<?>[] enumConstants = dictFormat.enumType().getEnumConstants();
                for (Enum<?> enumConstant : enumConstants){
                    try {
                        Object lable = enumConstant.getClass().getMethod("getLable").invoke(enumConstant);
                        Object value = enumConstant.getClass().getMethod("getValue").invoke(enumConstant);
                        if (value.toString().equals(rowValue)){
                            return new WriteCellData<>(lable.toString());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return super.writeConvertToT(context);
        }
    }
}
