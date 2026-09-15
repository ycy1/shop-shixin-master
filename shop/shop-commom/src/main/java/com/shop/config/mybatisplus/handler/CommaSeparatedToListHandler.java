package com.shop.config.mybatisplus.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.util.StringUtils;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 逗号分隔字符串转 List<T> 的通用处理器
 *
 * 使用方式：
 * - 实体类字段：@TableField(typeHandler = CommaSeparatedToListHandler.LongList.class)
 * - XML resultMap：typeHandler="com.example.handler.CommaSeparatedToListHandler$LongList"
 */
public abstract class CommaSeparatedToListHandler<T> extends BaseTypeHandler<List<T>> {

    private static final String DEFAULT_DELIMITER = ",";
    private final String delimiter;
    private final Class<T> elementType;

    protected CommaSeparatedToListHandler(Class<T> elementType) {
        this(elementType, DEFAULT_DELIMITER);
    }

    protected CommaSeparatedToListHandler(Class<T> elementType, String delimiter) {
        this.elementType = elementType;
        this.delimiter = delimiter;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<T> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setString(i, null);
            return;
        }
        String joined = parameter.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(delimiter));
        ps.setString(i, joined);
    }

    @Override
    public List<T> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseToList(value);
    }

    @Override
    public List<T> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseToList(value);
    }

    @Override
    public List<T> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseToList(value);
    }

    private List<T> parseToList(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        String[] parts = value.split(delimiter);
        List<T> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.hasText(trimmed)) {
                try {
                    result.add(convertToT(trimmed));
                } catch (Exception ignored) {
                    // 转换失败忽略该项
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private T convertToT(String value) {
        if (elementType == Long.class) {
            return (T) Long.valueOf(value);
        } else if (elementType == Integer.class) {
            return (T) Integer.valueOf(value);
        } else if (elementType == String.class) {
            return (T) value;
        } else if (elementType == Double.class) {
            return (T) Double.valueOf(value);
        } else if (elementType == Short.class) {
            return (T) Short.valueOf(value);
        } else if (elementType == Byte.class) {
            return (T) Byte.valueOf(value);
        } else if (elementType == Float.class) {
            return (T) Float.valueOf(value);
        } else if (elementType == Boolean.class) {
            return (T) Boolean.valueOf(value);
        } else {
            throw new IllegalArgumentException("不支持的类型: " + elementType);
        }
    }

    // ==================== 内部子类 ====================

    /**
     * 逗号分隔字符串 → List&lt;Long&gt;
     * XML引用: com.example.handler.CommaSeparatedToListHandler$LongList
     */
    public static class LongList extends CommaSeparatedToListHandler<Long> {
        public LongList() {
            super(Long.class);
        }
    }

    /**
     * 逗号分隔字符串 → List&lt;String&gt;
     */
    public static class StringList extends CommaSeparatedToListHandler<String> {
        public StringList() {
            super(String.class);
        }
    }

    /**
     * 逗号分隔字符串 → List&lt;Integer&gt;
     */
    public static class IntegerList extends CommaSeparatedToListHandler<Integer> {
        public IntegerList() {
            super(Integer.class);
        }
    }

    /**
     * 逗号分隔字符串 → List&lt;Double&gt;
     */
    public static class DoubleList extends CommaSeparatedToListHandler<Double> {
        public DoubleList() {
            super(Double.class);
        }
    }

    /**
     * 逗号分隔字符串 → List&lt;Boolean&gt;
     * 支持: 1/0, true/false, yes/no
     */
    public static class BooleanList extends CommaSeparatedToListHandler<Boolean> {
        public BooleanList() {
            super(Boolean.class);
        }
    }
}