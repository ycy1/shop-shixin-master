package com.shop.api.service.impl;

import com.shop.api.service.ShopCommentWriteService;
import com.shop.dto.comment.ShopCommentSaveDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopStore;
import com.shop.entity.SysComment;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopPlazaMessageMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.SysCommentMapper;
import com.shop.utils.IpUtil;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.NotificationsUtil;
import com.shop.utils.UserAgentUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前台评论区 写入实现（发表/回复、点赞开关、删自己那一条）
 *
 * 三颗都要求登录：/api/** 不过 Sa-Token 拦截器，所以由这一层自己取登录态，取不到就报「请先登录后再…」；
 * 入参里没有 userId 那一格 —— 能发的、能点的、能删的永远是自己那一格，参数能点名别人就等于替别人发言
 *（口径同本人签到、本人补签、那颗收藏按钮）。
 *
 * 只有「发表」这一颗收 commentType + businessId，而且回复时两格都不认前端传的：
 * 一律跟着被回复那条评论走（否则就能把回复挂到别人家的对象下，把 A 商品的回复串到 B 商品评论区里）。
 * 层级不限：replyToId 给谁就挂在谁下面（回复的回复再回复都行），parentId 就是它、
 * 作者记进 replyUserId（页面上那句「回复 @某人」读的就是它）。
 *
 * 留痕四格（ip / 归属地 / 浏览器 / 系统）全由服务端现取，写法照登录时往 sys_user 上盖那三笔：
 * 前台传什么都不认，认了就是让人随便编。归属地读的是启动时加载进内存那份 xdb，不外呼。
 *
 * 内容两道闸：去掉首尾空白后文字部分不能是空、不能超 500 字。不做敏感词、不做审核 ——
 * 后台那页能删能置顶，先把话放出来再谈管不管得过来。
 * 500 字数的是「人打的字」：正文里 `[图片](https://…)` 那一整串（连语法带地址）不计进去，
 * 否则一条带两三张图就把额度吃光了 —— 地址那一截另有限长和限张那道闸兜着。
 * 表情不另设一列也不转义：页面上那颗表情面板插进正文的就是普通 emoji 字符，跟着 content 一起存
 *（那一格是 utf8mb4，存得住 4 字节字符），长度两边都按 UTF-16 单元数算，一个 emoji 都算 2 字，
 * 不会出现「页面说还剩 10 字、提交被服务端拒」。正文照旧按纯文本画，任何时候不走 v-html。
 *
 * 图片也不另建列：前端把上传回来的地址写成 `[图片](https://…)` 内嵌在 content 里，跟着正文一起存，
 * 三端各自把这串拆开画。图还是「先传后提交」（POST /api/file/uploadImage，这一颗不收文件），
 * 但落库的是正文这一串能被随便编的字符，所以 assertImageMarks 要过两道：
 * 每一串的地址只认 http(s) 开头且不太长（脏一串就当场裂在评论区页面上）、张数 ≤9。
 */
@Service
@RequiredArgsConstructor
public class ShopCommentWriteServiceImpl implements ShopCommentWriteService {

    /** 评论类型的五档，和字典 comment_type、和那张表 CASE 联表的五档是同一套 */
    private static final List<Integer> COMMENT_TYPES =
            Arrays.asList(SysComment.TYPE_PRODUCT, SysComment.TYPE_STORE,
                    SysComment.TYPE_BRAND, SysComment.TYPE_PLAZA, SysComment.TYPE_PARK);

    /** 一条评论最多写多少字：列本身是 mediumtext，上限收在这一层，页面那颗计数按这个数走（数的是文字，图片那一串不计进来） */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 一条评论最多带几张图（同各家评价那九宫格），页面上选图也按这个数限住 */
    private static final int MAX_IMAGE_COUNT = 9;

    /** 单个图片URL最长多少字：存储平台给回的地址几十个字，这一串收的是正文里的字符，不给上限就等于让人往 mediumtext 里灌 */
    private static final int MAX_IMAGE_URL_LENGTH = 500;

    /**
     * 正文里图片那一串的形状：`[图片](https://…)`。三端都按这一串拆开来画，所以它既是判据也是协议，改形状要三端一起改。
     * 地址不含右括号和空白，所以 [^)\s]* 就够 —— 匹配不到的一律当普通文字存着、当普通文字画（半拉子语法不会变成图）。
     */
    private static final Pattern IMAGE_MARK = Pattern.compile("\\[图片\\]\\(([^\\s)]*)\\)");

    private final SysCommentMapper sysCommentMapper;

    private final ShopProductMapper shopProductMapper;

    private final ShopStoreMapper shopStoreMapper;

    private final ShopBrandMapper shopBrandMapper;

    private final ShopPlazaMessageMapper shopPlazaMessageMapper;

    private final ShopTrendyParkMapper shopTrendyParkMapper;

    private final NotificationsUtil notificationsUtil;

    @Override
    public SysComment publish(ShopCommentSaveDto form) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再发表评论");
        if (form == null) {
            throw new ServiceException("请填写评论内容");
        }
        String content = form.getContent() == null ? "" : form.getContent().trim();
        // 闸收的是「人打的字」：先把正文里 [图片](…) 那一整串（连语法带地址）摘掉再量，
        // 不然存储平台那一条七八十个字符的地址，两三张就把 500 字额度占满了
        String text = IMAGE_MARK.matcher(content).replaceAll("");
        if (text.trim().isEmpty()) {
            throw new ServiceException("评论内容不能为空");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new ServiceException("评论内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        assertImageMarks(content);

        SysComment record = new SysComment();
        record.setContent(content);
        record.setUserId(login.getId());

        Integer replyToId = form.getReplyToId();
        if (replyToId != null) {
            SysComment target = sysCommentMapper.selectById(replyToId);
            if (target == null) {
                throw new ServiceException("要回复的评论不存在");
            }
            // 两格跟着被回复那一条走，前端传什么都不认
            record.setCommentType(target.getCommentType());
            record.setBusinessId(target.getBusinessId());
            // 真挂到被回复那一行下面：回复的回复再回复，层数不限，页面上靠缩进看出谁对着谁说
            record.setParentId(target.getId());
            record.setReplyUserId(target.getUserId());
        } else {
            Integer commentType = form.getCommentType();
            if (commentType == null || !COMMENT_TYPES.contains(commentType)) {
                throw new ServiceException("评论类型只能是 1商品、2门店、3品牌、4广场 或 5公园");
            }
            Long businessId = form.getBusinessId();
            if (businessId == null) {
                throw new ServiceException("请指明是对哪一件商品、哪一家门店、哪一个品牌、哪一条广场消息还是哪一个公园的评论");
            }
            assertTargetExists(commentType, businessId);
            record.setCommentType(commentType);
            record.setBusinessId(businessId);
        }

        // 两个初始数由服务端钉：置顶只有后台能设，点赞数跟着点赞那一条链走
        record.setLikeCount(0);
        record.setIsStick(0);
        fillTrace(record);

        sysCommentMapper.insert(record);
        publishCommentNotice(record, login);
        return record;
    }

    /**
     * 一颗按钮管两个方向：点过就取消、没点过就点上，回按完之后的状态。
     *
     * 两句写（点赞行的插/删 + like_count 那一格）必须一起成，所以挂事务：
     * 只成一句的话要么数加上了但查不出谁点的，要么有那行了数却没动，两边都对不上账。
     * 先问「点过没」再决定往哪边走 —— 库里那道 uk_comment_user 兜的是同一个人插第二行，
     * 撞上了说明这一趟是同一颗按钮被连点两下，那一瞬间库里已经是「点过了」，回 true、不再记数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Integer id) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再点赞");
        if (id == null) {
            throw new ServiceException("请指明是给哪一条评论点赞");
        }
        SysComment comment = sysCommentMapper.selectById(id);
        if (comment == null) {
            throw new ServiceException("评论不存在");
        }
        Long userId = login.getId();
        if (Boolean.TRUE.equals(sysCommentMapper.getUserIsLikeComment(id, userId))) {
            sysCommentMapper.unLikeComment(id, userId);
            sysCommentMapper.updateLikeCount(id, -1);
            return false;
        }
        try {
            sysCommentMapper.likeComment(id, userId, comment.getCommentType());
        } catch (DuplicateKeyException e) {
            // 库里已经是「点过了」，这一趟什么也不补，只把状态回给前端
            return true;
        }
        sysCommentMapper.updateLikeCount(id, 1);
        publishLikeNotice(comment, login);
        return true;
    }

    /**
     * 删自己那一条：软删（写 is_deleted），已收到的点赞行不回收 ——
     * 那是「谁在什么时候点过赞」的事实留痕，和收藏、足迹删行时不动别的表同一个口径。
     *
     * 归属无条件比（不看账号类型）：这一颗是给前台本人用的，不比就等于谁拿到别人的评论 id 都能删。
     * 归属比不上、已经删了、根本没这个 id 三种回同一句「评论不存在」，不分档
     *（分档就让人拿 id 一句句试出这一条挂在谁名下，口径同收藏那一条）。
     *
     * 删掉哪一行就把它名下的整棵子树一起软删（回复能套多层，不只看直接回复）：
     * 只删它自己会留下一堆「父不在了」的回复，
     * 前台那个列表按顶层分页，它们本来就再也显示不出来，与其留悬着的行不如一并收掉。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再删除评论");
        if (id == null) {
            throw new ServiceException("请指明要删除哪一条评论");
        }
        SysComment comment = sysCommentMapper.selectById(id);
        if (comment == null || !login.getId().equals(comment.getUserId())) {
            throw new ServiceException("评论不存在");
        }
        // 回复能套多层，所以删谁都连名下整棵子树一起收：只删自己会留下一支「父不在了」的行，
        // 前台按顶层分页翻不出它们，与其悬着不如一并软删（selectDescendantIds 那一趟递归把各层捞全）
        List<Integer> descendantIds = sysCommentMapper.selectDescendantIds(id);
        if (!descendantIds.isEmpty()) {
            sysCommentMapper.deleteBatchIds(descendantIds);
        }
        sysCommentMapper.deleteById(id);
    }

    /**
     * 过一遍正文里每一串 `[图片](地址)`：地址只认 http(s) 开头、不许太长，张数限 9。
     * 这一串是前端拼上来、能被随便编的字符，而三端又会把里面的地址交给 img 的 src，
     * 所以脏一串就得在这里当场拒掉，不能让它留在库里裂在评论区页面上。
     * 匹配不到 `[图片](…)` 这个形状的（比如只打了「[图片(」半拉子）不参与这道闸，照旧当普通文字存、当普通文字画。
     */
    private void assertImageMarks(String content) {
        Matcher matcher = IMAGE_MARK.matcher(content);
        int count = 0;
        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.length() > MAX_IMAGE_URL_LENGTH || !(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new ServiceException("评论图片地址不合法");
            }
            count++;
        }
        if (count > MAX_IMAGE_COUNT) {
            throw new ServiceException("一条评论最多带 " + MAX_IMAGE_COUNT + " 张图");
        }
    }

    /**
     * ip / 归属地 / 浏览器 / 系统 四格现取，不从前端收（口径同活动埋点、登录时盖 sys_user 那三笔）。
     * request 取不到就整段跳过：这几格是留痕，不该因为读不到 User-Agent 就把人这条评论发不出去。
     */
    private void fillTrace(SysComment record) {
        String ip = IpUtil.getIp();
        record.setIp(ip);
        record.setIpSource(IpUtil.getIp2region(ip));
        HttpServletRequest request = IpUtil.getRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        record.setBrowser(UserAgentUtil.getBrowser(userAgent));
        record.setSystemOs(UserAgentUtil.getOs(userAgent));
    }

    /**
     * 评论落库成功后丢事件出去（发互动消息）：主表那一行和接收人都在 NotificationsUtil 那段异步里写，
     * 这一层只摆「谁、对着哪一行、说什么」；publish 没有事务，靠监听器上那句 fallbackExecution 兜住。
     *
     * 两档收件人：回复 → 被回复那条评论的作者；顶层的门店评价 → 这家店的店长（manager_id 可空，为空就是没店长）。
     * 自己回自己、自己评自家店都不发；回复不再重复惊动店长（只有第一手评价算一条新评价）。
     */
    private void publishCommentNotice(SysComment record, LoginUserInfo login) {
        Long me = login.getId();
        String summary = IMAGE_MARK.matcher(record.getContent()).replaceAll("").trim();
        String author = StringUtils.defaultIfBlank(login.getNickname(), login.getUsername());
        Long commentId = record.getId().longValue();

        Long replyUserId = record.getReplyUserId();
        if (replyUserId != null && !replyUserId.equals(me)) {
            notificationsUtil.publish(NotificationEvent.builder()
                    .type(NotificationTypeEnum.COMMENT)
                    .businessId(commentId)
                    .title("有人回复了你的评论")
                    .message(author + " 回复了你：" + summary)
                    .fromUserId(me)
                    .userId(replyUserId)
                    .build());
        }

        if (record.getParentId() == null && SysComment.TYPE_STORE == record.getCommentType()) {
            ShopStore store = shopStoreMapper.selectById(record.getBusinessId());
            Long managerId = store == null ? null : store.getManagerId();
            if (managerId != null && !managerId.equals(me)) {
                notificationsUtil.publish(NotificationEvent.builder()
                        .type(NotificationTypeEnum.COMMENT)
                        .businessId(commentId)
                        .title("你的门店收到一条新评价")
                        .message(author + " 评价了「" + store.getStoreName() + "」：" + summary)
                        .fromUserId(me)
                        .userId(managerId)
                        .build());
            }
        }
    }

    /**
     * 点赞通知的是被赞那条评论的作者，取消赞不补发（toggleLike 只有真点上那一支会走到这里）。
     * 自己赞自己不发；这一颗在事务里，事件要等提交后才落库 —— 回滚了就不该留下提醒。
     */
    private void publishLikeNotice(SysComment comment, LoginUserInfo login) {
        Long authorId = comment.getUserId();
        if (authorId == null || authorId.equals(login.getId())) {
            return;
        }
        String author = StringUtils.defaultIfBlank(login.getNickname(), login.getUsername());
        String summary = IMAGE_MARK.matcher(StringUtils.defaultString(comment.getContent()))
                .replaceAll("").trim();
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.LIKE)
                .businessId(comment.getId().longValue())
                .title("有人赞了你的评论")
                .message(author + " 赞了你的评论：" + summary)
                .fromUserId(login.getId())
                .userId(authorId)
                .build());
    }

    /**
     * 按类型确认评论对象在（五档各去自己那张表里查），判据和文案抄那颗收藏按钮的 assertTargetExists。
     * 表上没有外键，所以这一趟拦的是"指向不存在的东西"；至于对象后来改名、下架，评论这一条不受影响。
     *
     * 广场消息那一档只判"这条消息还在不在"，不判审核状态 —— 发布者在自己那条待审核的消息底下先说两句话
     * 没有道理要拦，判了反而要说清"为什么我自己的消息不让我评论"；消息被删掉后 selectById 就返回空（逻辑删除），
     * 和商品/门店/品牌那一档是同一个表现。
     */
    private void assertTargetExists(Integer commentType, Long businessId) {
        if (SysComment.TYPE_PRODUCT == commentType) {
            if (shopProductMapper.selectById(businessId) == null) {
                throw new ServiceException("所评商品不存在");
            }
            return;
        }
        if (SysComment.TYPE_STORE == commentType) {
            if (shopStoreMapper.selectById(businessId) == null) {
                throw new ServiceException("所评门店不存在");
            }
            return;
        }
        if (SysComment.TYPE_BRAND == commentType) {
            if (shopBrandMapper.selectById(businessId) == null) {
                throw new ServiceException("所评品牌不存在");
            }
            return;
        }
        if (SysComment.TYPE_PLAZA == commentType) {
            if (shopPlazaMessageMapper.selectById(businessId) == null) {
                throw new ServiceException("所评广场消息不存在");
            }
            return;
        }
        if (SysComment.TYPE_PARK == commentType) {
            if (shopTrendyParkMapper.selectById(businessId) == null) {
                throw new ServiceException("所评主题公园不存在");
            }
            return;
        }
        throw new ServiceException("评论类型只能是 1商品、2门店、3品牌、4广场 或 5公园");
    }
}
