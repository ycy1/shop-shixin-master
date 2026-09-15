package com.shop.utils;

import com.shop.common.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: quequnlong
 * @date: 2024/12/28
 * @description: 邮箱工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailUtil {


    @Value("${mail.smtp.email}")
    private String fromEmail;

    @Value("${mail.smtp.password}")
    private String password;

    @Value("${mail.smtp.port}")
    private int port;

    @Value("${mail.smtp.host}")
    private String host;

    private final RedisUtil redisUtil;

    private final JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();


    public void getJavaMailSenderImpl(){
        javaMailSender.setHost(host);
        javaMailSender.setUsername(fromEmail);
        javaMailSender.setPassword(password);
        javaMailSender.setPort(port);
        javaMailSender.setDefaultEncoding("UTF-8");
        Properties p = new Properties();
        p.setProperty("mail.smtp.auth", "true");
        p.setProperty("mail.smtp.ssl.enable", "true"); // 启用SSL
        p.setProperty("mail.debug", "true");
        javaMailSender.setJavaMailProperties(p);
    }

    /**
     * 发送验证码
     * @param email
     * @throws MessagingException
     */
    public String sendCode(String email) throws MessagingException {

       // this.getJavaMailSenderImpl();

        int code = (int) ((Math.random() * 9 + 1) * 100000); // 随机生成5位数验证码
        String content = "<html>\n" +
                "\t<body><div id=\"contentDiv\" onmouseover=\"getTop().stopPropagation(event);\" onclick=\"getTop().preSwapLink(event, 'html', 'ZC0004_vDfNJayMtMUuKGIAzzsWvc8');\" style=\"position:relative;font-size:14px;height:auto;padding:15px 15px 10px 15px;z-index:1;zoom:1;line-height:1.7;\" class=\"body\">\n" +
                "  <div id=\"qm_con_body\">\n" +
                "    <div id=\"mailContentContainer\" class=\"qmbox qm_con_body_content qqmail_webmail_only\" style=\"opacity: 1;\">\n" +
                "      <style type=\"text/css\">\n" +
                "        .qmbox h1,.qmbox \t\t\th2,.qmbox \t\t\th3 {\t\t\t\tcolor: #00785a;\t\t\t}\t\t\t.qmbox p {\t\t\t\tpadding: 0;\t\t\t\tmargin: 0;\t\t\t\tcolor: #333;\t\t\t\tfont-size: 16px;\t\t\t}\t\t\t.qmbox hr {\t\t\t\tbackground-color: #d9d9d9;\t\t\t\tborder: none;\t\t\t\theight: 1px;\t\t\t}\t\t\t.qmbox .eo-link {\t\t\t\tcolor: #0576b9;\t\t\t\ttext-decoration: none;\t\t\t\tcursor: pointer;\t\t\t}\t\t\t.qmbox .eo-link:hover {\t\t\t\tcolor: #3498db;\t\t\t}\t\t\t.qmbox .eo-link:hover {\t\t\t\ttext-decoration: underline;\t\t\t}\t\t\t.qmbox .eo-p-link {\t\t\t\tdisplay: block;\t\t\t\tmargin-top: 20px;\t\t\t\tcolor: #009cff;\t\t\t\ttext-decoration: underline;\t\t\t}\t\t\t.qmbox .p-intro {\t\t\t\tpadding: 30px;\t\t\t}\t\t\t.qmbox .p-code {\t\t\t\tpadding: 0 30px 0 30px;\t\t\t}\t\t\t.qmbox .p-news {\t\t\t\tpadding: 0px 30px 30px 30px;\t\t\t}\n" +
                "      </style>\n" +
                "      <div style=\"max-width:800px;padding-bottom:10px;margin:20px auto 0 auto;\">\n" +
                "        <table cellpadding=\"0\" cellspacing=\"0\" style=\"background-color: #fff;border-collapse: collapse; border:1px solid #e5e5e5;box-shadow: 0 10px 15px rgba(0, 0, 0, 0.05);text-align: left;width: 100%;font-size: 14px;border-spacing: 0;\">\n" +
                "          <tbody>\n" +
                "            <tr style=\"background-color: #f8f8f8;\">\n" +
                "              <td>\n" +
                "                <img style=\"padding: 15px 0 15px 30px;width:50px\" src=\"https://foruda.gitee.com/avatar/1739413372327645883/13781_d029020a68_1739413372.png\" />" +
                "                <span>拾壹博客 </span>\n" +
                "              </td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td class=\"p-intro\">\n" +
                "                <h1 style=\"font-size: 26px; font-weight: bold;\">验证您的邮箱地址</h1>\n" +
                "                <p style=\"line-height:1.75em;\">感谢您使用 拾壹博客. </p>\n" +
                "                <p style=\"line-height:1.75em;\">以下是您的邮箱验证码，请将它输入到  <span style=\"color:#409eff;\">拾壹博客</span> 的邮箱验证码输入框中:</p>\n" +
                "              </td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td class=\"p-code\">\n" +
                "                <p style=\"color: #253858;text-align:center;line-height:1.75em;background-color: #f2f2f2;min-width: 200px;margin: 0 auto;font-size: 28px;border-radius: 5px;border: 1px solid #d9d9d9;font-weight: bold;\">"+code+"</p>\n" +
                "              </td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td class=\"p-intro\">\n" +
                "                <p style=\"line-height:1.75em;\">这一封邮件包括一些您的私密信息，请不要回复或转发它，以免带来不必要的信息泄露风险。 </p>\n" +
                "              </td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "              <td class=\"p-intro\">\n" +
                "                <hr>\n" +
                "                <p style=\"text-align: center;line-height:1.75em;\">shiyi - <a href='https://www.shiyit.com' style='text-decoration: none;color:#409eff'>拾壹博客</a></p>\n" +
                "              </td>\n" +
                "            </tr>\n" +
                "          </tbody>\n" +
                "        </table>\n" +
                "      </div>\n" +
                "      <style type=\"text/css\">\n" +
                "        .qmbox style, .qmbox script, .qmbox head, .qmbox link, .qmbox meta {display: none !important;}\n" +
                "      </style>\n" +
                "    </div>\n" +
                "  </div><!-- -->\n" +
                "  <style>\n" +
                "    #mailContentContainer .txt {height:auto;}\n" +
                "  </style>\n" +
                "</div></body>\n" +
                "</html>\n";

        // 创建邮件消息
        this.send(email, "您有一封来自 拾壹博客 的回执！" ,content);
        log.info("邮箱验证码发送成功,邮箱:{},验证码:{}",email,code);

        redisUtil.set(RedisConstants.CAPTCHA_CODE_KEY + email, code +"");
        redisUtil.expire(RedisConstants.CAPTCHA_CODE_KEY + email, RedisConstants.MINUTE_EXPIRE * 2, TimeUnit.SECONDS);
        return String.valueOf(code);
    }


    /**
     * 发送邮件
     * @param email 收件人邮箱
     * @param subject 邮件主题
     * @param template 邮件HTML内容
     * @throws MessagingException 邮件发送异常
     */
    public void send(String email, String subject, String template) {
        try {
            this.getJavaMailSenderImpl();
            //创建一个MINE消息
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper mineHelper = new MimeMessageHelper(mimeMessage, true);
            // 设置邮件主题
            mineHelper.setSubject(subject);
            // 设置邮件发送者
            mineHelper.setFrom(Objects.requireNonNull(javaMailSender.getUsername()));
            // 设置邮件接收者，可以有多个接收者，中间用逗号隔开
            mineHelper.setTo(email);
            // 设置邮件发送日期
            mineHelper.setSentDate(DateUtil.getNowDate());
            // 正则筛选 <img>，下载图片作为附件（带 Content-ID），并把 src 改写为 cid: 引用
            List<MimeBodyPart> imageParts = new ArrayList<>();
            String processedTemplate = collectImagesAsAttachments(template, imageParts);
            // 组装 multipart：HTML 正文 + 图片附件
            MimeMultipart multipart = new MimeMultipart("mixed");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(processedTemplate, "text/html;charset=UTF-8");
            multipart.addBodyPart(htmlPart);
            for (MimeBodyPart imagePart : imageParts) {
                multipart.addBodyPart(imagePart);
            }
            mimeMessage.setContent(multipart);
            // 发送邮件
            javaMailSender.send(mimeMessage);
            log.info("邮件发送成功,邮箱:{},主题:{},图片附件:{}",email,subject,imageParts.size());
        }catch (Exception e) {
            log.error("发送邮件失败,收件人:{},error:{}", email, e.getMessage());
        }

    }

    /**
     * 正则筛选 <img> 标签，下载图片作为附件（带 Content-ID），并把 src 改写为 cid: 引用
     * @param html 原始HTML
     * @param imageParts 收集到的图片附件（MimeBodyPart）
     * @return 替换后的HTML，图片 src 已变为 cid:xxx
     */
    private String collectImagesAsAttachments(String html, List<MimeBodyPart> imageParts) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Pattern pattern = Pattern.compile("<img[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            String imgTag = matcher.group(0);
            String src = matcher.group(1);
            // 已处理过的 cid: 跳过
            if (src.startsWith("cid:")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(imgTag));
                continue;
            }
            String cid = "blog-img-" + System.currentTimeMillis() + "-" + (index + 1);
            String fileName = "image-" + (index + 1) + guessImageExt(src);
            index++;
            try {
                ImageData imageData = downloadImage(src);
                if (imageData.data.length == 0) {
                    throw new IOException("下载图片内容为空: " + src);
                }
                String contentType = imageData.contentType != null ? imageData.contentType : guessImageContentType(src);
                MimeBodyPart imagePart = new MimeBodyPart();
                imagePart.setDataHandler(new DataHandler(new ByteArrayDataSource(imageData.data, contentType)));
                imagePart.setFileName(fileName);
                imagePart.setContentID("<" + cid + ">");
                imagePart.setDisposition(Part.ATTACHMENT);
                imageParts.add(imagePart);
                // 将 img 的 src 替换为 cid: 方式
                String replaced = imgTag.replaceFirst("(?i)src\\s*=\\s*[\"'][^\"']*[\"']", "src=\"cid:" + cid + "\"");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replaced));
            } catch (Exception e) {
                log.warn("图片附件失败，保留原图: {}", src);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(imgTag));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 下载远程图片字节及 Content-Type
     */
    private ImageData downloadImage(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        try {
            ImageData imageData = new ImageData();
            imageData.contentType = conn.getContentType();
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                imageData.data = out.toByteArray();
            }
            return imageData;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 下载结果
     */
    private static class ImageData {
        byte[] data;
        String contentType;
    }

    /**
     * 根据图片地址后缀猜测 Content-Type
     */
    private String guessImageContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".svg")) {
            return "image/svg+xml";
        }
        return "image/png";
    }

    /**
     * 根据图片地址后缀猜测文件名扩展名
     */
    private String guessImageExt(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return ".jpg";
        }
        if (lower.contains(".gif")) {
            return ".gif";
        }
        if (lower.contains(".webp")) {
            return ".webp";
        }
        if (lower.contains(".svg")) {
            return ".svg";
        }
        return ".png";
    }

    /**
     * 发送带附件的邮件（支持自定义附件名称）
     * @param email 收件人邮箱
     * @param subject 邮件主题
     * @param template 邮件HTML内容
     * @param attachmentMap 附件映射,key为自定义显示名称,value为文件对象
     * @throws MessagingException 邮件发送异常
     */
    public void sendWithAttachments(String email, String subject, String template,
                                    java.util.Map<String, File> attachmentMap) {
        try {
            this.getJavaMailSenderImpl();
            //创建一个MIME消息
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper mimeHelper = new MimeMessageHelper(mimeMessage, true);
            // 设置邮件主题
            mimeHelper.setSubject(subject);
            // 设置邮件发送者
            mimeHelper.setFrom(Objects.requireNonNull(javaMailSender.getUsername()));
            // 设置邮件接收者
            mimeHelper.setTo(email);
            // 设置邮件发送日期
            mimeHelper.setSentDate(DateUtil.getNowDate());
            // 设置邮件的正文（HTML格式）
            mimeHelper.setText(template, true);

            // 添加附件
            if (attachmentMap != null && !attachmentMap.isEmpty()) {
                for (java.util.Map.Entry<String, File> entry : attachmentMap.entrySet()) {
                    File file = entry.getValue();
                    String attachmentName = entry.getKey();

                    if (file != null && file.exists()) {
                        FileSystemResource fileResource = new FileSystemResource(file);
                        // 使用自定义的附件名称
                        mimeHelper.addAttachment(attachmentName, fileResource);
                        log.info("添加附件: {} (原始文件名: {}), 大小: {} bytes",
                                attachmentName, file.getName(), file.length());
                    } else {
                        log.warn("附件不存在或为null: {}", file != null ? file.getAbsolutePath() : "null");
                    }
                }
            }

            // 发送邮件
            javaMailSender.send(mimeMessage);
            log.info("带附件邮件发送成功,收件人:{}, 附件数量:{}", email, attachmentMap != null ? attachmentMap.size() : 0);
        } catch (MessagingException e) {
            log.error("发送带附件邮件失败,收件人:{},error:{}", email, e.getMessage());
        }
    }


}
