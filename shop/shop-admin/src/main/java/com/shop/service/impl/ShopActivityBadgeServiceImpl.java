package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivityBadge;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.entity.ShopActivityCoupon;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityBadgeMapper;
import com.shop.mapper.ShopActivityBadgeUserMapper;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopActivityMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.service.ShopActivityBadgeService;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 营销徽章定义 服务实现（写法照优惠券 ShopActivityCouponServiceImpl：必填校验、可空列靠实体上的 IGNORED 清空、
 * 状态整行提交）
 *
 * 和数据范围：这一页和优惠券、活动、门店培训一样不按账号类型收窄（平台看全部）—— 徽章是平台发的活动物资。
 * 行上那一格 target_id 说的是"这枚徽章绑哪家店/哪条活动"，不是这一行的可见范围，所以它也挂 IGNORED（清空合法）。
 *
 * 这一张表上没有一格是现算出来的：reach_count 是存的门槛、points / coupon_ids 是存的奖励，
 * 只有列表里那个「已获得人数」是按明细表现数出来的（库里没这一列，也不留一格计数 ——
 * 发放那一步是唯一写入方，数出来就是当前事实，不会因为哪条链漏改而歪掉）。
 * 所以改这一页不会回头动已经发到用户手上的那些行（它们各自带三格快照 + 兑现过的分和券）——
 * 想让某一枚从某个人名下消失，走「获取记录」弹窗里那颗删除（只删明细那一行，分和券不收回）。
 *
 * 判定和兑现都不在这里（在 shop-api 的 ShopActivityBadgeGrantService），这里只定"够多少算达标、达标给什么"。
 */
@Service
@RequiredArgsConstructor
public class ShopActivityBadgeServiceImpl extends ServiceImpl<ShopActivityBadgeMapper, ShopActivityBadge>
        implements ShopActivityBadgeService {

    /** 达标线留 0 或留空时自动链路一枚都不发（只走手动下发），所以这一格只拦"比 0 还小"，不拦 0 */
    private static final long REACH_MIN = 0;

    /** coupon_ids 那一列在库里是 varchar(50)：拼完超过这一格就当场报回去，别让 MySQL 的截断错误顶到页面上 */
    private static final int COUPON_IDS_MAX_LENGTH = 50;

    /** 只用来校验「目标」那一格选中的店/活动还在（注 mapper 而不是 service，避免几份 service 互相依赖） */
    private final ShopStoreMapper shopStoreMapper;

    private final ShopActivityMapper shopActivityMapper;

    /** 奖励里那几张券模板要真存在（下发时按 id 逐个兑现，这里先把没了的拦掉） */
    private final ShopActivityCouponMapper shopActivityCouponMapper;

    /** 删除判据读这张表：一枚徽章发出去过就没有人再需要它的定义 */
    private final ShopActivityBadgeUserMapper shopActivityBadgeUserMapper;

    @Override
    public IPage<ShopActivityBadge> selectPage(ShopActivityBadge query) {
        if (query == null) {
            query = new ShopActivityBadge();
        }
        // 关键字跨徽章名称/文字两列搜 + 按类型和状态筛，还要联门店/活动带出目标名称、数出已获得人数，
        // 条件写 XML 比 wrapper 嵌套清楚；代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectBadgePage(PageUtil.getPage(), query);
    }

    @Override
    public ShopActivityBadge selectBadgeById(Long id) {
        ShopActivityBadge badge = baseMapper.selectBadgeById(id);
        if (badge == null) {
            throw new ServiceException("徽章不存在");
        }
        return badge;
    }

    @Override
    public void insert(ShopActivityBadge badge) {
        validate(badge);
        normalize(badge);
        // 状态由表单那一格定（就是启用/禁用两档，不是流转状态），不传按启用：新建一枚徽章默认就参与判定
        if (badge.getStatus() == null) {
            badge.setStatus(ShopActivityBadge.STATUS_ENABLED);
        }
        save(badge);
    }

    @Override
    public void update(ShopActivityBadge badge) {
        if (badge.getId() == null) {
            throw new ServiceException("徽章ID不能为空");
        }
        if (getById(badge.getId()) == null) {
            throw new ServiceException("徽章不存在");
        }
        validate(badge);
        normalize(badge);
        // 已发出去的人数不在这一格里：那一格不是本表列，由列表那条 SQL 现数
        updateById(badge);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 只删还没发出去过的那几枚：一旦发到用户手上，明细表里就有行指着这一枚定义（三格快照是那一次的凭证），
        // 删了之后那一行再也说不出"它当初是从哪一枚徽章来的"
        //（判据同"发出去过的券删不掉"、"投放过的活动删不掉"、"入过货的单子删不掉"）
        List<ShopActivityBadgeUser> granted = shopActivityBadgeUserMapper.selectList(
                new LambdaQueryWrapper<ShopActivityBadgeUser>()
                        .select(ShopActivityBadgeUser::getBadgeId)
                        .in(ShopActivityBadgeUser::getBadgeId, ids));
        if (!granted.isEmpty()) {
            Set<Long> grantedBadgeIds = granted.stream()
                    .map(ShopActivityBadgeUser::getBadgeId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> names = listByIds(ids).stream()
                    .filter(badge -> grantedBadgeIds.contains(badge.getId()))
                    .map(ShopActivityBadge::getBadgeName)
                    .collect(Collectors.toList());
            throw new ServiceException("已经发出去过的徽章删不掉，这几枚名下还有用户徽章：" + String.join("、", names));
        }
        removeByIds(ids);
    }

    /**
     * NOT NULL 带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报「不能为空」。
     *
     * 还有一刀是按类型清 target_id：签到(3)和消费(4)两类本来就没有目标，
     * 从门店改成消费时那一格不该继续指着原来那家店（实体上它钉了 IGNORED，这里给 null 就真清掉了）。
     */
    private void normalize(ShopActivityBadge badge) {
        Integer type = badge.getBadgeType();
        if (type == ShopActivityBadge.TYPE_SIGNIN || type == ShopActivityBadge.TYPE_CONSUME) {
            badge.setTargetId(null);
        }
        if (badge.getPriority() == null) {
            badge.setPriority(0);
        }
        if (badge.getReachCount() == null) {
            badge.setReachCount(0L);
        }
        if (badge.getPoints() == null) {
            badge.setPoints(0);
        }
        badge.setCouponIds(normalizeCouponIds(badge.getCouponIds()));
    }

    private void validate(ShopActivityBadge badge) {
        if (StringUtils.isBlank(badge.getBadgeName())) {
            throw new ServiceException("徽章名称不能为空");
        }
        badge.setBadgeName(badge.getBadgeName().trim());

        Integer type = badge.getBadgeType();
        if (type == null) {
            throw new ServiceException("请选择徽章类型：达标判定按这一档分叉，没有它这一枚不知道该由谁来判");
        }
        if (type == ShopActivityBadge.TYPE_STORE) {
            // 门店徽章判的是"这个人在那家店买过几件"，没绑店就退化成全平台，那一档该用 4 消费
            if (badge.getTargetId() == null) {
                throw new ServiceException("门店徽章要先选定是哪家店");
            }
            if (shopStoreMapper.selectById(badge.getTargetId()) == null) {
                throw new ServiceException("所选门店不存在");
            }
        } else if (type == ShopActivityBadge.TYPE_ACTIVITY) {
            if (badge.getTargetId() == null) {
                throw new ServiceException("活动徽章要先选定是哪条活动");
            }
            if (shopActivityMapper.selectById(badge.getTargetId()) == null) {
                throw new ServiceException("所选活动不存在");
            }
        } else if (type != ShopActivityBadge.TYPE_SIGNIN && type != ShopActivityBadge.TYPE_CONSUME) {
            throw new ServiceException("徽章类型只有 门店/活动/签到/消费 这四档");
        }

        // 图标必填：前台个人中心把一枚一枚徽章挂成背景上的一排挂件，只有文字的那一枚在那儿立不起来。
        // 这一格还会在发放那一刻定成用户徽章的快照，事后补不了，所以只能在定外观的时候就拦死（口径同徽章名称）。
        if (StringUtils.isBlank(badge.getBadgeIcon())) {
            throw new ServiceException("请上传徽章图标：前台是按挂件展示的，只有文字的那一枚挂不起来");
        }
        badge.setBadgeIcon(badge.getBadgeIcon().trim());

        // 名称故意不查重：同一档活动做几枚同名徽章是常态（口径同活动名称、供应商名称），
        // 所以这张表主键之外没有唯一键，判重交给明细表那一条 uk_badge_user
        if (badge.getReachCount() != null && badge.getReachCount() < REACH_MIN) {
            throw new ServiceException("达标线不能是负数；留 0 就是这一枚只走手动下发");
        }
        if (badge.getPoints() != null && badge.getPoints() < 0) {
            throw new ServiceException("奖励积分不能是负数，不发分就填 0");
        }
        Integer status = badge.getStatus();
        if (status != null && status != ShopActivityBadge.STATUS_ENABLED
                && status != ShopActivityBadge.STATUS_DISABLED) {
            throw new ServiceException("徽章状态只有 1 启用 和 0 禁用 两档");
        }
    }

    /**
     * 奖励里那几张券：拆开、去重、逐个验明模板还在，再按英文逗号拼回去（下发那一步就是按这个串逐个兑现的）。
     *
     * 这里先拦"模板不存在"：券被删掉之后这一格就成了指向不存在模板的孤儿，
     * 自动那一路只会 log 一句跳过那张券（不能让一张配歪的券顶回一笔支付回调），
     * 后台「下发」那一路会整批报错 —— 与其等运营点下去才知道，不如定奖励这一格时就说明白。
     */
    private String normalizeCouponIds(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        // 分隔符认英文逗号、中文逗号、顿号和空白（这一格后台是多选下拉拼出来的，也允许手工改成那种写法）
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : raw.split("[，,、\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(part));
            } catch (NumberFormatException e) {
                throw new ServiceException("优惠券ID那一格「" + part + "」不是数字，这一格只能填逗号分隔的券模板ID");
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        List<Long> idList = new ArrayList<>(ids);
        List<ShopActivityCoupon> exists = shopActivityCouponMapper.selectBatchIds(idList);
        Set<Long> found = exists.stream().map(ShopActivityCoupon::getId).collect(Collectors.toSet());
        List<String> missing = idList.stream().filter(id -> !found.contains(id))
                .map(id -> "#" + id).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new ServiceException("这几张优惠券模板已经不存在：" + String.join("、", missing));
        }
        String joined = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (joined.length() > COUPON_IDS_MAX_LENGTH) {
            throw new ServiceException("奖励里勾的券太多，ID 拼起来 " + joined.length()
                    + " 个字符，超过那一列的 " + COUPON_IDS_MAX_LENGTH + " 格（少勾几张，或拆成几枚徽章）");
        }
        return joined;
    }
}
