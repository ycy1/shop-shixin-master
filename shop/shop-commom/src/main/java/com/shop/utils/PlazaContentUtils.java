package com.shop.utils;

import cn.hutool.http.HtmlUtil;
import com.shop.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 广场正文那一把尺（前台发布/修改和后台修改共用，全站只这一份）
 *
 * 抽出来只有一个原因：判据住在 shop-api 那个 service 的私有方法里，后台这一屏也要改正文了，
 * 两处各写一遍迟早对不上 ——「前台发不出去的内容，后台却改得进去」就是脏数据进库的那道口子。
 *
 * 只管正文这一格、由它算出来的摘要 content_text，和广场消息图那一串地址：
 * 关联对象那一路的校验要查四张业务表，留在写入方各自的 service 里，这里不掺。
 */
public final class PlazaContentUtils {

    /** 正文里「人打的字」最多多少字：列是 mediumtext，上限收在这一层，编辑器的计数按这个数走（剥掉标签后量） */
    private static final int MAX_TEXT_LENGTH = 2000;

    /** 整段 HTML 最长多少字：只限文字限不住一堆 &lt;p&gt; 套出来的长串，所以正文本身也要有一道闸 */
    private static final int MAX_HTML_LENGTH = 20000;

    /** 标题限长，和列宽 varchar(200) 对齐 */
    private static final int MAX_TITLE_LENGTH = 200;

    /** 摘要落到 content_text（text 列）上，截到这一格为止 —— 卡片上只显示两三行，没必要把整篇存两份 */
    private static final int MAX_SUMMARY_LENGTH = 500;

    /** 广场消息图最多几张（三端上传控件的上限也跟着这个数走） */
    private static final int MAX_IMAGE_COUNT = 9;

    /** 单个地址限长：上传接口回来的绝对地址远不到这一格，收它是为了拦住「往这一格里灌别的东西」 */
    private static final int MAX_IMAGE_URL_LENGTH = 255;

    /** 整串（若干地址用逗号拼起来）限长，和列宽 varchar(2000) 对齐：9 张 × 255 正好越界，所以列宽这道先拒 */
    private static final int MAX_IMAGES_LENGTH = 2000;

    private PlazaContentUtils() {
    }

    /**
     * 正文三道理：不是空、文字限长、整段 HTML 限长。
     * 量的是「人打的字」：先剥掉标签再数，否则编辑器套出来的一层层 &lt;p&gt; 会把额度吃光。
     *
     * @return 剥掉标签后的纯文本，拿它交给 {@link #summary} 算 content_text 那一格摘要
     */
    public static String assertContent(String content) {
        String text = HtmlUtil.cleanHtmlTag(content).trim();
        if (text.isEmpty() && !content.toLowerCase().contains("<img")) {
            // 只剥标签数出来的 0 字有一种是真空内容（编辑器清空后剩下 <p><br></p>），
            // 有一种是纯图没字的一条 —— 后者放行，前者拒掉
            throw new ServiceException("消息内容不能为空");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ServiceException("消息内容不能超过 " + MAX_TEXT_LENGTH + " 字");
        }
        if (content.length() > MAX_HTML_LENGTH) {
            throw new ServiceException("消息内容太长了，请分条发布");
        }
        return text;
    }

    /** 标题可空（全空就是没标题，落 NULL），填了就限长，和列宽 varchar(200) 对齐 */
    public static String assertTitle(String rawTitle) {
        String title = StringUtils.trimToNull(rawTitle);
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new ServiceException("标题不能超过 " + MAX_TITLE_LENGTH + " 字");
        }
        return title;
    }

    /**
     * 广场消息图：可空（不传或全是空串 ⇒ 落 NULL，「不传即清空」），填了收三道理 —— 张数、单个地址长度、整串长度。
     *
     * 这一格存的是发布人自己上传之后由 /api/file/uploadImage 回来的地址，多张用英文逗号拼在同一列里，
     * 所以返回的是「剥掉空白、丢掉空段之后重新拼出来的那一串」：前端多带一个尾逗号、地址两边多打了空格都不影响落库形状，
     * 读侧（卡片取首图、详情页整组铺开）拆出来的才是一致的一组地址。服务端仍然不从正文里抓图来补这一格。
     */
    public static String assertPlazaImages(String rawImages) {
        if (StringUtils.isBlank(rawImages)) {
            return null;
        }
        List<String> urls = new ArrayList<>();
        for (String part : rawImages.split(",")) {
            String url = StringUtils.trimToNull(part);
            if (url == null) {
                continue;
            }
            if (url.length() > MAX_IMAGE_URL_LENGTH) {
                throw new ServiceException("图片地址太长了");
            }
            urls.add(url);
        }
        if (urls.isEmpty()) {
            return null;
        }
        if (urls.size() > MAX_IMAGE_COUNT) {
            throw new ServiceException("最多上传 " + MAX_IMAGE_COUNT + " 张图");
        }
        String joined = String.join(",", urls);
        if (joined.length() > MAX_IMAGES_LENGTH) {
            throw new ServiceException("图片太多了，请减少张数");
        }
        return joined;
    }

    /** 摘要：剥标签后的纯文本取前 MAX_SUMMARY_LENGTH 字 */
    public static String summary(String text) {
        return StringUtils.substring(text, 0, MAX_SUMMARY_LENGTH);
    }
}
