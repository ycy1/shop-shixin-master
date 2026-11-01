package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopPlazaReadService;
import com.shop.dto.plaza.ShopPlazaQueryDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopPlazaMessage;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopPlazaMessageMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 前台广场 读取实现
 *
 * 鉴权同评论区那几颗：/api/** 不过 Sa-Token 拦截器，所以这里自己取登录态。
 * 广场那一页和详情都不要求登录 —— 广场是内容页，游客要能逛；取到登录 id 只是多联一次
 * shop_plaza_like，把「我赞过这一条没有」带回来给那颗按钮显示初始状态，取不到就当没人赞过。
 * 只有 {@link #myPage} 那一条要登录：它的谓语是「我发的」，没有登录态连查谁都不知道。
 *
 * **广场上看不到没审核过的东西**：那两条读法里那一刀都由服务层压 ——
 * 列表传 onlyPublic=true（SQL 里钉 status=1 AND audit_status=1），详情由 {@link #detail} 自己判。
 * 所以查询参数里那格 auditStatus 在广场那一页点名也没用：SQL 里 onlyPublic 那一刀跟在它后面，
 * 两个条件一起 AND，点了「待审核」只会捞回空的一页，翻不到别人还没过审的内容（我的那一页要正是另一回事）。
 * status / isDeleted 两格 DTO 里干脆没有 —— 服务层是照 DTO 一格一格搭查询条件的，DTO 里没出现的列前端就点不到名。
 * 我的那一页不走 onlyPublic 这一刀：那里看的正是自己还没过审、被拒掉的那几条。
 *
 * 列表那一趟不取 content：正文是富文本 HTML，一屏十条拖着它既没地方显示又把响应撑肥，
 * 卡片预览读的是 content_text、配图读的是发布人自己上传的 plaza_image（一串地址取第一张），正文里那些图跟着详情走。
 */
@Service
@RequiredArgsConstructor
public class ShopPlazaReadServiceImpl implements ShopPlazaReadService {

    /** 状态：1 正常（2 是后台下架，0 是历史值） */
    private static final int STATUS_ON = 1;

    /** 审核状态：1 已发布 —— 只有进了这一档的才在广场上 */
    private static final int AUDIT_PASSED = 1;

    /** orderByLike 那一格认 1（口径同 isRecommend 那一个开关） */
    private static final int ORDER_BY_LIKE = 1;

    private final ShopPlazaMessageMapper shopPlazaMessageMapper;

    @Override
    public IPage<ShopPlazaMessage> page(ShopPlazaQueryDto query) {
        ShopPlazaMessage condition = new ShopPlazaMessage();
        // 软删那一刀由服务层钉死：列表 SQL 现在是「isDeleted 递了才拦」（后台广场管理页要混着看已删除的那几条），
        // 前台这一路不钉就等于把别人删掉的內容推到广场上
        condition.setIsDeleted(0);
        boolean orderByLike = false;
        if (query != null) {
            condition.setKeyword(query.getKeyword());
            condition.setRelationType(query.getRelationType());
            condition.setAuditStatus(query.getAuditStatus());
            condition.setIsRecommend(query.getIsRecommend());
            orderByLike = Integer.valueOf(ORDER_BY_LIKE).equals(query.getOrderByLike());
        }
        // orderByAuditTier 恒 false：状态分档是后台那一屏的活儿（要判的是待审核的那些），
        // 广场上只会看到已发布 + 正常的那几条（onlyPublic 那一刀已经钉死），分档对前台是个空操作
        return shopPlazaMessageMapper.selectPlazaPage(PageUtil.getPage(), condition, currentUserId(), true, orderByLike, false);
    }

    @Override
    public IPage<ShopPlazaMessage> myPage(ShopPlazaQueryDto query) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后查看我的广场消息");
        ShopPlazaMessage condition = new ShopPlazaMessage();
        if (query != null) {
            condition.setKeyword(query.getKeyword());
            condition.setRelationType(query.getRelationType());
            condition.setAuditStatus(query.getAuditStatus());
        }
        // 归属和 selfLiked 用的是同一个登录 id：这一页只看自己的，那两颗按钮的状态也就顺手带回来了
        // 这一页的筛选比广场那一页多一格 auditStatus：作者要正是想挑「还在审的那几条 / 被拒的那几条」来改
        return shopPlazaMessageMapper.selectMyPlazaPage(PageUtil.getPage(), condition, login.getId());
    }

    @Override
    public ShopPlazaMessage detail(Long id) {
        if (id == null) {
            throw new ServiceException("请指明要看哪一条广场消息");
        }
        Long userId = currentUserId();
        // 这一条 SQL 不拦审核状态和上下架，也不给前台看软删行（第三个参数 false）：待审核那一条作者本人要看得到（要说"还在审"和那句审核备注）
        ShopPlazaMessage message = shopPlazaMessageMapper.selectPlazaById(id, userId, false);
        if (message == null) {
            throw new ServiceException("这条消息不存在或已经被删除");
        }
        boolean publicOne = Integer.valueOf(STATUS_ON).equals(message.getStatus())
                && Integer.valueOf(AUDIT_PASSED).equals(message.getAuditStatus());
        boolean mine = userId != null && userId.equals(message.getUserId());
        // 不是广场上的那一些、又不是自己发的：回同一句"不存在"。
        // 分档说成"这条还在审核"就等于让任何人拿 id 试出哪几条还没过审、挂在谁名下
        if (!publicOne && !mine) {
            throw new ServiceException("这条消息不存在或已经被删除");
        }
        return message;
    }

    /** 取当前登录 id，没登录就是 null（两条读 SQL 都吃 null：等值条件联不上，selfLiked 自然为 false） */
    private Long currentUserId() {
        LoginUserInfo login = LoginUserUtils.getLoginUser();
        return login == null ? null : login.getId();
    }
}
