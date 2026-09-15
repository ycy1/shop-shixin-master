import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.config.ConfigureBuilder;
import com.deepoove.poi.data.*;
import com.deepoove.poi.data.style.Style;
import com.deepoove.poi.plugin.comment.CommentRenderData;
import com.deepoove.poi.plugin.comment.CommentRenderPolicy;
import com.deepoove.poi.plugin.comment.Comments;
import com.deepoove.poi.plugin.highlight.HighlightRenderData;
import com.deepoove.poi.plugin.highlight.HighlightRenderPolicy;
import com.deepoove.poi.plugin.highlight.HighlightStyle;
import com.deepoove.poi.plugin.markdown.MarkdownRenderData;
import com.deepoove.poi.plugin.markdown.MarkdownRenderPolicy;
import com.deepoove.poi.plugin.markdown.MarkdownStyle;
import com.deepoove.poi.policy.AttachmentRenderPolicy;
import com.deepoove.poi.xwpf.XWPFHighlightColor;
import com.shop.config.deepoove.CustomTableRenderPolicy;
import lombok.Data;
import org.apache.poi.util.LocaleUtil;
import org.ddr.poi.html.HtmlRenderPolicy;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xxj
 * @title TestA
 * @date 2026/8/31 16:27
 * @description TODO
 */
public class PoiTlTest {
    public static void main(String[] args) throws Exception {
        // 1. 准备数据
        List<Map<String, Object>> employees = List.of(new HashMap<String, Object>(){{
            put("index", 1);
            put("name", "张三");
            put("employeeId", "10086");
        }}, new HashMap<String, Object>(){{
            put("name", "李四");
            put("employeeId", "10087");
        }});

        Map<String, Object> data = new HashMap<>();
        data.put("name", "张明");
        data.put("employeeId", "10086");
        data.put("position", "高级软件工程师");
//        data.put("signature", "C:\\Users\\Lenovo\\Desktop\\IMG_20260815_174517.jpg");
        data.put("signature", Pictures.ofLocal("C:\\Users\\Lenovo\\Desktop\\IMG_20260815_174517.jpg")
                .size(120, 100) // 尺寸设定
                .create());

        data.put("signatureUrl", Pictures.ofUrl("http://182.92.85.80/group1/M00/00/04/tlxVUGjIIBSAQDYWAAAqUCGcKC072.png")
                .size(120, 100).altMeta("signature") // 尺寸设定
                .create());
        data.put("employees", employees);

        data.put("name", "Sayi");
        data.put("author", Texts.of("Sayi").color("000000").create());
        data.put("anchor", Texts.of("anchortxt").anchor("appendix1").create());
        TextRenderData text = Texts.of("deepoove.com").create();
        Style style = new Style();
        style.setColor("58aadc");
        style.setFontFamily("微软雅黑");
        style.setFontSize(15);
        style.setBold(true);
        style.setHighlightColor(XWPFHighlightColor.GREEN);
        style.setItalic(true);
        text.setStyle(style);
        data.put("link", text);

        // 第0行居中且背景为蓝色的表格
        RowRenderData row0 = Rows.of("姓名", "学历").textColor("FFFFFF")
                .bgColor("4472C4").center().create();
        RowRenderData row1 = Rows.create("李四", "博士");
        data.put("table1", Tables.create(row0, row1));
        data.put("list", Numberings.of(NumberingFormat.LOWER_ROMAN).addItem("张三").addItem("王五").create());


        @Data
        class AddrModel {
            private String addr;
            public AddrModel(String addr) {
                this.addr = addr;
            }
            // Getter/Setter
        }

        List<AddrModel> subData = new ArrayList<>();
        subData.add(new AddrModel("Hangzhou,China"));
        subData.add(new AddrModel("Shanghai,China"));
        data.put("nested", Includes.ofLocal("C:\\Users\\Lenovo\\Desktop\\sub.docx").setRenderModel(subData).create());

        ChartMultiSeriesRenderData chart = Charts
                .ofMultiSeries("ChartTitle", new String[] { "中文", "English" })
                .addSeries("countries", new Double[] { 15.0, 6.0 })
                .addSeries("speakers", new Double[] { 223.0, 119.0 })
                .create();

        data.put("barChart", chart);
        data.put("report", Tables.create(row0, row1,Rows.create("王五", "研究生"), Rows.create("张三", "未知")));

        CommentRenderData comment = Comments.of("鹅")
                .signature("Sayi", "s", LocaleUtil.getLocaleCalendar())
                .comment("鹅，是一种动物")
                .create();
        data.put("comment", comment);

        AttachmentRenderData attach = Attachments.ofLocal("C:\\Users\\Lenovo\\Desktop\\sub.docx", AttachmentType.XLSX).create();
        data.put("attachment", attach);

        HighlightRenderData code = new HighlightRenderData();
        code.setCode("/**\n"
                + " * @author John Smith <john.smith@example.com>\n"
                + "*/\n"
                + "package l2f.gameserver.model;\n"
                + "\n"
                + "public abstract strictfp class L2Char extends L2Object {\n"
                + "  public static final Short ERROR = 0x0001;\n"
                + "\n"
                + "  public void moveTo(int x, int y, int z) {\n"
                + "    _ai = null;\n"
                + "    log(\"Should not be called\");\n"
                + "    if (1 > 5) { // wtf!?\n"
                + "      return;\n"
                + "    }\n"
                + "  }\n"
                + "}");
        code.setLanguage("java");
        code.setStyle(HighlightStyle.builder().withShowLine(true).withTheme("idea").build());
        data.put("code", code);

        MarkdownRenderData codeMd = new MarkdownRenderData();
        codeMd.setMarkdown(new String(Files.readAllBytes(Paths.get("C:\\Users\\Lenovo\\Desktop\\md.md"))));
        codeMd.setStyle(MarkdownStyle.newStyle());
        data.put("md", codeMd);

        data.put("html", "<p><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\">《复仇者联盟4：终局之战》是由</span><span style=\"color: rgb(255, 122, 69); background-color: rgb(255, 255, 255); font-size: 14px;\">安东尼·罗素</span><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\">和乔·罗素联合执导，</span><span style=\"color: rgb(51, 51, 51); background-color: rgb(224, 232, 250); font-size: 14px;\">克里斯托弗·马库斯、斯蒂芬·麦克菲利编剧，小罗伯特·唐尼、克里斯·埃文斯、克里斯·海姆斯沃斯、斯嘉丽·约翰逊、杰瑞米·雷纳、马克·鲁法洛、保罗·路德、布丽·拉尔森、唐·钱德尔</span><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\">、凯伦·吉兰、乔什·布洛林等主演的动作科幻片。该片于2019年4月22日在洛杉矶举行全球首映礼</span><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\"><sup><em> [23]</em></sup></span><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\">，同年4月26日在北美地区公映</span><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\"><sup><em> </em></sup></span><span style=\"color: rgb(51, 51, 51); background-color: rgb(255, 255, 255); font-size: 14px;\">。</span><img src=\"http://182.92.85.80/group1/M00/00/07/tlxVUGqX166AD5sDAALEQqFahY431.webp\" alt=\"null\" data-href=\"null\" style=\"width: 159.80px;height: 119.85px;\"/>\uD83D\uDE29\uD83D\uDE2F\uD83D\uDC4E\uD83D\uDC48</p><p><br></p>");



        // 2. 编译模板并渲染
        ConfigureBuilder builder = Configure.builder();
        builder.useSpringEL(); // 使用SpringEL
        builder.bind("report", new CustomTableRenderPolicy<RowRenderData>());
        builder.bind("comment", new CommentRenderPolicy());
        builder.bind("attachment", new AttachmentRenderPolicy());
        builder.bind("code", new HighlightRenderPolicy());
        builder.bind("md", new MarkdownRenderPolicy());
        builder.bind("html", new HtmlRenderPolicy());
        XWPFTemplate template = XWPFTemplate.compile("C:\\Users\\Lenovo\\Desktop\\template.docx", builder.build()).render(data);


        // 3. 输出文档
        template.writeAndClose(new FileOutputStream("output_text.docx"));
    }
}
