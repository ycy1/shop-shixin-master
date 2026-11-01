package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.api.service.ShopTrendyWebReadService;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopTrendy;
import com.shop.entity.ShopTrendyBox;
import com.shop.entity.ShopTrendyBoxItem;
import com.shop.entity.ShopTrendyPark;
import com.shop.entity.ShopTrendyParkPhoto;
import com.shop.entity.ShopTrendyParkTicket;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopTrendyBoxItemMapper;
import com.shop.mapper.ShopTrendyBoxMapper;
import com.shop.mapper.ShopTrendyCollectRecordMapper;
import com.shop.mapper.ShopTrendyMapper;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.ShopTrendyParkPhotoMapper;
import com.shop.mapper.ShopTrendyParkTicketMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.trendy.ShopParkTicketWebVo;
import com.shop.vo.trendy.ShopParkPhotoWebVo;
import com.shop.vo.trendy.ShopTrendyBoxItemWebVo;
import com.shop.vo.trendy.ShopTrendyBoxWebVo;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectionViewVo;
import com.shop.vo.trendy.ShopTrendyParkWebVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author xxj
 * @title ShopTrendyWebReadServiceImpl
 * @description 潮玩专区三趟读的落地。回填一律「按当页这批 id 一次查完再归堆」，不是一行发一趟查询
 *（口径同后台那几个 fillXxx）；款式预览每台盒最多带 {@link #STYLE_PREVIEW} 个，够卡片上那一排小图。
 */
@Service
@RequiredArgsConstructor
public class ShopTrendyWebReadServiceImpl implements ShopTrendyWebReadService {

    /** 盲盒状态：1 未开始 2 进行中 3 已结束 4 已取消 —— 前台只给前两档 */
    private static final int BOX_NOT_START = 1;
    private static final int BOX_RUNNING = 2;

    /** 园区状态：1 营业中（停业的 0 不进前台） */
    private static final int PARK_OPEN = 1;

    /** 票种、款式、潮玩系列的「在架/启用」档，和后台那几个 service 同一个值 */
    private static final int ON_SHELF = 1;

    /** 一台盒的卡片上那一排小图最多几个款式 */
    private static final int STYLE_PREVIEW = 12;

    /** 照片的审核通过档（字典 audit_status 的 1）：前台图集只出这一档 */
    private static final int PHOTO_PASSED = 1;

    /** 园区详情那张照片卡片上摆几张（3 列 × 2 行）：不轮播，多的那几张走「全部照片」那条分页读 */
    private static final int PHOTO_WALL_MAX = 6;

    /** 「热门」勾上的那一档；列表接口收到 hot=1 就是只查这个值 */
    private static final Integer HOT_ON = 1;

    private final ShopTrendyBoxMapper boxMapper;

    private final ShopTrendyBoxItemMapper boxItemMapper;

    private final ShopTrendyMapper trendyMapper;

    private final ShopTrendyParkMapper parkMapper;

    private final ShopTrendyParkTicketMapper parkTicketMapper;

    /** 只用来捞园区详情那一圈图集（一张照片一行，原先那是园区表上的一个 json 列） */
    private final ShopTrendyParkPhotoMapper photoMapper;

    /** 图鉴那一条读（系列 × 我的进度 × 领取记录）长在集齐领取记录的 mapper 上：分子分母都数的是抽取记录 */
    private final ShopTrendyCollectRecordMapper collectMapper;

    /** 奖励文案那两格要回捞名字：券在营销活动那张表上、实物在商品表上 */
    private final ShopActivityCouponMapper couponMapper;

    private final ShopProductMapper productMapper;

    @Override
    public IPage<ShopTrendyBoxWebVo> pageBoxes(Integer status, Integer hot) {
        List<Integer> visible = Arrays.asList(BOX_NOT_START, BOX_RUNNING);
        LambdaQueryWrapper<ShopTrendyBox> wrapper = new LambdaQueryWrapper<ShopTrendyBox>()
                .in(ShopTrendyBox::getStatus, visible.contains(status) ? Collections.singletonList(status) : visible)
                // 首页那两台机台只要热门的；没勾热门的补位那一趟不带这个条件
                .eq(HOT_ON.equals(hot), ShopTrendyBox::getIsHot, HOT_ON)
                // 热门的排前面：/trendy/box 那一整页也是这个次序，运营在后台勾一下就能换到最前
                .orderByDesc(ShopTrendyBox::getIsHot)
                .orderByAsc(ShopTrendyBox::getSort)
                .orderByDesc(ShopTrendyBox::getId);
        IPage<ShopTrendyBox> page = boxMapper.selectPage(PageUtil.<ShopTrendyBox>getPage(), wrapper);

        Page<ShopTrendyBoxWebVo> result = new Page<>(page.getCurrent(), page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(toBoxVos(page.getRecords()));
        return result;
    }

    @Override
    public ShopTrendyBoxWebVo boxDetail(Long id) {
        ShopTrendyBox row = id == null ? null : boxMapper.selectById(id);
        if (row == null || !isVisibleBoxStatus(row.getStatus())) {
            throw new ServiceException("盲盒不存在或已结束");
        }
        ShopTrendyBoxWebVo vo = new ShopTrendyBoxWebVo();
        BeanUtils.copyProperties(row, vo);
        applyRemain(row, vo);
        List<ShopTrendyBoxWebVo> one = Collections.singletonList(vo);
        fillTrendyName(one);
        // 详情页要看的是整台盒，款式不截（0 = 全给）
        fillBoxItems(one, 0);
        return vo;
    }

    @Override
    public List<ShopTrendyParkWebVo> listParks(Integer hot) {
        // 园区那张表没有 sort 列（票种、盲盒才有），所以热门的排前面、其余按新建的在前
        List<ShopTrendyPark> parks = parkMapper.selectList(new LambdaQueryWrapper<ShopTrendyPark>()
                .eq(ShopTrendyPark::getStatus, PARK_OPEN)
                // 首页轮播那一趟只查热门的（没勾则回退到不带条件的那一趟）
                .eq(HOT_ON.equals(hot), ShopTrendyPark::getIsHot, HOT_ON)
                .orderByDesc(ShopTrendyPark::getIsHot)
                .orderByDesc(ShopTrendyPark::getId));
        List<ShopTrendyParkWebVo> vos = new ArrayList<>(parks.size());
        for (ShopTrendyPark park : parks) {
            ShopTrendyParkWebVo vo = new ShopTrendyParkWebVo();
            BeanUtils.copyProperties(park, vo);
            vos.add(vo);
        }
        fillTicketSummary(vos);
        return vos;
    }

    @Override
    public ShopTrendyParkWebVo parkDetail(Long id) {
        ShopTrendyPark park = assertParkOpen(id);
        ShopTrendyParkWebVo vo = new ShopTrendyParkWebVo();
        BeanUtils.copyProperties(park, vo);
        fillTicketSummary(Collections.singletonList(vo));
        vo.setTickets(listOnShelfTickets(Collections.singletonList(id)).getOrDefault(id, Collections.emptyList()));
        // 卡片上那几格照片：精选在前，只给 6 张（不轮播，剩下的去「全部照片」那一页翻）
        vo.setPhotos(toPhotoVos(photoMapper.selectList(photoOrder(id).last("LIMIT " + PHOTO_WALL_MAX))));
        return vo;
    }

    @Override
    public IPage<ShopParkPhotoWebVo> pageParkPhotos(Long id) {
        assertParkOpen(id);
        IPage<ShopTrendyParkPhoto> page = photoMapper.selectPage(PageUtil.<ShopTrendyParkPhoto>getPage(), photoOrder(id));
        Page<ShopParkPhotoWebVo> result = new Page<>(page.getCurrent(), page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(toPhotoVos(page.getRecords()));
        return result;
    }

    /** 停业、逻辑删除、根本没这个 id —— 都回同一句话，不从错误信息里试哪个园区存在过 */
    private ShopTrendyPark assertParkOpen(Long id) {
        ShopTrendyPark park = id == null ? null : parkMapper.selectById(id);
        if (park == null || !Integer.valueOf(PARK_OPEN).equals(park.getStatus())) {
            throw new ServiceException("园区不存在或暂停营业");
        }
        return park;
    }

    /** 照片在两页上的同一份口径：只给审核通过的，精选在前、同档按运营排的 sort、再按录入倒序 */
    private LambdaQueryWrapper<ShopTrendyParkPhoto> photoOrder(Long parkId) {
        return new LambdaQueryWrapper<ShopTrendyParkPhoto>()
                .eq(ShopTrendyParkPhoto::getParkId, parkId)
                .eq(ShopTrendyParkPhoto::getStatus, PHOTO_PASSED)
                .orderByDesc(ShopTrendyParkPhoto::getIsFeatured)
                .orderByAsc(ShopTrendyParkPhoto::getSort)
                .orderByDesc(ShopTrendyParkPhoto::getId);
    }

    private List<ShopParkPhotoWebVo> toPhotoVos(List<ShopTrendyParkPhoto> rows) {
        List<ShopParkPhotoWebVo> vos = new ArrayList<>(rows.size());
        for (ShopTrendyParkPhoto row : rows) {
            ShopParkPhotoWebVo vo = new ShopParkPhotoWebVo();
            BeanUtils.copyProperties(row, vo);
            vos.add(vo);
        }
        return vos;
    }

    /** 前台能看见的盒：未开始 + 进行中（已结束、已取消的一律不出现） */
    private boolean isVisibleBoxStatus(Integer status) {
        return Integer.valueOf(BOX_NOT_START).equals(status) || Integer.valueOf(BOX_RUNNING).equals(status);
    }

    /**
     * 盒的实体行换成前台那份 VO，顺手把三格回填齐：系列名、在架款式数、款式预览。
     * 三格各一趟批量查询（按当页这批 boxId / trendyId），不按行发查询。
     */
    private List<ShopTrendyBoxWebVo> toBoxVos(List<ShopTrendyBox> rows) {
        List<ShopTrendyBoxWebVo> vos = new ArrayList<>(rows.size());
        for (ShopTrendyBox row : rows) {
            ShopTrendyBoxWebVo vo = new ShopTrendyBoxWebVo();
            BeanUtils.copyProperties(row, vo);
            applyRemain(row, vo);
            vos.add(vo);
        }
        if (vos.isEmpty()) {
            return vos;
        }
        fillTrendyName(vos);
        fillBoxItems(vos, STYLE_PREVIEW);
        return vos;
    }

    /** 剩余次数：0 是「不限」（建表那一档默认就是 0），不限就没有「还剩几」这一说，给 null 让页面不画那一格 */
    private void applyRemain(ShopTrendyBox row, ShopTrendyBoxWebVo vo) {
        if (row.getTotalCount() != null && row.getTotalCount() > 0) {
            int drawn = row.getDrawnCount() == null ? 0 : row.getDrawnCount();
            vo.setRemainCount(Math.max(row.getTotalCount() - drawn, 0));
        }
    }

    private void fillTrendyName(List<ShopTrendyBoxWebVo> vos) {
        Set<Long> ids = new HashSet<>();
        for (ShopTrendyBoxWebVo vo : vos) {
            if (vo.getTrendyId() != null) {
                ids.add(vo.getTrendyId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (ShopTrendy trendy : trendyMapper.selectList(new LambdaQueryWrapper<ShopTrendy>()
                .select(ShopTrendy::getId, ShopTrendy::getTrendyName)
                .in(ShopTrendy::getId, ids))) {
            nameById.put(trendy.getId(), trendy.getTrendyName());
        }
        for (ShopTrendyBoxWebVo vo : vos) {
            vo.setTrendyName(nameById.get(vo.getTrendyId()));
        }
    }

    /**
     * 在架款式数 + 款式预览：一次把这些盒的款式全捞回来，在内存里按盒归堆数完再截断。
     * 比「先分组数一遍、再捞明细」少一趟查询，款式本来就要给预览，数数顺手一起做就行。
     *
     * @param limit 预览截前几档；0 = 全给（详情页那一趟）
     */
    private void fillBoxItems(List<ShopTrendyBoxWebVo> vos, int limit) {
        List<Long> boxIds = new ArrayList<>(vos.size());
        for (ShopTrendyBoxWebVo vo : vos) {
            boxIds.add(vo.getId());
        }
        Map<Long, List<ShopTrendyBoxItemWebVo>> byBox = new HashMap<>();
        for (ShopTrendyBoxItem item : boxItemMapper.selectList(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .eq(ShopTrendyBoxItem::getStatus, ON_SHELF)
                .in(ShopTrendyBoxItem::getBoxId, boxIds)
                .orderByAsc(ShopTrendyBoxItem::getSort)
                .orderByAsc(ShopTrendyBoxItem::getId))) {
            ShopTrendyBoxItemWebVo vo = new ShopTrendyBoxItemWebVo();
            BeanUtils.copyProperties(item, vo);
            byBox.computeIfAbsent(item.getBoxId(), k -> new ArrayList<>()).add(vo);
        }
        for (ShopTrendyBoxWebVo vo : vos) {
            List<ShopTrendyBoxItemWebVo> items = byBox.getOrDefault(vo.getId(), Collections.emptyList());
            vo.setItemCount(items.size());
            vo.setStyles(limit > 0 && items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items);
        }
    }

    /** 园区卡片那两格：名下在架票种数 + 最低价，一次分组捞完（票种明细另有一趟，只在详情那条用） */
    private void fillTicketSummary(List<ShopTrendyParkWebVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> parkIds = new ArrayList<>(vos.size());
        for (ShopTrendyParkWebVo vo : vos) {
            parkIds.add(vo.getId());
        }
        Map<Long, Integer> counted = new HashMap<>();
        Map<Long, BigDecimal> lowest = new HashMap<>();
        for (Map<String, Object> one : parkTicketMapper.selectMaps(new QueryWrapper<ShopTrendyParkTicket>()
                .select("park_id AS parkId", "COUNT(1) AS cnt", "MIN(price) AS lowest")
                .eq("status", ON_SHELF)
                .in("park_id", parkIds)
                .groupBy("park_id"))) {
            Object parkId = one.get("parkId");
            if (parkId == null) {
                continue;
            }
            Long id = ((Number) parkId).longValue();
            Object cnt = one.get("cnt");
            Object min = one.get("lowest");
            counted.put(id, cnt == null ? 0 : ((Number) cnt).intValue());
            lowest.put(id, min == null ? null : new BigDecimal(min.toString()));
        }
        for (ShopTrendyParkWebVo vo : vos) {
            vo.setTicketCount(counted.getOrDefault(vo.getId(), 0));
            vo.setPriceMin(lowest.get(vo.getId()));
        }
    }

    /** 这些园区名下的在架票种，按 parkId 归堆（排序照后台那份：sort 升序、新的在前） */
    private Map<Long, List<ShopParkTicketWebVo>> listOnShelfTickets(List<Long> parkIds) {
        Map<Long, List<ShopParkTicketWebVo>> byPark = new HashMap<>();
        for (ShopTrendyParkTicket ticket : parkTicketMapper.selectList(new LambdaQueryWrapper<ShopTrendyParkTicket>()
                .eq(ShopTrendyParkTicket::getStatus, ON_SHELF)
                .in(ShopTrendyParkTicket::getParkId, parkIds)
                .orderByAsc(ShopTrendyParkTicket::getSort)
                .orderByDesc(ShopTrendyParkTicket::getId))) {
            ShopParkTicketWebVo vo = new ShopParkTicketWebVo();
            BeanUtils.copyProperties(ticket, vo);
            // 实体那格叫 timeSlots（一列存多个时段值），VO 沿用同一个名字，copyProperties 已经带过来了
            byPark.computeIfAbsent(ticket.getParkId(), k -> new ArrayList<>()).add(vo);
        }
        return byPark;
    }

    /**
     * 图鉴那一页：系列打头的一条分页读，分子/分母/领取记录三格都在 XML 那一条 LEFT JOIN 里贴好，
     * 这一层只补两件事 —— 奖励那句人话（rewardText）和「这一行现在能不能点领取」（canClaim）。
     *
     * 认人只认登录态，参数里没有 userId：能点名别人就等于让人替别人看图鉴、领图鉴上的奖励。
     * 没登录照回（进度念「0 / N」），所以这里用的是 getLoginUser() 而不是 requireLoginUser()。
     */
    @Override
    public IPage<ShopTrendyCollectionViewVo> pageCollection() {
        LoginUserInfo me = LoginUserUtils.getLoginUser();
        Long userId = me == null ? null : me.getId();
        IPage<ShopTrendyCollectionViewVo> page = collectMapper.selectCollectionPage(
                PageUtil.<ShopTrendyCollectionViewVo>getPage(), userId);
        List<ShopTrendyCollectionViewVo> rows = page.getRecords();
        fillRewardText(rows);
        for (ShopTrendyCollectionViewVo row : rows) {
            int total = row.getTotalSku() == null ? 0 : row.getTotalSku();
            int got = row.getCollectedSku() == null ? 0 : row.getCollectedSku();
            // 一条规格都没绑的系列永远集不齐（判定那条 SQL 的 tt.total_sku > 0 同一刀），别给一颗点了必失败的按钮
            row.setCanClaim(total > 0 && got >= total && row.getRecordId() == null && hasReward(row));
        }
        return page;
    }

    /**
     * 图鉴卡片底下那颗「看收集明细」：整条系列的规格一格一行，抽到过的那格带第一次凑上的日期。
     *
     * 用的就是后台「集齐进度」点开一个人那条 SQL（同一个 mapper 方法、同一份口径），
     * 只是 userId 不给参数、由登录态取 —— 前台能点名别人就等于让别人看别人的图鉴。
     * 没登录照回，每一格都是"还没抽到"（那句 LEFT JOIN 的 u.user_id = NULL 永远不命中）。
     */
    @Override
    public List<ShopTrendyCollectItemVo> listCollectionItems(Long trendyId) {
        ShopTrendy trendy = trendyId == null ? null : trendyMapper.selectById(trendyId);
        if (trendy == null || !Integer.valueOf(ON_SHELF).equals(trendy.getStatus())) {
            throw new ServiceException("潮玩系列不存在或已下架");
        }
        LoginUserInfo me = LoginUserUtils.getLoginUser();
        return collectMapper.selectCollectItems(trendyId, me == null ? null : me.getId());
    }

    /** 奖励配没配齐：类型要落在 1/2/3 三档里、值那一格读得出数字（判据和领取那条 SQL 一模一样） */
    private boolean hasReward(ShopTrendyCollectionViewVo row) {
        Integer type = row.getRewardType();
        return type != null && (type == ShopTrendy.REWARD_POINTS || type == ShopTrendy.REWARD_COUPON
                || type == ShopTrendy.REWARD_GOODS) && parseRewardValue(row.getRewardValue()) != null;
    }

    /**
     * 那一格奖励的展示文案：积分直接念数，券和实物只给 id 人认不出来，按当页这两批 id 各一趟 IN 把名字捞回来
     *（和后台系列列表那一格同一份口径，见 shop-admin 的 ShopTrendyServiceImpl#fillRewardText；
     *  两边共用不了一个方法是因为它长在后台那一端的 service 上，前台摸不到）。
     */
    private void fillRewardText(List<ShopTrendyCollectionViewVo> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> couponIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        for (ShopTrendyCollectionViewVo row : rows) {
            Long value = parseRewardValue(row.getRewardValue());
            if (value == null || row.getRewardType() == null) {
                continue;
            }
            if (row.getRewardType() == ShopTrendy.REWARD_COUPON) {
                couponIds.add(value);
            } else if (row.getRewardType() == ShopTrendy.REWARD_GOODS) {
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
        for (ShopTrendyCollectionViewVo row : rows) {
            Long value = parseRewardValue(row.getRewardValue());
            if (value == null || row.getRewardType() == null) {
                continue;
            }
            if (row.getRewardType() == ShopTrendy.REWARD_POINTS) {
                row.setRewardText(value + " 分");
            } else if (row.getRewardType() == ShopTrendy.REWARD_COUPON) {
                row.setRewardText(couponNames.getOrDefault(value, "优惠券 #" + value));
            } else if (row.getRewardType() == ShopTrendy.REWARD_GOODS) {
                row.setRewardText(productNames.getOrDefault(value, "实物 #" + value));
            }
        }
    }

    /** 奖励值那一格读不出数字（空值、脏值）就当这一条系列还没配奖励，两批 id 都不进 IN */
    private Long parseRewardValue(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
