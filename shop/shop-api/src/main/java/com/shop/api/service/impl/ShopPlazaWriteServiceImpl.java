package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopPlazaWriteService;
import com.shop.dto.plaza.ShopPlazaPublishDto;
import com.shop.dto.plaza.ShopPlazaShareDto;
import com.shop.dto.plaza.ShopPlazaUpdateDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopPlazaLike;
import com.shop.entity.ShopPlazaMessage;
import com.shop.entity.ShopPlazaShare;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityMapper;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopPlazaLikeMapper;
import com.shop.mapper.ShopPlazaMessageMapper;
import com.shop.mapper.ShopPlazaShareMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.utils.IpUtil;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PlazaContentUtils;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 前台广场 写入实现（发布、改自己那条、删自己那条、点赞开关、记一次分享）
 *
 * 五颗都要求登录：/api/** 不过 Sa-Token 拦截器，所以由这一层自己取登录态，取不到就报「请先登录后再…」。
 * 入参里没有 userId 那一格 —— 发布的永远是自己发的，参数能点名别人就等于替别人发言
 *（口径同发表评论、那颗收藏按钮）。点赞记的是「哪个人点的」，游客没有这一格可记。
 *
 * **改和删只认本人**：这一层拿登录 id 和那一行的 user_id 硬比（同评论删除、购物车 assertOwn 那两处的写法），
 * 归属比不上、已经删了、根本没这个 id 三种回同一句「这条消息不存在或已经被删除」，不分档
 *（分档就让人拿 id 一句句试出这一条挂在谁名下）。审核状态一律不参与这一刀：
 * 待审核的和被拒绝的更要让作者自己收掉，否则发错了只能等后台来处理。
 *
 * **改完重新排队**：update 把 audit_status 打回 0 待审核、上一次那句审核备注一并清掉，
 * 广场上看不到它直到后台再判一次；publish_time 和 status 都不动 ——
 * 前者是后台 audit 里那把「头一次通过才给分」的闸门，清掉它就等于改一次正文多领 10 分；
 * 后者是后台下架那一格，改正文不该把自己翻回上架。
 *
 * **发布不等于上广场**：这一颗只把消息落成 0 待审核，广场上看不到它，也还不给分。
 * 送那 10 分在后台「审核通过」那一刻（shop-admin 的 ShopPlazaMessageServiceImpl#audit，
 * 改状态和记流水在同一个事务里）。为什么不给发布就直给的版本：广场是公开页，
 * 谁都能发、发完就拿到分，等于把积分口子开在没人看过的内容上（审核那一档是他定的口径）。
 * 删掉那条也不收回已经拿到的分：那 10 分是审核通过那一刻给「内容过了审」这件事的，不是这条消息的租金。
 *
 * 正文是富文本组件产出的 HTML（Web 装 WangEditor、App 由 textarea + 表情 + 选图自己拼一段 HTML），
 * 图片就在 &lt;img src&gt; 标签里，所以不像评论那样收 `[图片](url)` 那一串、也不限张数：
 * 先传后提交那条口全站只有一处（POST /api/file/uploadImage），编辑器把回来的地址写进标签再随正文一起提交。
 * 服务端只过四道理：剥掉标签后的文字不能是空（除非正文里有图）、文字部分限长、整段 HTML 限长、图片那一串限张数和长度 ——
 * content 是 mediumtext，不给上限就等于让人往库里灌。这四道理连同由正文算出的摘要都在 PlazaContentUtils 里，
 * 后台「修改」那一屏现在也改得动正文，判据必须和发布者这一路是同一把尺，所以两边都调它、谁都不重抄一遍。
 * 脏 HTML 不在这里挡：前台渲染走 sanitizeRichText（只留标签白名单），后台展示走剥标签，
 * 写入侧留原文（口径同公告正文留 HTML 喂邮件附件那一路）。
 *
 * 广场消息图 plaza_image 是正文之外单独的一格：发布人自己上传（同样先传后提交，回来的地址按顺序用英文逗号拼在这一格），
 * 卡片配图取第一张，详情页在正文下方整组铺开；服务端不从正文里抓图来补这一列。这一格可空，而且每次都照表单写 ——
 * 表单不传就是清空（实体钉 IGNORED），所以两端「修改」时必须把已有那串图带回来，否则提交那一下会把它们抹掉。
 *
 * content_text（纯文本摘要）由服务端从正文里算，表单不收这一格：
 * 列表卡片要不取正文就能铺出一段摘要，靠的正是这一格，让前端传就等于传两份可能对不上的东西。
 *
 * 三个计数（点赞/评论/分享）这里一个都不回写，读的时候一律按明细表现算（见实体注释）：
 * 所以 toggleLike 只写 shop_plaza_like 那一行、share 只插 shop_plaza_share 那一行，
 * 广场上那个数永远不会和明细对不上。
 */
@Service
@RequiredArgsConstructor
public class ShopPlazaWriteServiceImpl implements ShopPlazaWriteService {

    /** 关联类型的四档业务对象 + 0 无关联，和字典 plaza_relation_type、和列表那条 SQL 的四个 LEFT JOIN 是同一套 */
    private static final int RELATION_NONE = 0;
    private static final int RELATION_PRODUCT = 1;
    private static final int RELATION_STORE = 2;
    private static final int RELATION_BRAND = 3;
    private static final int RELATION_ACTIVITY = 4;

    /** 分享渠道：5 复制链接 —— 前端不点名渠道时的默认（本期只有网页端复制链接和 App 端转发，不接分享 SDK） */
    private static final int CHANNEL_COPY_LINK = 5;

    /** 状态：1 正常（新发的那一条本来就是上架的，下架由后台改 status） */
    private static final int STATUS_ON = 1;

    /** 审核状态：0 待审核 —— 发布一律落这一档，广场上看不到 */
    private static final int AUDIT_PENDING = 0;

    /** 审核状态：1 已发布 —— 只有这一档在广场上 */
    private static final int AUDIT_PASSED = 1;

    private final ShopPlazaMessageMapper shopPlazaMessageMapper;

    private final ShopPlazaLikeMapper shopPlazaLikeMapper;

    private final ShopPlazaShareMapper shopPlazaShareMapper;

    /** 下面四颗只用来校验「关联对象」那一格选中的东西还在（注入 mapper 而不是 service，避免两边互相依赖） */
    private final ShopProductMapper shopProductMapper;

    private final ShopStoreMapper shopStoreMapper;

    private final ShopBrandMapper shopBrandMapper;

    private final ShopActivityMapper shopActivityMapper;

    @Override
    public ShopPlazaMessage publish(ShopPlazaPublishDto form) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后发布广场消息");
        if (form == null) {
            throw new ServiceException("请填写消息内容");
        }
        String content = StringUtils.trimToEmpty(form.getContent());
        String text = PlazaContentUtils.assertContent(content);
        String title = PlazaContentUtils.assertTitle(form.getTitle());
        String plazaImage = PlazaContentUtils.assertPlazaImages(form.getPlazaImage());
        int relationType = form.getRelationType() == null ? RELATION_NONE : form.getRelationType();
        Long relationId = form.getRelationId();
        assertRelation(relationType, relationId);

        ShopPlazaMessage record = new ShopPlazaMessage();
        record.setUserId(login.getId());
        record.setTitle(title);
        record.setContent(content);
        record.setContentText(PlazaContentUtils.summary(text));
        record.setPlazaImage(plazaImage);
        record.setRelationType(relationType);
        record.setRelationId(relationType == RELATION_NONE ? null : relationId);
        // 这四格由服务端钉，表单里没有：置顶/推荐只有后台改得动，两个状态档见类注释
        record.setIsTop(0);
        record.setIsRecommend(0);
        record.setStatus(STATUS_ON);
        record.setAuditStatus(AUDIT_PENDING);
        // 三个计数库里是 NOT NULL DEFAULT 0，这里补上 0 让 INSERT 那一列显式有值；
        // 之后谁都不回写（读的时候现算覆盖，实体上钉了 NEVER）
        record.setLikeCount(0);
        record.setCommentCount(0);
        record.setShareCount(0);
        shopPlazaMessageMapper.insert(record);
        return record;
    }

    /**
     * 改自己发的那一条：正文/标题/关联对象换一遍，再打回 0 待审核重排队（为什么打回见类注释）。
     *
     * 校验和发布用同一把尺，所以改完那一段不会比原来那一条宽松：直接放一段空正文进去，
     * 广场上就会留下一条只有「待审核」状态、卡片上是空的行。
     *
     * 整行取回来改再 updateById：这张表上 title / content_text / plaza_image / relation_id / audit_remark
     * 都钉的是 IGNORED（能清空），拿只填了几个字段的稀疏实体写回去，没带的列会被刷成 NULL
     *（同盘点单、门店那一路踩过的坑）。
     */
    @Override
    public void update(ShopPlazaUpdateDto form) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后修改广场消息");
        if (form == null || form.getId() == null) {
            throw new ServiceException("请指明要修改哪一条广场消息");
        }
        ShopPlazaMessage record = requireOwnMessage(form.getId(), login.getId());
        String content = StringUtils.trimToEmpty(form.getContent());
        String text = PlazaContentUtils.assertContent(content);
        String title = PlazaContentUtils.assertTitle(form.getTitle());
        String plazaImage = PlazaContentUtils.assertPlazaImages(form.getPlazaImage());
        int relationType = form.getRelationType() == null ? RELATION_NONE : form.getRelationType();
        Long relationId = form.getRelationId();
        assertRelation(relationType, relationId);

        record.setTitle(title);
        record.setContent(content);
        record.setContentText(PlazaContentUtils.summary(text));
        record.setPlazaImage(plazaImage);
        record.setRelationType(relationType);
        record.setRelationId(relationType == RELATION_NONE ? null : relationId);
        // 排队重来：状态打回待审核，上一次那句审核备注跟着清掉（发布者重新看到的一定是这一版的结论）；
        // publish_time / status 两格一句都没动，理由在类注释里
        record.setAuditStatus(AUDIT_PENDING);
        record.setAuditRemark(null);
        shopPlazaMessageMapper.updateById(record);
    }

    /**
     * 删自己发的那一条：软删（写 is_deleted），任意审核状态都删得动。
     *
     * 名下收到的赞、评论、分享行一律不回收 —— 那是「谁在什么时候点过赞」的事实留痕，
     * 和删评论时不动点赞行、删收藏时不动别的表同一个口径；广场那条流按消息分页，消息没了它们本来就翻不到。
     * 一行软删，一趟就一句 SQL，所以不挂事务（同购物车删除那一条）。
     */
    @Override
    public void delete(Long id) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后删除广场消息");
        if (id == null) {
            throw new ServiceException("请指明要删除哪一条广场消息");
        }
        requireOwnMessage(id, login.getId());
        shopPlazaMessageMapper.deleteById(id);
    }

    /**
     * 一颗按钮管两个方向：点过就取消（硬删那一行）、没点过就点上，回按完之后的状态。
     *
     * 和评论那颗的写法差一处：这里不补 like_count 那句 UPDATE —— 广场上的点赞数是现算的，
     * 所以这一趟只有一行插/删，仍然挂事务（走同一套「多表写入必须一起成」的闸，将来接计数也不用改形状）。
     *
     * 先试删再试插：删得动就是「点过」，一行都没删掉就是「没点过」——
     * 判据压在库里那句 uk_message_user 上，同一颗按钮被连点两下时第二趟插不进去，
     * 捕住 DuplicateKeyException 回 true（此刻库里确实已经是「点过了」），不报错。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long id) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后点赞");
        TransactionUtils.requireActive("点赞");
        assertPublic(id);
        Long userId = login.getId();
        int removed = shopPlazaLikeMapper.delete(new LambdaQueryWrapper<ShopPlazaLike>()
                .eq(ShopPlazaLike::getMessageId, id)
                .eq(ShopPlazaLike::getUserId, userId));
        if (removed > 0) {
            return false;
        }
        ShopPlazaLike like = new ShopPlazaLike();
        like.setMessageId(id);
        like.setUserId(userId);
        try {
            shopPlazaLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            return true;
        }
        return true;
    }

    /**
     * 记一次分享：只插一行留痕，不判重复、不动任何计数（分享数读这张表现算）。
     *
     * 这一颗也要求登录：user_id 那一列是 NOT NULL，「谁分享的」是这一行的主语，
     * 游客点复制链接没得记（口径同曝光埋点里只有需要认人时才收登录态的写法）。
     * ip 服务端现取，前端传什么都不认；share_content / device_id 两列本期不收。
     */
    @Override
    public void share(ShopPlazaShareDto form) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后分享");
        if (form == null || form.getMessageId() == null) {
            throw new ServiceException("请指明要分享哪一条广场消息");
        }
        assertPublic(form.getMessageId());
        ShopPlazaShare record = new ShopPlazaShare();
        record.setMessageId(form.getMessageId());
        record.setUserId(login.getId());
        record.setShareChannel(form.getShareChannel() == null ? CHANNEL_COPY_LINK : form.getShareChannel());
        record.setIp(IpUtil.getIp());
        shopPlazaShareMapper.insert(record);
    }

    /**
     * 取回我自己发的那一条：归属无条件比（不看账号类型），这一路是给前台本人用的，
     * 不比就等于谁拿到别人的消息 id 都能改能删（写法同 ShopCommentWriteServiceImpl#delete）。
     *
     * 查不到、已经删了、挂在别人名下三种都回同一句，不告诉调用者差在哪一档。
     */
    private ShopPlazaMessage requireOwnMessage(Long id, Long userId) {
        ShopPlazaMessage record = shopPlazaMessageMapper.selectById(id);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new ServiceException("这条消息不存在或已经被删除");
        }
        return record;
    }

    /**
     * 只有广场上看得见的那一条才收得到赞和分享：判 status=1 且 audit_status=1（同列表 SQL 里那一刀）。
     * 待审核的内容还没有公开，给一个还没上广场的东西点赞/记分享，那个数对谁都不显示、只是脏数据。
     */
    private void assertPublic(Long messageId) {
        if (messageId == null) {
            throw new ServiceException("请指明是对哪一条广场消息操作");
        }
        ShopPlazaMessage message = shopPlazaMessageMapper.selectById(messageId);
        if (message == null) {
            throw new ServiceException("这条消息不存在或已经被删除");
        }
        boolean publicOne = Integer.valueOf(STATUS_ON).equals(message.getStatus())
                && Integer.valueOf(AUDIT_PASSED).equals(message.getAuditStatus());
        if (!publicOne) {
            throw new ServiceException("这条消息还没上广场，先不用点赞分享");
        }
    }

    /** 挂业务对象时那一档要认、而且要真有其人/其物（表上没有外键，这一趟拦的是"指向不存在的东西"） */
    private void assertRelation(int relationType, Long relationId) {
        if (relationType == RELATION_NONE) {
            return;
        }
        if (relationType < RELATION_NONE || relationType > RELATION_ACTIVITY) {
            throw new ServiceException("关联类型只能是 0无关联、1商品、2门店、3品牌 或 4活动");
        }
        if (relationId == null) {
            throw new ServiceException("选了关联类型就要一并选要关联的那一个对象");
        }
        if (RELATION_PRODUCT == relationType && shopProductMapper.selectById(relationId) != null) {
            return;
        }
        if (RELATION_STORE == relationType && shopStoreMapper.selectById(relationId) != null) {
            return;
        }
        if (RELATION_BRAND == relationType && shopBrandMapper.selectById(relationId) != null) {
            return;
        }
        if (RELATION_ACTIVITY == relationType && shopActivityMapper.selectById(relationId) != null) {
            return;
        }
        throw new ServiceException("所选关联对象不存在");
    }
}
