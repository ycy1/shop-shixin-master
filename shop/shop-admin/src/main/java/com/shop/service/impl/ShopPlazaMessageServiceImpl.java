package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.plaza.ShopPlazaAuditDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopPlazaMessage;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.ShopPlazaMessageMapper;
import com.shop.service.ShopPlazaMessageService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.NotificationsUtil;
import com.shop.utils.PageUtil;
import com.shop.utils.PlazaContentUtils;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 广场消息 服务实现（写法照门店培训 ShopStoreTrainingServiceImpl：两条自定义读 SQL + 后台改单，这一页没有新增）
 *
 * 数据范围：和门店招商、门店培训一样**不按账号类型收窄**（平台看全部）——
 * 广场是全平台的一页内容，按登录账号收窄会让门店/ERP 账号看不见大批用户发的消息。
 *
 * 这一份没有 add：广场上的消息都是前台用户发的，写入路只有一条（shop-api 的 ShopPlazaWriteService#publish，
 * 落 0 待审核）。后台这里做的是审核、下架、删除。
 *
 * **审核通过是这条链唯一给分的地方**（他定的口径：进后台待审，审核通过才上广场并给分）：
 * 判成 1 已发布 那一刻补 publish_time、给发布者 10 分、记一笔「12 发布广场消息」的流水，三笔在同一个事务里 ——
 * 只改状态不送分，或者送了分没流水，那一本账就对不上了。
 * 送分的闸门是 publish_time 是不是空，不是"这一趟的目标档是 1"：
 * 一条已经通过、又被改判拒绝、再被翻回通过的，publish_time 早就在了，
 * 拿它当"从没上过广场"的凭据，才不会给同一条消息送两笔分。
 *
 * 10 分是这里的常量，不读 sys_config 的 points_config：那份配置住在 shop-api 的 ShopPointsSupport 里，
 * 口径同「后台修改积分不自己按 expire_day 推默认值」那一句 —— 两处各读一遍迟早对不上。
 * 所以流水的 expire_time 留空（这一笔不设过期；要按天过期由后台「积分管理 → 修改」那一路人工填）。
 *
 * 修改认的是 标题 / 正文 / 广场消息图 / 置顶 / 推荐 / 上下架 这六格（他这一轮点名的口径：后台也改得动消息内容）。
 * 正文一改，由它算出来的摘要 content_text 跟着重算，判据和前台发布那一路是同一把尺
 *（PlazaContentUtils），谁都不重抄一遍 —— 否则「前台发不出去的内容，后台改得进去」。
 * 广场消息图这一格照请求体写（发布人自己上传的那一串地址，逗号隔开，不传就是清掉），服务端不从正文抓图来补。
 * 关联对象和审核那几格还是不由这一趟动：那是「别人说的话」和「别人的结论」，各有各的入口。
 *
 * 这一趟走的是 Mapper 上那条自定义 UPDATE（updateContentByAdmin）而不是 updateById：
 * 全局逻辑删除会把 getById / updateById 都补上 is_deleted = 0，已删除的那一条在后台既读不到也改不动，
 * 而这一屏两头都要能碰（他点名的「admin 可看到已删除的」）。代价是留痕那一格要自己递（见 Mapper 注释），
 * 以及 publish_time / audit_status 绝不在这条 SQL 里出现 —— 后台改内容不把已发布的打回重审，
 * 更不能清掉 publish_time：那是审核「头一次通过才给分」的闸门，清了等于改一次正文多领 10 分。
 */
@Service
@RequiredArgsConstructor
public class ShopPlazaMessageServiceImpl extends ServiceImpl<ShopPlazaMessageMapper, ShopPlazaMessage>
        implements ShopPlazaMessageService {

    /** 审核状态：1 已发布 —— 只有进了这一档的才在广场上 */
    private static final int AUDIT_PASSED = 1;

    /** 审核状态：2 已拒绝 —— 只写状态和备注，不给分 */
    private static final int AUDIT_REJECTED = 2;

    /** 状态：1 正常 2 已隐藏，后台那一格只收这两档（0 是历史值，删除走 is_deleted） */
    private static final int STATUS_ON = 1;
    private static final int STATUS_HIDDEN = 2;

    /** 发布送的那一档分（他给的定数：每发布一条消息奖励 10 积分，审核通过时兑现） */
    private static final int PUBLISH_POINTS = 10;

    /** 变动类型（字典 points_biz_type）：12 发布广场消息 */
    private static final int BIZ_TYPE_PLAZA = 12;

    /** 账户表：审核通过要改这一本账（走 Mapper 上那条不带闸门的增量 UPDATE，加钱不需要判余额） */
    private final ShopActivityPointsMapper shopActivityPointsMapper;

    /** 流水表：改账必须补一行凭证，否则发布者那本账上多出来的 10 分没人说得清 */
    private final ShopActivityPointsLogMapper shopActivityPointsLogMapper;

    /** 站内消息：审核结论要送回发布者那台设备上，写法同公告、签收那几处挂载点 */
    private final NotificationsUtil notificationsUtil;

    @Override
    public IPage<ShopPlazaMessage> selectPage(ShopPlazaMessage query) {
        if (query == null) {
            query = new ShopPlazaMessage();
        }
        // BaseEntity 的 isDeleted 有字段默认值 0，请求里不递它也不是 null ⇒ 不显式清空就等于「只看未删除」。
        // 这一页要的就是混着看（他点名的口径：删掉的也要直接翻得到，由列表「状态」那一格印「已删除」认），所以钉成 null。
        query.setIsDeleted(null);
        // 关键字跨三列搜 + 审核状态/上下架/关联类型各筛一刀，条件写 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted 那一刀由上面这一格决定拦不拦。
        // onlyPublic 传 false：这一页要看的正是待审核和已下架的那些。
        // userId 传 null：后台这一页不关心"我赞过没有"，那一格只给前台那颗按钮用。
        // orderByLike 传 false：谁赞得多不在后台单独当第一顺位，点赞只在「已发布」那一档里比。
        // orderByAuditTier 传 true：这一屏按状态分档排 —— 待审核顶在最前（要点「审核」的那些），
        // 已发布其次（档内按点赞量倒序），已拒绝再往后，已删除/已隐藏沉到底。
        // 后台「个人信息 → 广场消息」那一档走的是同一个方法，所以那一屏也跟着分档（他名下待审的那几条排在最前，正合适）。
        // 注意查询条件里的 userId 是另一回事（那一格由 XML 收成 t.user_id = ?）：
        // 「个人信息 → 广场消息」那一档靠它把范围收到这一位名下，这一页不填就是全平台。
        return baseMapper.selectPlazaPage(PageUtil.getPage(), query, null, false, false, true);
    }

    @Override
    public ShopPlazaMessage selectPlazaById(Long id) {
        if (id == null) {
            throw new ServiceException("广场消息ID不能为空");
        }
        // 第三个参数 true：后台这一屏要连已删除的那一条也翻得出正文（详情、审核、修改三个弹窗共用这一条读法）。
        // 走的是 XML 里那一条，不是 getById —— 后者被全局逻辑删除接管，软删行一律回 null
        ShopPlazaMessage message = baseMapper.selectPlazaById(id, null, true);
        if (message == null) {
            throw new ServiceException("广场消息不存在");
        }
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(ShopPlazaAuditDto form) {
        TransactionUtils.requireActive("审核");
        if (form == null || form.getId() == null) {
            throw new ServiceException("广场消息ID不能为空");
        }
        Integer target = requireAuditTarget(form.getAuditStatus());
        ShopPlazaMessage message = getById(form.getId());
        if (message == null) {
            throw new ServiceException("广场消息不存在");
        }
        applyAudit(message, target, form.getAuditRemark());
    }

    /**
     * 批量审核：一次把选中的几条判成同一档。判据逐条走 applyAudit（和单条同一份），
     * ids 先去重再循环 —— 表头全选点两下、或者翻页时选中集攒重复，都不该让同一条被翻两次档、更不该送两笔分。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditBatch(ShopPlazaAuditDto form) {
        TransactionUtils.requireActive("批量审核");
        List<Long> ids = form == null ? null : form.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("请先选择要审核的广场消息");
        }
        Integer target = requireAuditTarget(form.getAuditStatus());
        for (Long id : new LinkedHashSet<>(ids)) {
            if (id == null) {
                continue;
            }
            // 这一条用 getById（带全局逻辑删除那一刀）而不是列表那条自定义 SQL：软删的行回 null 正好跳过，
            // 后台那一屏能把已删除的行勾进多选，但「审核」对它们本来就没有意义（前台早已翻不到）
            ShopPlazaMessage message = getById(id);
            if (message == null) {
                continue;
            }
            applyAudit(message, target, form.getAuditRemark());
        }
    }

    /** 审核档只收 1 已发布 / 2 已拒绝：0 待审核是发布落库那一档，不是人能判出来的 */
    private Integer requireAuditTarget(Integer target) {
        if (target == null || (target != AUDIT_PASSED && target != AUDIT_REJECTED)) {
            throw new ServiceException("审核结果只能是 1 已发布 或 2 已拒绝");
        }
        return target;
    }

    /**
     * 一条消息判完档要做的那几件事，单条和批量共用这一份（谁都不重抄一遍，否则批量这一路最容易漏掉送分或通知）
     */
    private void applyAudit(ShopPlazaMessage message, Integer target, String auditRemark) {
        // 判「通过」之外什么都不给分，重复点「通过」也不给：闸门是 publish_time 空不空，不是目标档
        boolean firstPass = target == AUDIT_PASSED && message.getPublishTime() == null;
        // 整行改：库里查出来的那一行带着所有列，改完再写回去，IGNORED 那几列不会被刷成 NULL
        message.setAuditStatus(target);
        message.setAuditRemark(StringUtils.trimToNull(auditRemark));
        if (firstPass) {
            message.setPublishTime(LocalDateTime.now());
        }
        updateById(message);
        if (firstPass) {
            grantPublishPoints(message);
        }
        notifyAuditResult(message, target, firstPass);
    }

    /**
     * 把审核结论推回发布者的消息中心：他发完就走了，不提醒一句永远不知道自己那条过了还是被拒（他这轮要的这一条）。
     *
     * 走事件不走直接调用（同公告、签收那几处）：audit 挂的是事务，
     * 通知发在事务里 ⇒ 一旦回滚，广场上看不到这条消息，作者却已经收到一句「审核通过」。
     * business_id 直接放这条消息的 id，link 留空由各端自拼（三端详情路径不一样，口径同活动/培训那两档）。
     *
     * 同一档结论重复点「审核」就重复提醒一次：这里不查 alreadySent —— 那条去重是给定时扫描那两档用的
     *（一天扫一遍、扫过就别再吵），手工审核是有人对着这一条做了个决定，宁可多发也别让作者以为没审。
     */
    private void notifyAuditResult(ShopPlazaMessage message, Integer target, boolean firstPass) {
        String name = StringUtils.defaultIfBlank(message.getTitle(), "无标题");
        boolean passed = target == AUDIT_PASSED;
        String title = passed ? "广场消息已通过审核" : "广场消息未通过审核";
        String content = passed
                ? "你发布的「" + name + "」已通过审核，"
                        + (firstPass ? "现在上广场了，同时奖励 " + PUBLISH_POINTS + " 积分" : "重新回到广场上了")
                : "你发布的「" + name + "」未通过审核"
                        + (StringUtils.isBlank(message.getAuditRemark())
                                ? "，可以修改后重新提交"
                                : "：" + message.getAuditRemark());
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.PLAZA)
                .businessId(message.getId())
                .title(title)
                .message(content)
                .userId(message.getUserId())
                .build());
    }

    @Override
    public void update(ShopPlazaMessage message) {
        if (message == null || message.getId() == null) {
            throw new ServiceException("广场消息ID不能为空");
        }
        // 这三格都要求是 1/0 或 1/2 这两档，其余一概不认请求体
        Integer isTop = requireFlag(message.getIsTop(), "是否置顶");
        Integer isRecommend = requireFlag(message.getIsRecommend(), "是否推荐");
        if (message.getStatus() == null
                || (message.getStatus() != STATUS_ON && message.getStatus() != STATUS_HIDDEN)) {
            throw new ServiceException("状态只能是 1 正常 或 2 已隐藏");
        }
        // 正文和标题走前台发布那同一把尺（判据只 PlazaContentUtils 那一份）：
        // 后台这一屏也填正文，尺不一样就等于多开了一处能把空正文写进库的口子
        String content = StringUtils.trimToEmpty(message.getContent());
        String text = PlazaContentUtils.assertContent(content);
        String title = PlazaContentUtils.assertTitle(message.getTitle());
        String plazaImage = PlazaContentUtils.assertPlazaImages(message.getPlazaImage());

        // 整行取回来改再写：这一条 SQL 只 SET 白名单那七格，其余列不在里面，
        // 但读全那一行才拿得到 content 之外的现值（不至于把正文以外的东西带歪）
        ShopPlazaMessage exists = baseMapper.selectPlazaById(message.getId(), null, true);
        if (exists == null) {
            throw new ServiceException("广场消息不存在");
        }
        exists.setTitle(title);
        exists.setContent(content);
        exists.setContentText(PlazaContentUtils.summary(text));
        exists.setPlazaImage(plazaImage);
        exists.setIsTop(isTop);
        exists.setIsRecommend(isRecommend);
        exists.setStatus(message.getStatus());
        // 留痕那一格自定义 SQL 不触发 MyMetaObjectHandler，由这一层按当前登录账号递进去（口径同 MP 自动填的那一套）
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后修改广场消息");
        baseMapper.updateContentByAdmin(exists, login.getId());
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 任何审核状态都能删：违规内容等下架子再删就晚了。
        // 名下收到的赞和分享不回收（那是事实留痕，同删评论时不动点赞行）；评论也不跟着删 ——
        // 广场那条流按顶层分页，消息没了那些回复本来就翻不到，留着还能查得到曾经有过什么
        removeByIds(ids);
    }

    /**
     * 审核通过那一拍给发布者送 10 分，写法照后台「积分管理 → 修改」的加分那一段
     *（增量的 UPDATE 对没有账的人是 0 行改动、不报错，所以要分清"改到了行"还是"要建第一行账"；
     * 建账撞 uk_user 不报错，改走增量，别把已有那行账覆盖掉。这张表的行由第一次变动带出来，谁都不单独开账）
     *
     * 变动后的余额读回来而不是自己加减：同一事务里刚改过的那一版才是账户上的真数，
     * 流水里那一列要能和下一笔接得上。
     */
    private void grantPublishPoints(ShopPlazaMessage message) {
        Long userId = message.getUserId();
        if (shopActivityPointsMapper.gainPoints(userId, PUBLISH_POINTS) == 0) {
            ShopActivityPoints account = new ShopActivityPoints();
            account.setUserId(userId);
            account.setTotalPoints(PUBLISH_POINTS);
            account.setUsedPoints(0);
            account.setBalance(PUBLISH_POINTS);
            account.setFrozenPoints(0);
            // expire_points 留 0：那一格是"即将过期积分"，要等过期那一条链才算
            account.setExpirePoints(0);
            try {
                shopActivityPointsMapper.insert(account);
            } catch (DuplicateKeyException e) {
                // 建账和增量之间正好有别的动作第一次给这个人加分：那一行已经有了，走增量
                shopActivityPointsMapper.gainPoints(userId, PUBLISH_POINTS);
            }
        }
        ShopActivityPoints after = shopActivityPointsMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                .eq(ShopActivityPoints::getUserId, userId)
                .last("LIMIT 1"));

        ShopActivityPointsLog log = new ShopActivityPointsLog();
        log.setUserId(userId);
        log.setPoints(PUBLISH_POINTS);
        log.setBalance(after == null || after.getBalance() == null ? 0 : after.getBalance());
        log.setBizType(BIZ_TYPE_PLAZA);
        // biz_id 指回这条消息；biz_no 留空 —— 这张表故意不做编号列（前后台都按 id 认）
        log.setBizId(message.getId());
        log.setDescription("发布广场消息「" + StringUtils.defaultIfBlank(message.getTitle(), "无标题")
                + "」审核通过，奖励 " + PUBLISH_POINTS + " 分");
        // 过期时间留空：那份 expire_day 配置住在 shop-api 的 ShopPointsSupport 里，后台不重抄它的读法
        shopActivityPointsLogMapper.insert(log);
    }

    /** 置顶/推荐那两档只收 1 或 0：不传就是没打算改这一格，报错比默默补 0 诚实 */
    private Integer requireFlag(Integer value, String name) {
        if (value == null || (value != 0 && value != 1)) {
            throw new ServiceException(name + "只能是 1 是 或 0 否");
        }
        return value;
    }
}
