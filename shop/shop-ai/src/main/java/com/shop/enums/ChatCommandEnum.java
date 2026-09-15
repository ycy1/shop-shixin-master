package com.shop.enums;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天指令枚举
 */
public enum ChatCommandEnum {

    CLEAR("/clear", "清除上下文"),
    EXPORT("/export", "导出对话"),
    INIT("/init", "重新加载窗口"),

    EXPORT_N("/export -n? (\\d+)|/export -(\\d+)", "导出最近N条数据", true);

    private final String pattern;
    private final String description;
    private final boolean hasParam;

    ChatCommandEnum(String pattern, String description) {
        this(pattern, description, false);
    }

    ChatCommandEnum(String pattern, String description, boolean hasParam) {
        this.pattern = pattern;
        this.description = description;
        this.hasParam = hasParam;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasParam() {
        return hasParam;
    }

    /**
     * 判断文本是否为指令
     */
    public static boolean isCommand(String text) {
        return text != null && text.trim().startsWith("/");
    }

    /**
     * 解析指令
     */
    public static ParseResult parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String trimmed = text.trim();

        for (ChatCommandEnum cmd : values()) {
            Pattern p = Pattern.compile("^" + cmd.pattern + "$", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(trimmed);
            if (m.matches()) {
                String param = null;
                for (int g = 1; g <= m.groupCount(); g++) {
                    if (m.group(g) != null) {
                        param = m.group(g);
                        break;
                    }
                }
                return new ParseResult(cmd, param);
            }
        }
        return null;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ParseResult {
        private ChatCommandEnum command;
        private String param;
    }
}
