package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopBrand;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopStore;
import com.shop.entity.ShopTrendy;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.ShopTrendyCollectRecordMapper;
import com.shop.mapper.ShopTrendyIpMapper;
import com.shop.mapper.ShopTrendyMapper;
import com.shop.service.ShopTrendyService;
import com.shop.utils.CouponUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectProgressVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopTrendyServiceImpl extends ServiceImpl<ShopTrendyMapper, ShopTrendy> implements ShopTrendyService {

    /** 券模板状态（字典 coupon_status）：1 未开始 2 发放中 这两档发得出去，3 已结束 4 已取消 发不出去 */
    private static final int TEMPLATE_STATUS_NOT_STARTED = 1;

    private static final int TEMPLATE_STATUS_RUNNING = 2;

    private static final int TEMPLATE_STATUS_FINISHED = 3;

    private static final int TEMPLATE_STATUS_CANCELED = 4;

    /** 下面三个都只用来看还有谁绑着这条潮玩；注入 mapper 而不是 service，避免两边互相依赖 */
    private final ShopProductMapper productMapper;

    private final ShopBrandMapper brandMapper;

    private final ShopStoreMapper storeMapper;

    /** 只用来看表单里选的「所属IP」存不存在（口径同上面三个） */
    private final ShopTrendyIpMapper ipMapper;

    /** 集齐奖励那一档：选的券在不在、实物有没有配得上券，都读这张表判 */
    private final ShopActivityCouponMapper couponMapper;

    /** 「集齐进度」那一弹窗的查询走它（这张表的写方在 shop-api 的定时任务里，后台只读） */
    private final ShopTrendyCollectRecordMapper collectRecordMapper;

    @Override
    public IPage<ShopTrendy> selectPage(ShopTrendy query) {
        if (query == null) {
            query = new ShopTrendy();
        }
        LambdaQueryWrapper<ShopTrendy> wrapper = new LambdaQueryWrapper<ShopTrendy>()
                .like(StringUtils.isNotBlank(query.getTrendyName()), ShopTrendy::getTrendyName, query.getTrendyName())
                .eq(query.getStatus() != null, ShopTrendy::getStatus, query.getStatus())
                // 「潮玩IP」那两层面板共用这一条查询：第一层带 ipId（它名下那些），候选层带 noIp（还没归属任何IP的）
                .eq(query.getIpId() != null, ShopTrendy::getIpId, query.getIpId());
        if (Boolean.TRUE.equals(query.getNoIp())) {
            wrapper.isNull(ShopTrendy::getIpId);
        }
        // 启用在前（status：1启用 0禁用），同状态内按 id 新的在前（这张表没有 sort 列）
        wrapper.orderByDesc(ShopTrendy::getStatus).orderByDesc(ShopTrendy::getId);
        IPage<ShopTrendy> page = page(PageUtil.getPage(), wrapper);
        fillProductCount(page.getRecords());
        fillRewardText(page.getRecords());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindProducts(List<Long> productIds, Long trendyId) {
        if (productIds == null || productIds.isEmpty()) {
            throw new ServiceException("请选择要操作的商品");
        }
        // trendyId 传 null 就是解绑（这一格本来就是可空的）
        if (trendyId != null && getById(trendyId) == null) {
            throw new ServiceException("潮玩系列不存在");
        }
        // 一次捞回来对个数：库里有已经被别人删掉的 id 就当场说，别闷头更新 0 行让人以为绑上了
        List<ShopProduct> found = productMapper.selectBatchIds(productIds);
        if (found.size() != productIds.size()) {
            throw new ServiceException("有商品已不存在，请刷新后重试");
        }
        // 一条 UPDATE 收尾（公网库一趟往返不便宜，别一行发一趟）。
        // 也不能走 updateById(空实体)：那会把实体里其它 IGNORED 列（品牌/门店/主图…）一起写成 null。
        // 留痕两格按 MyMetaObjectHandler 的口径手工补上。
        productMapper.update(null, new LambdaUpdateWrapper<ShopProduct>()
                .set(ShopProduct::getSectionId, trendyId)
                .set(ShopProduct::getUpdateBy, StpUtil.getLoginIdAsLong())
                .set(ShopProduct::getUpdateTime, LocalDateTime.now())
                .in(ShopProduct::getId, productIds));
    }

    /**
     * 回填「名下有几件商品」：按当页这批 id 一次分组数完，别一行发一趟查询。
     * selectMaps 走 BaseMapper，逻辑删除条件自动带上 ⇒ 已删除的商品不计数（口径同删除拦截那三趟）。
     */
    private void fillProductCount(List<ShopTrendy> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(rows.size());
        rows.forEach(row -> ids.add(row.getId()));
        Map<Long, Integer> counted = new HashMap<>();
        for (Map<String, Object> one : productMapper.selectMaps(new QueryWrapper<ShopProduct>()
                .select("trendy_id AS trendyId", "COUNT(1) AS cnt")
                .in("trendy_id", ids)
                .groupBy("trendy_id"))) {
            Object trendyId = one.get("trendyId");
            Object cnt = one.get("cnt");
            if (trendyId != null && cnt != null) {
                counted.put(((Number) trendyId).longValue(), ((Number) cnt).intValue());
            }
        }
        rows.forEach(row -> row.setProductCount(counted.getOrDefault(row.getId(), 0)));
    }

    /**
     * 回填「集齐奖励」那一列给人看的那一句：积分档直接拼成「N 分」，券和实物两档那一格存的是 id、
     * 光给数字没人认得，所以按当页这两批 id 各一趟 IN 把名字捞回来（写法同上面那个计数，一行一趟就成 N 趟公网往返）。
     */
    private void fillRewardText(List<ShopTrendy> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> couponIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        for (ShopTrendy row : rows) {
            Long value = parseRewardValue(row.getRewardValue());
            if (value == null) {
                continue;
            }
            if (isRewardType(row, ShopTrendy.REWARD_COUPON)) {
                couponIds.add(value);
            } else if (isRewardType(row, ShopTrendy.REWARD_GOODS)) {
                productIds.add(value);
            }
        }
        // 空集合不能交给 selectBatchIds：那会拼出 IN () 直接把 SQL 打崩，所以两批各自先判有没有人要查
        Map<Long, String> couponNames = new HashMap<>();
        if (!couponIds.isEmpty()) {
            for (ShopActivityCoupon coupon : couponMapper.selectBatchIds(couponIds)) {
                couponNames.put(coupon.getId(), coupon.getCouponName());
            }
        }
        Map<Long, String> productNames = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (ShopProduct product : productMapper.selectBatchIds(productIds)) {
                productNames.put(product.getId(), product.getProductName());
            }
        }
        for (ShopTrendy row : rows) {
            Long value = parseRewardValue(row.getRewardValue());
            if (value == null) {
                continue;
            }
            if (isRewardType(row, ShopTrendy.REWARD_POINTS)) {
                row.setRewardText(value + " 分");
            } else if (isRewardType(row, ShopTrendy.REWARD_COUPON)) {
                row.setRewardText(couponNames.getOrDefault(value, "券 #" + value));
            } else if (isRewardType(row, ShopTrendy.REWARD_GOODS)) {
                row.setRewardText(productNames.getOrDefault(value, "商品 #" + value));
            }
        }
    }

    /** 奖励值那一格读不出数字（空值、脏值）就当这一条还没配奖励，两批 id 都不进 IN */
    private Long parseRewardValue(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isRewardType(ShopTrendy row, int type) {
        return row.getRewardType() != null && row.getRewardType() == type;
    }

    @Override
    public IPage<ShopTrendyCollectProgressVo> collectProgress(Long trendyId, String keyword) {
        // 先认这条系列再查：弹窗是从列表一行上开的，这一刀正常永远过得去，挡的是手改 trendyId 打接口的情况
        if (trendyId == null || getById(trendyId) == null) {
            throw new ServiceException("潮玩系列不存在");
        }
        return collectRecordMapper.selectCollectProgress(PageUtil.getPage(), trendyId,
                StringUtils.trimToNull(keyword));
    }

    @Override
    public List<ShopTrendyCollectItemVo> collectItems(Long trendyId, Long userId) {
        if (trendyId == null || userId == null) {
            throw new ServiceException("要看某个人的款式明细，得先指明是哪条系列、哪个人");
        }
        if (getById(trendyId) == null) {
            throw new ServiceException("潮玩系列不存在");
        }
        return collectRecordMapper.selectCollectItems(trendyId, userId);
    }

    @Override
    public List<ShopTrendy> listOptions() {
        return list(new LambdaQueryWrapper<ShopTrendy>()
                .eq(ShopTrendy::getStatus, 1)
                .orderByAsc(ShopTrendy::getId));
    }

    @Override
    public void insert(ShopTrendy trendy) {
        validate(trendy, null);
        if (trendy.getStatus() == null) {
            trendy.setStatus(1);
        }
        try {
            save(trendy);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_trendy_name，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("潮玩「" + trendy.getTrendyName() + "」已被占用，可能来自一个已删除的潮玩");
        }
    }

    @Override
    public void update(ShopTrendy trendy) {
        if (trendy.getId() == null) {
            throw new ServiceException("潮玩ID不能为空");
        }
        if (getById(trendy.getId()) == null) {
            throw new ServiceException("潮玩不存在");
        }
        validate(trendy, trendy.getId());
        try {
            updateById(trendy);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("潮玩「" + trendy.getTrendyName() + "」已被占用，可能来自一个已删除的潮玩");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // selectCount 走 BaseMapper，自动过滤已删除的行，所以下架/删掉的商品拦不住这里
        Long usedProducts = productMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                .in(ShopProduct::getSectionId, ids));
        if (usedProducts != null && usedProducts > 0) {
            throw new ServiceException("该潮玩下还有 " + usedProducts + " 个商品，请先在商品里取消关联或删除商品");
        }
        Long usedBrands = brandMapper.selectCount(new LambdaQueryWrapper<ShopBrand>()
                .in(ShopBrand::getSectionId, ids));
        if (usedBrands != null && usedBrands > 0) {
            throw new ServiceException("该潮玩已绑定 " + usedBrands + " 个品牌，请先在品牌里取消绑定");
        }
        Long usedStores = storeMapper.selectCount(new LambdaQueryWrapper<ShopStore>()
                .in(ShopStore::getSectionId, ids));
        if (usedStores != null && usedStores > 0) {
            throw new ServiceException("该潮玩已绑定 " + usedStores + " 家门店，请先在门店里取消绑定");
        }
        removeByIds(ids);
    }

    /**
     * 名称必填 + 判重（excludeId 是修改时排除自己）+ 所属IP存不存在 + 集齐奖励那一档
     */
    private void validate(ShopTrendy trendy, Long excludeId) {
        if (StringUtils.isBlank(trendy.getTrendyName())) {
            throw new ServiceException("潮玩名称不能为空");
        }
        validateReward(trendy);
        // ipId 可空（不归属任何IP），填了就必须是库里真有的：这一格既能从系列表单改，也能从IP那侧的面板改，
        // 光靠页面筛不住直接调接口传脏 id 的情况
        if (trendy.getIpId() != null && ipMapper.selectById(trendy.getIpId()) == null) {
            throw new ServiceException("所选潮玩IP不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopTrendy>()
                .eq(ShopTrendy::getTrendyName, trendy.getTrendyName())
                .ne(excludeId != null, ShopTrendy::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("潮玩「" + trendy.getTrendyName() + "」已存在");
        }
    }

    /**
     * 集齐奖励那一档：类型只能是字典 trendy_reward_type 那三档，奖励值按类型各判各的存在性。
     *
     * 这里判得比发放那一头<b>严</b>是故意的：定时任务那条链是"尽力而为"，券被删了、实物没配券都只会在
     * 领取记录上留一句"兑现失败"就过去了，等平台发现已经是有人集齐之后的事。配置坏了最该在保存这一拍
     * 当场说清楚 —— 运营手上一件实物、一张券，报的错认得出是哪一格。
     */
    private void validateReward(ShopTrendy trendy) {
        Integer type = trendy.getRewardType();
        if (type == null) {
            // 没带类型 = 这一条系列还没配集齐奖励，两格都不判（库里那两列 NOT NULL 有默认值，
            // 发放那条 SQL 按"类型不在 1/2/3 或奖励值是空串"筛掉，不会给人发一笔空的）
            return;
        }
        if (type < ShopTrendy.REWARD_POINTS || type > ShopTrendy.REWARD_GOODS) {
            throw new ServiceException("集齐奖励类型只有 1 积分 / 2 优惠券 / 3 实物三档");
        }
        Long value = parseRewardValue(trendy.getRewardValue());
        if (value == null || value <= 0) {
            throw new ServiceException("集齐奖励值还没填好："
                    + (type == ShopTrendy.REWARD_POINTS ? "积分那一档要填正整数"
                    : type == ShopTrendy.REWARD_COUPON ? "优惠券那一档要选一张券" : "实物那一档要选一件商品"));
        }
        // 抹掉首尾空格再落库：这一格是字符串，发放那一条读它时按数字解析，留个 " 12 " 进去就是给自己找错
        trendy.setRewardValue(String.valueOf(value));
        if (type == ShopTrendy.REWARD_COUPON) {
            ShopActivityCoupon coupon = couponMapper.selectById(value);
            if (coupon == null) {
                throw new ServiceException("所选的集齐奖励优惠券不存在（可能已经被删除）");
            }
            Integer status = coupon.getStatus();
            if (status != null && (status == TEMPLATE_STATUS_FINISHED || status == TEMPLATE_STATUS_CANCELED)) {
                throw new ServiceException("券「" + coupon.getCouponName() + "」已经结束或取消，集齐时发不出去");
            }
            return;
        }
        if (type == ShopTrendy.REWARD_GOODS) {
            ShopProduct product = productMapper.selectById(value);
            if (product == null) {
                throw new ServiceException("所选的实物商品不存在（可能已经被删除）");
            }
            if (!hasIssuableProductCoupon(product.getId())) {
                throw new ServiceException("实物「" + product.getProductName()
                        + "」还没有配一张「指定商品」且还在发的券，集齐时发不出去 —— "
                        + "请先在优惠券那页给它建一张代金券，再回来保存");
            }
        }
    }

    /**
     * 这件商品现在发得出去一张券吗：挑券口径同发放那一条（适用范围是「指定商品」、状态是 未开始/发放中、
     * 名单里含这件商品），这里只判有没有，挑哪一张由发放那一头定（代金券优先、同类型取 id 最小的）。
     */
    private boolean hasIssuableProductCoupon(Long productId) {
        List<ShopActivityCoupon> candidates = couponMapper.selectList(new LambdaQueryWrapper<ShopActivityCoupon>()
                .eq(ShopActivityCoupon::getScopeType, CouponUtils.SCOPE_PRODUCT)
                .in(ShopActivityCoupon::getStatus, TEMPLATE_STATUS_NOT_STARTED, TEMPLATE_STATUS_RUNNING));
        for (ShopActivityCoupon candidate : candidates) {
            if (CouponUtils.productScopeIds(candidate.getScopeValue()).contains(productId)) {
                return true;
            }
        }
        return false;
    }
}
