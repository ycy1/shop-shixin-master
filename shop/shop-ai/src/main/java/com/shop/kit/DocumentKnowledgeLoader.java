package com.shop.kit;

import cn.hutool.core.io.FileUtil;
import com.shop.entity.ConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档知识加载器：根据文件后缀选择对应解析器读取文档内容
 * @date 2026/7/17
 */
@Slf4j
@Component
public class DocumentKnowledgeLoader {

    private static final int MAX_CONTENT_LENGTH = 100_000;

    /**
     * 文本类文件后缀
     */
    private static final java.util.Set<String> TEXT_EXTENSIONS = java.util.Set.of(
            "txt", "md", "json", "xml", "yaml", "yml", "properties", "csv",
            "sql", "java", "py", "js", "ts", "html", "css", "scss", "less",
            "sh", "bat", "cmd", "log", "ini", "cfg", "conf",
            "c", "cpp", "h", "hpp", "go", "rs", "rb", "php", "kt", "swift",
            "doc", "docx", "pdf", "xls", "xlsx"
    );

    public String load(ConfigSource config) {
        String path = config.getDocPath();
        if (path == null || path.isEmpty()) {
            return "文档路径未配置";
        }

        try {
            String content;
            if (path.startsWith("http://") || path.startsWith("https://")) {
                content = loadFromUrl(path);
            } else {
                content = loadFromLocal(path);
            }

            if (content == null) return "不支持的文件类型或读取失败";

            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH)
                        + "\n\n...(文档过长已截断，仅显示前" + MAX_CONTENT_LENGTH + "字符)";
            }

            log.info("文档知识加载完成: path={}, length={}", path, content.length());
            return "文档内容：\n\n" + content;
        } catch (Exception e) {
            log.error("文档知识加载失败: {}", path, e);
            return "文档加载失败：" + e.getMessage();
        }
    }

    private String loadFromLocal(String filePath) throws Exception {
        String ext = extension(filePath).toLowerCase();

        if ("pdf".equals(ext)) {
            return readPdf(filePath);
        }
        if ("doc".equals(ext) || "docx".equals(ext)) {
            return readWord(filePath);
        }
        if ("xls".equals(ext) || "xlsx".equals(ext)) {
            return readExcel(filePath);
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return readTextFile(filePath);
        }

        // 未知类型，尝试按文本读取
        log.warn("未知文件类型 .{}，尝试按文本读取: {}", ext, filePath);
        return readTextFile(filePath);
    }

    private String loadFromUrl(String urlStr) throws Exception {
        String ext = extension(urlStr).toLowerCase();

        // 下载到临时文件
        java.nio.file.Path tempFile = Files.createTempFile("knowledge_", "." + ext);
        try (InputStream is = new URL(urlStr).openStream()) {
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // 与本地文件使用相同的解析逻辑
        String result = loadFromLocal(tempFile.toString());

        Files.deleteIfExists(tempFile);
        return result;
    }

    /**
     * 读取文本文件（自动检测编码）
     */
    private String readTextFile(String filePath) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        String[] charsets = {"UTF-8", "GBK", "GB2312", "ISO-8859-1"};
        for (String cs : charsets) {
            String result = new String(bytes, Charset.forName(cs));
            if (result.indexOf('�') < 0) {
                return result;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 读取 PDF 文件（使用 Apache PDFBox）
     */
    private String readPdf(String filePath) {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(new java.io.File(filePath))) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            log.error("PDF 读取失败: {}", filePath, e);
            return "[PDF 读取失败：" + e.getMessage() + "]";
        }
    }

    /**
     * 读取 Excel 文件（.xls / .xlsx）
     */
    private String readExcel(String filePath) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            boolean isXlsx = filePath.toLowerCase().endsWith(".xlsx");
            if (isXlsx) {
                var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(is);
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    var sheet = wb.getSheetAt(i);
                    sb.append("\n===== 工作表：").append(sheet.getSheetName()).append(" =====\n");
                    for (var row : sheet) {
                        for (var cell : row) {
                            String val;
                            switch (cell.getCellType()) {
                                case STRING -> val = cell.getStringCellValue();
                                case NUMERIC -> val = String.valueOf(cell.getNumericCellValue());
                                case BOOLEAN -> val = String.valueOf(cell.getBooleanCellValue());
                                case FORMULA -> val = cell.getCellFormula();
                                default -> val = "";
                            }
                            sb.append(val).append("\t");
                        }
                        sb.append("\n");
                    }
                }
                wb.close();
            } else {
                var wb = new org.apache.poi.hssf.usermodel.HSSFWorkbook(is);
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    var sheet = wb.getSheetAt(i);
                    sb.append("\n===== 工作表：").append(sheet.getSheetName()).append(" =====\n");
                    for (var row : sheet) {
                        for (var cell : row) {
                            String val;
                            switch (cell.getCellType()) {
                                case STRING -> val = cell.getStringCellValue();
                                case NUMERIC -> val = String.valueOf(cell.getNumericCellValue());
                                case BOOLEAN -> val = String.valueOf(cell.getBooleanCellValue());
                                case FORMULA -> val = cell.getCellFormula();
                                default -> val = "";
                            }
                            sb.append(val).append("\t");
                        }
                        sb.append("\n");
                    }
                }
                wb.close();
            }
        } catch (Exception e) {
            log.error("Excel 读取失败: {}", filePath, e);
            return "[Excel 读取失败：" + e.getMessage() + "]";
        }
        return sb.toString().trim();
    }

    /**
     * 读取 Word 文件（.doc / .docx）
     */
    private String readWord(String filePath) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            if (filePath.toLowerCase().endsWith(".docx")) {
                var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(is);

                // 用 bodyElements 按文档顺序读取所有内容（段落+表格混排）
                for (var body : document.getBodyElements()) {
                    if (body instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                        String text = ((org.apache.poi.xwpf.usermodel.XWPFParagraph) body).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            sb.append(text.trim()).append("\n");
                        }
                    } else if (body instanceof org.apache.poi.xwpf.usermodel.XWPFTable) {
                        for (var row : ((org.apache.poi.xwpf.usermodel.XWPFTable) body).getRows()) {
                            for (var cell : row.getTableCells()) {
                                sb.append(cell.getText()).append("\t");
                            }
                            sb.append("\n");
                        }
                    }
                }

                // 读取页眉页脚
                try { for (var h : document.getHeaderList()) { for (var p : h.getParagraphs()) { String t = p.getText(); if (t != null && !t.isEmpty()) sb.append(t).append("\n"); } } } catch (Exception ignored) {}
                try { for (var f : document.getFooterList()) { for (var p : f.getParagraphs()) { String t = p.getText(); if (t != null && !t.isEmpty()) sb.append(t).append("\n"); } } } catch (Exception ignored) {}

                document.close();
            } else {
                // .doc 格式：使用 WordExtractor 提取更完整内容
                try (var extractor = new org.apache.poi.hwpf.extractor.WordExtractor(is)) {
                    String[] paragraphs = extractor.getParagraphText();
                    for (String p : paragraphs) {
                        if (p != null && !p.trim().isEmpty()) {
                            sb.append(p.trim()).append("\n");
                        }
                    }
                    // 额外提取页眉页脚
                    String headerText = extractor.getHeaderText();
                    if (headerText != null && !headerText.isEmpty()) {
                        sb.append(headerText).append("\n");
                    }
                    String footerText = extractor.getFooterText();
                    if (footerText != null && !footerText.isEmpty()) {
                        sb.append(footerText).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Word 文档读取失败: {}", filePath, e);
            return "[Word 文档读取失败：" + e.getMessage() + "]";
        }
        return sb.toString().trim();
    }

    private String extension(String path) {
        int idx = path.lastIndexOf('.');
        if (idx < 0) return "";
        // 处理 URL 中的查询参数，如 file.txt?version=1
        String ext = path.substring(idx + 1);
        int qIdx = ext.indexOf('?');
        return qIdx > 0 ? ext.substring(0, qIdx) : ext;
    }


}
