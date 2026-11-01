package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopTrendyCollectRewardService;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopTrendy;
import com.shop.entity.ShopTrendyCollectRecord;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopTrendyCollectRecordMapper;
import com.shop.utils.CouponUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.NotificationsUtil;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 集齐判定 + 兑现的实现（三条口径怎么分工见接口 {@link ShopTrendyCollectRewardService}）
 *
 * 一次领取的动作顺序是固定的：一句 SQL 比完这条系列上这个人的进度 → 「先插记录、后兑现、再发通知」
 * → 把兑现那一句回给页面。中间那一步整条都在一个事务里，所以一个人要么"记录 + 分/券 + 通知"三样都在，
 * 要么一样都不在（原来那条定时任务是一批一批做，现在是一颗按钮做一次，落库形状没变，幂等那道闸照旧）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopTrendyCollectRewardServiceImpl implements ShopTrendyCollectRewardService {

    private final ShopTrendyCollectRecordMapper collectMapper;

    private final ShopActivityCouponMapper couponMapper;

    private final ShopActivityCouponUserMapper couponUserMapper;

    private final ShopActivityPointsMapper pointsMapper;

    private final ShopActivityPointsLogMapper pointsLogMapper;

    private final ShopProductMapper productMapper;

    private final ShopPointsSupport shopPointsSupport;

    private final NotificationsUtil notificationsUtil;

    /**
     * 积分流水的业务类型：13 潮玩集齐（字典 points_biz_type）。
     *
     * 12 那一档已经被「发布广场消息审核通过送分」占了（BIZ_TYPE_PLAZA），11 是徽章 —— 这一档只能往后要一个号，
     * 抢同一个号会出现"集齐的分记在广场那档筛选里"，两边都筛不出自己那批。
     */
    private static final int BIZ_TYPE_COLLECT = 13;

    /**
     * 用户券的领取方式：7 潮玩集齐 —— 由集齐奖励发出去的券单独记这一档，
     * 和 2 系统发放（后台「优惠券」那页的手工发放）、6 徽章发放分开，
     * 用户在「我的优惠券」和后台「发放记录」里都能按这一档筛出"这张券是集齐哪条系列换来的"
     */
    private static final int GET_WAY_COLLECT = 7;

    /** 券模板状态：3 已结束 —— 这一档起不再发券（4 已取消 那一档读 CouponUtils 里那一个常量） */
    private static final int TEMPLATE_STATUS_FINISHED = 3;

    /** 实物那一档只在"还没结束/没取消"的模板里挑，这两档是可以发的 */
    private static final int TEMPLATE_STATUS_NOT_STARTED = 1;

    private static final int TEMPLATE_STATUS_RUNNING = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopTrendyCollectRecord claim(Long trendyId) {
        if (trendyId == null) {
            throw new ServiceException("请选择要领取的潮玩系列");
        }
        // 谁领的只认登录态：参数能点名别人就等于替别人领（口径同抽盒那一条）
        long userId = LoginUserUtils.requireLoginUser("后再领取集齐奖励").getId();
        // 现判一次「这个人、这条系列」够不够线：读的那一页可以报"还差几格"，能不能领不能拿那一格当数
        ShopTrendyCollectRecord hit = collectMapper.selectCollectHitOne(trendyId, userId);
        if (hit == null) {
            // 一句话把三种可能都盖住：没集齐、这条系列还没配奖励、这一笔已经领过（第三句在 grant 里报）
            throw new ServiceException("这一条系列你还没集齐（或它还没配集齐奖励），领不到奖励");
        }
        grant(hit);
        return hit;
    }

    /**
     * 兑现一笔集齐奖励：先落记录行，落成功了才动分、券和通知。
     *
     * 撞 uk_trendy_user（两个人同时点、页面重复提交、或这一行刚被别的动作插进去）当场拒回去 ——
     * 一人一条系列只领一次，这条链上就这一道幂等闸；拒的时候一行都没写，分券通知也一样没动。
     */
    private void grant(ShopTrendyCollectRecord hit) {
        TransactionUtils.requireActive("集齐奖励领取");
        hit.setCollectTime(LocalDateTime.now());
        // 判定那条 SQL 带回来的只有系列/人/规格数/奖励四格，兑现结果这两格必须由这一步现填
        hit.setCouponUserId(null);
        hit.setRewardDesc(null);
        try {
            collectMapper.insert(hit);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("这一条系列的奖励你已经领过了");
        }
        Redeem redeem = redeem(hit);
        // 兑现说明回填到刚插的那一行上：发不出去的原因只有这一格能留痕（后台没有这张表的修改入口）
        hit.setRewardDesc(redeem.text);
        collectMapper.updateById(hit);
        publishNotice(hit, redeem.ok);
    }

    /**
     * 按类型分三档兑现，返回给记录表看的那一句 + 成没成。
     *
     * 整档都吞在 try 里：这一颗按钮是"记录已经落了"之后的事，券模板被删、发行量占满都只该让这个人
     * 拿到一句"还没发出去"，不该把那一行领取记录一起撤掉 —— 撤了他再点一次，要么领到第二份，
     * 要么永远卡在"看不出没发"这种状态。补发一律走人工（通知里就是这么说的）。
     */
    private Redeem redeem(ShopTrendyCollectRecord hit) {
        Integer type = hit.getRewardType();
        try {
            if (type != null && type == ShopTrendy.REWARD_POINTS) {
                return redeemPoints(hit);
            }
            if (type != null && type == ShopTrendy.REWARD_COUPON) {
                String couponName = issueCoupon(hit, templateOf(parseId(hit.getRewardValue(), "券模板")));
                return Redeem.done("奖励优惠券「" + couponName + "」已发到卡包");
            }
            if (type != null && type == ShopTrendy.REWARD_GOODS) {
                return redeemGoods(hit);
            }
            return Redeem.failed("这一条系列没配集齐奖励类型，奖励没兑现");
        } catch (ServiceException e) {
            return Redeem.failed("兑现失败：" + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("系列 {} 的集齐奖励发放失败，userId={}", hit.getTrendyId(), hit.getUserId(), e);
            return Redeem.failed("兑现失败：奖励发放这一步没走完，需要人工补发");
        }
    }

    /** 1 积分：原子加到账户上（没账的人由这一笔把第一行账带出来），再记一笔「13 潮玩集齐」的流水 */
    private Redeem redeemPoints(ShopTrendyCollectRecord hit) {
        long amount = parseId(hit.getRewardValue(), "积分奖励");
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            throw new ServiceException("积分奖励得是 1 到 " + Integer.MAX_VALUE + " 之间的整数，现在填的是 "
                    + hit.getRewardValue());
        }
        int points = (int) amount;
        gainPoints(hit.getUserId(), points);
        ShopActivityPoints account = pointsMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                .eq(ShopActivityPoints::getUserId, hit.getUserId())
                .last("LIMIT 1"));
        ShopActivityPointsLog row = new ShopActivityPointsLog();
        row.setUserId(hit.getUserId());
        row.setPoints(points);
        // 这一格记的是"这一笔之后"账上的可用分，读回来、不自己算（口径同徽章、签到那两条链）
        row.setBalance(account == null || account.getBalance() == null ? 0 : account.getBalance());
        row.setBizType(BIZ_TYPE_COLLECT);
        // biz_id / biz_no 留 NULL：这一笔没有可指的那一张单据（认来源看说明里那个系列名）
        row.setDescription("集齐潮玩系列「" + hit.getTrendyName() + "」奖励 " + points + " 分");
        row.setExpireTime(LocalDateTime.now().plusDays(shopPointsSupport.expireDays()));
        pointsLogMapper.insert(row);
        return Redeem.done("奖励 " + points + " 积分已到账");
    }

    /**
     * 3 实物：库里那一格存的是商品 id，系统能兑现的是"这件商品免费拿"，所以发的是它的一张代金券 ——
     * 本期没有实物出库，用户拿着这张券去下单，钱那一头由统一下单的用券链抵掉。
     *
     * 挑哪张券：在这件商品配过的「指定商品」券里，代金券优先（"抵这件商品的价款"就是它的意思）、
     * 同类型再取 id 最小的那张，口径固定，不随运营新建券漂。一张都没配过就把原因写进说明，
     * 这种配置缺失本来在后台保存那一侧就被拦掉了，真走到这里说明券后来被删了或改过范围。
     */
    private Redeem redeemGoods(ShopTrendyCollectRecord hit) {
        Long productId = parseId(hit.getRewardValue(), "实物商品");
        ShopProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("实物商品 " + productId + " 不存在（可能已经被删除）");
        }
        List<ShopActivityCoupon> candidates = couponMapper.selectList(new LambdaQueryWrapper<ShopActivityCoupon>()
                .eq(ShopActivityCoupon::getScopeType, CouponUtils.SCOPE_PRODUCT)
                .in(ShopActivityCoupon::getStatus, TEMPLATE_STATUS_NOT_STARTED, TEMPLATE_STATUS_RUNNING)
                .orderByAsc(ShopActivityCoupon::getId));
        candidates.sort(Comparator.comparing((ShopActivityCoupon item) ->
                item.getCouponType() != null && item.getCouponType() == CouponUtils.TYPE_VOUCHER ? 0 : 1));
        for (ShopActivityCoupon template : candidates) {
            if (!CouponUtils.productScopeIds(template.getScopeValue()).contains(productId)) {
                continue;
            }
            try {
                String couponName = issueCoupon(hit, template);
                return Redeem.done("实物「" + product.getProductName() + "」的代金券「" + couponName + "」已发到卡包");
            } catch (ServiceException e) {
                // 这一张发不出去（额度占满、有效期算不出来）就换下一张候选，都发不出去才落在最后那一句
                log.warn("集齐实物奖励：券模板 {} 发不出去，userId={}：{}", template.getId(), hit.getUserId(),
                        e.getMessage());
            }
        }
        return Redeem.failed("兑现失败：这件实物「" + product.getProductName()
                + "」发不出券来 —— 要么没配过「指定商品」的券，要么那几张的发行量或有效期不对");
    }

    /**
     * 给这一笔分。
     *
     * 增量那条 UPDATE 对没账的人是 0 行改动（不报错），所以拿返回值分清"改到了行"还是"要建第一行账"；
     * 建账撞 uk_user 不报错、改走增量，别把已有那行账覆盖掉（同徽章、签到那两处的写法）。
     */
    private void gainPoints(Long userId, int points) {
        if (pointsMapper.gainPoints(userId, points) == 0) {
            ShopActivityPoints account = new ShopActivityPoints();
            account.setUserId(userId);
            account.setTotalPoints(points);
            account.setUsedPoints(0);
            account.setBalance(points);
            account.setFrozenPoints(0);
            // expire_points 留 0：那一格是"即将过期积分"，要等过期那一条链（本期没有定时任务）才算
            account.setExpirePoints(0);
            try {
                pointsMapper.insert(account);
            } catch (DuplicateKeyException e) {
                if (pointsMapper.gainPoints(userId, points) == 0) {
                    throw new ServiceException("积分账户写入失败，集齐的积分奖励没发出去");
                }
            }
        }
    }

    /** 按 id 取券模板，没了就是配置对不上（模板被删是这一条链唯一常见的死因，所以报错说清 id） */
    private ShopActivityCoupon templateOf(Long couponId) {
        ShopActivityCoupon template = couponMapper.selectById(couponId);
        if (template == null) {
            throw new ServiceException("券模板 " + couponId + " 不存在（可能已经被删除）");
        }
        return template;
    }

    /**
     * 发一张券给一个人（占量 → 回读序号 → 落快照行，三步和后台「发放」、徽章兑现完全同形）。
     *
     * 快照那六格和有效期都从模板现取，走的是同一个 CouponUtils，四份写入方共用一条口径；
     * 发出去的这张券的 id 回填到集齐记录那一行，要回看"这个人领的是哪一张"就不用再按人扫券表。
     */
    private String issueCoupon(ShopTrendyCollectRecord hit, ShopActivityCoupon template) {
        Integer status = template.getStatus();
        if (status != null && (status == TEMPLATE_STATUS_FINISHED
                || status == CouponUtils.TEMPLATE_STATUS_CANCELED)) {
            throw new ServiceException("券「" + template.getCouponName() + "」已经结束或取消");
        }
        LocalDateTime expireTime = CouponUtils.expireTimeOf(template);
        if (couponMapper.increaseIssuedCount(template.getId(), 1) == 0) {
            throw new ServiceException("券「" + template.getCouponName() + "」的发行量已经占满");
        }
        // 占完再读一次：这一行此刻被本事务锁着，读回来的 issued_count 就是"这一张之后"的数，并发发不到同一个号
        ShopActivityCoupon after = couponMapper.selectById(template.getId());
        int issuedAfter = after == null || after.getIssuedCount() == null ? 1 : after.getIssuedCount();

        ShopActivityCouponUser record = new ShopActivityCouponUser();
        record.setCouponId(template.getId());
        record.setCouponCode(CouponUtils.couponCode(template.getId(), issuedAfter));
        record.setUserId(hit.getUserId());
        record.setCouponName(template.getCouponName());
        record.setCouponType(template.getCouponType());
        record.setFaceValue(template.getFaceValue());
        record.setThresholdAmount(template.getThresholdAmount());
        record.setMaxDiscount(template.getMaxDiscount());
        record.setScopeType(template.getScopeType());
        record.setStatus(CouponUtils.USER_STATUS_UNUSED);
        record.setReceiveTime(LocalDateTime.now());
        record.setExpireTime(expireTime);
        record.setGetWay(GET_WAY_COLLECT);
        record.setRemark("由集齐潮玩系列「" + hit.getTrendyName() + "」发放");
        couponUserMapper.insert(record);
        hit.setCouponUserId(record.getId());
        return template.getCouponName();
    }

    /**
     * 集齐了才提醒：撞 uk_trendy_user 那一支已经提前 return，不会给同一个人重复发同一条系列的提醒。
     *
     * 正文只说兑现成功那一份；没发出去的那一句是给运营看的技术原因，不原样推给用户，
     * 只告诉他"没到账、找客服核实"（这一条链不会自动重试，所以补发确实得靠人工）。
     */
    private void publishNotice(ShopTrendyCollectRecord hit, boolean ok) {
        String head = "你已集齐潮玩系列「" + hit.getTrendyName() + "」的全部 " + hit.getSkuCount() + " 款规格";
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.COLLECT)
                .businessId(hit.getTrendyId())
                // 落地页在发送这一刻就定死（口径同园区照片那一档）：web 图鉴那一页认 trendyId 定位到这条系列。
                // 路径不带语种前缀，前台各端自己过 href()；App 没有图鉴这一页，那边认 link 之前先把这一档摘掉
                .link("/trendy/collection?trendyId=" + hit.getTrendyId())
                .title("集齐了「" + hit.getTrendyName() + "」")
                .message(ok ? head + "，" + hit.getRewardDesc()
                        : head + "，但奖励还没发出去，请联系客服核实补发")
                .userId(hit.getUserId())
                .build());
    }

    /**
     * 奖励值那一格读成一个 id / 数量。
     *
     * 空值和脏值都直接抛（不是跳过）：这一格是后台表单选的，读不出来说明配置坏了，
     * 静默跳过等于这个人集齐了却什么都没拿到，还看不出来。
     */
    private long parseId(String raw, String label) {
        if (StringUtils.isBlank(raw)) {
            throw new ServiceException(label + "那一格还没填");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException(label + "那一格读不出数字：" + raw);
        }
    }

    /** 兑现的结果：给记录表看的那一句 + 成没成（成不成决定通知正文怎么说） */
    private static final class Redeem {

        private final boolean ok;

        private final String text;

        private Redeem(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }

        private static Redeem done(String text) {
            return new Redeem(true, text);
        }

        private static Redeem failed(String text) {
            return new Redeem(false, text);
        }
    }
}
