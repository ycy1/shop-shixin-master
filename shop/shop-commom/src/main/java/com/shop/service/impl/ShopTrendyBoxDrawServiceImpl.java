package com.shop.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.entity.ShopTrendyBox;
import com.shop.entity.ShopTrendyBoxItem;
import com.shop.entity.ShopTrendyBoxUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopTrendyBoxItemMapper;
import com.shop.mapper.ShopTrendyBoxMapper;
import com.shop.mapper.ShopTrendyBoxUserMapper;
import com.shop.service.ShopCartAddService;
import com.shop.service.ShopTrendyBoxDrawService;
import com.shop.vo.trendy.ShopTrendyBoxDrawVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽盒：一趟 = 按盒的类型抽 N 次，每次落一行 shop_trendy_box_user。
 *
 * 四种 box_type 的差别只在「本趟抽几个」「怎么选款」「扣不扣款式库存」这三件事上：
 *   1 单抽   —— 抽 draw_count 次，每次按各款概率加权随机；扣款式库存。
 *   2 端盒   —— 一次拿一整盒 = 本趟把「现在还有余量的款式各一个」都拿走（不重复），扣款式库存；
 *               按 end_box_price 结算（没填端盒价就退回单抽价 × 个数）。
 *   3 一番赏 —— 抽 draw_count 次，**不看概率**，按 sort 顺序从池头往下发（发完的款式自动跳过），扣款式库存。
 *   4 抽签   —— 抽 draw_count 次，按概率加权随机，但**不扣款式库存**（抽签只出个结果，不发实物；
 *               盒自己的可抽次数照扣）。
 * 三种要发实物的类型都靠「条件 UPDATE + 判断影响行数」扣库存，不读出来改回去 —— 两个人同时抽最后一件时，
 * 只有一个能拿到，另一个自动换款或提前收趟（口径同库存/券那几处原子扣减）。
 *
 * 保底（guarantee_count）：这一档按「该用户在该盒最近连续 guarantee_count 次里没出过隐藏款」判，
 * 满足就把本趟的**第一次**给隐藏款（端盒/一番赏不适用：端盒本来就把所有款都拿了，一番赏按顺序发）。
 *
 * 只报钱不扣钱：返回体里的 cost 是这一趟该付多少，扣余额/走支付那条口还没接上，接上时在这份事务里加一步。
 *
 * 抽中即加购：落完抽取记录，把这一趟抽中、且款式挂到了具体规格的实物按 SKU 合并件数写进 shop_consumer_cart
 *（判据与「先叠 → 再复活 → 最后插」那三条路都在 ShopCartAddService 那一份里，和详情页那颗按钮同一个实现）。
 * 所以购物车页和结算那一条链都不用认识盲盒：抽中的东西就是车里的普通一行，该勾勾、该改数量改数量、
 * 按商品正常单价结算。抽签（4）不发实物，那一种不进车。
 * 端盒（2）是这一条上的另一种形状：一趟躺成**一整盒一行**，单价是端盒价、数量是一盒，
 * 盒内有哪几款各几件记在这一行的快照里，下单时才按它拆成每款一条明细（见 {@link #addWholeBox}）。
 */
@Service
@RequiredArgsConstructor
public class ShopTrendyBoxDrawServiceImpl implements ShopTrendyBoxDrawService {

    private static final int TYPE_SINGLE = 1;
    private static final int TYPE_END_BOX = 2;
    private static final int TYPE_ICHIBAN = 3;
    private static final int TYPE_LOT = 4;

    /** 查保底时最多回看多少条：guarantee_count 理论上不会大过这个数，纯粹是给 SQL 一个上限 */
    private static final int GUARANTEE_LOOKBACK_LIMIT = 200;

    private final ShopTrendyBoxMapper boxMapper;

    private final ShopTrendyBoxItemMapper itemMapper;

    private final ShopTrendyBoxUserMapper recordMapper;

    private final ShopCartAddService shopCartAddService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopTrendyBoxDrawVo draw(Long boxId, boolean wholeBox) {
        if (boxId == null) {
            throw new ServiceException("请选择要抽的盲盒");
        }
        long userId = currentUserId();
        ShopTrendyBox box = boxMapper.selectById(boxId);
        if (box == null) {
            throw new ServiceException("盲盒不存在");
        }
        assertOpen(box);
        List<ShopTrendyBoxItem> pool = listPool(boxId);

        int type = box.getBoxType() == null ? TYPE_SINGLE : box.getBoxType();
        int perDraw = box.getDrawCount() == null || box.getDrawCount() < 1 ? 1 : box.getDrawCount();
        // 这一趟要不要一整盒：盒本身就是端盒（2）必然要；单抽盒（1）由页面那颗「购买整盒」点名要
        boolean endBox = type == TYPE_END_BOX || wholeBox;
        assertWholeBoxBuyable(box, type, wholeBox);
        // 整盒一趟 = 现在还有余量的款式各拿一个（不重复）；其余一趟 = 盒配的每次抽取数
        int times = endBox ? countWithStock(pool) : perDraw;
        // 整盒那一趟 = 买一盒，不是"抽一次"，所以不受盒上那个每人上限（max_draw_count）约束；
        // 盒自己的总可抽次数和每一款的余量照旧要拦（那两道拦的是货，不是人）
        if (!endBox) {
            assertQuota(box, userId, times);
        }

        List<ShopTrendyBoxUser> hits = new ArrayList<>(times);
        Set<Long> takenItems = new HashSet<>();
        // 保底只管单抽那几件：整盒本来就把还有余量的款各拿一件，隐藏款在盒里就一定拿得到，不用另加一次
        boolean forceHiddenAtFirst = type == TYPE_SINGLE && !endBox && needGuarantee(box, userId);
        int guard = 0;
        for (int i = 0; i < times; i++) {
            // 每次都要重查一遍余量：上一趟可能刚被别人抽空某款，本地那份 pool 会过期
            ShopTrendyBoxItem picked = pick(box, freshPool(pool), i == 0 && forceHiddenAtFirst, takenItems);
            if (picked == null) {
                break;
            }
            // 抽签不占款式库存；其余类型扣一件，扣不到（被人抢了）就把这款剔出本趟候选再来一次
            if (type != TYPE_LOT && !takeItem(picked.getId())) {
                pool.removeIf(item -> item.getId().equals(picked.getId()));
                i--;
                // 兜底：极端并发下一直抢不到，别把这一趟变成死循环
                if (++guard > times * 3) {
                    break;
                }
                continue;
            }
            takenItems.add(picked.getId());
            hits.add(snapshot(box.getId(), userId, picked));
        }
        if (hits.isEmpty()) {
            throw new ServiceException("这一盒的款式已经抽完了");
        }
        takeBox(box.getId(), hits.size());

        for (ShopTrendyBoxUser hit : hits) {
            recordMapper.insert(hit);
        }
        return result(box, type, hits, addToCart(box, userId, hits, endBox), endBox);
    }

    /**
     * 「购买整盒」这一颗能不能按：抽签（4）本来就不发实物，没有整盒可买；盒上没配端盒价也不给 ——
     * 不然只能退回单抽价 × 件数，那是拿单抽的价卖一整盒（页面上那颗按钮也是看这一格有没有值才画出来的）。
     * 单抽（1）、端盒（2）、一番赏（3）都让整盒买：一番赏整盒 = 现在还有余量的款各拿一件，
     * "按顺序发"在一整盒身上没有意义（本来就是每款一件）。
     */
    private void assertWholeBoxBuyable(ShopTrendyBox box, int type, boolean wholeBox) {
        if (!wholeBox || type == TYPE_END_BOX) {
            return;
        }
        if (type == TYPE_LOT) {
            throw new ServiceException("这个盲盒是抽签，不发实物，没有整盒可买");
        }
        if (box.getEndBoxPrice() == null) {
            throw new ServiceException("这个盲盒没配端盒价，不能整盒买");
        }
    }

    /**
     * 抽中的实物进购物车 = 这一趟抽到几件就加几件（同一款被抽中两次就是那一行 +2；换一台盒抽中同一款
     * 是另一行，各挂各的盒价），
     * 单价按**这台盒的单抽价**写进那一行（不是这款规格的商品原价）—— 抽中即加购这条链要说得清
     * 「你这一件花的是抽盒的钱」，结算和订单明细读的都是购物车那一格（见 ShopCartAddService#tryAdd）。
     * 盒上没配单抽价（price 为空）才退回这条规格的现价。
     *
     * 另外两件事：抽签（4）本来就不发实物，整趟不进车；没挂规格的款式（sku_id 为 NULL，空奖/只展示）
     * 没有可卖的规格，跳过。剩下那些走 ShopCartAddService#tryAdd —— 判据（规格在、商品在、在架、有分类）
     * 和详情页那颗「加入购物车」一模一样，只是过不了的那一种**不回滚这一趟抽盒**：
     * 款式库存和盒的可抽次数都已经扣掉了，让人抽到一件刚好被下架的商品就把这一趟退回，
     * 比「那一件没进车」糟得多（那一条还留在抽取记录里，商品回到架上还能单独处理）。
     *
     * @param endBox 这一趟是不是一整盒（盒本身是端盒、或页面点了「购买整盒」）：那种进车是一整盒一行
     */
    private int addToCart(ShopTrendyBox box, long userId, List<ShopTrendyBoxUser> hits, boolean endBox) {
        Integer raw = box.getBoxType();
        if (raw != null && raw == TYPE_LOT) {
            return 0;
        }
        if (endBox) {
            return addWholeBox(box, userId, hits);
        }
        Map<Long, Integer> quantityBySku = new LinkedHashMap<>();
        for (ShopTrendyBoxUser hit : hits) {
            if (hit.getSkuId() != null) {
                quantityBySku.merge(hit.getSkuId(), 1, Integer::sum);
            }
        }
        int added = 0;
        for (Map.Entry<Long, Integer> one : quantityBySku.entrySet()) {
            if (shopCartAddService.tryAdd(userId, one.getKey(), one.getValue(), box.getPrice(), box.getId())) {
                added += one.getValue();
            }
        }
        return added;
    }

    /**
     * 端盒那一趟进车 = 一整盒一行（不是盒内每款一行）：数量那一格是「一盒」，单价是这一盒的钱，
     * 盒里有哪几款各几件**不抄快照** —— 车上那一行只记「哪台盒端了几盒」，款式按盒 id 现查
     *（见 ShopTrendyBoxStyleService；口径是「一整盒 = 这台盒当前配置的款式各一件」）。
     *
     * 这一盒按多少钱：盒上配了端盒价就按它（这才叫「端一盒的钱」），没配才退回单抽价 × 本趟件数 ——
     * 和这一趟报给页面的那笔 cost 是同一条判据（见 {@link #result}），两处必须一个数，
     * 不然老虎机上说好 75 一盒、车里结出 90。
     * 同一台盒端第二次并到同一行上 +1 盒，价按这一次最新的端盒价覆盖（和商品那一路同一个脾气）。
     *
     * @return 这一趟抽到几件（页面那一句「已加购 N 件」念的是件数，不是盒数）；一件都没进车回 0
     */
    private int addWholeBox(ShopTrendyBox box, long userId, List<ShopTrendyBoxUser> hits) {
        int pieces = 0;
        for (ShopTrendyBoxUser hit : hits) {
            // 空奖、只展示的那种款式没挂规格，没有可卖的规格，不算一件实物
            if (hit.getSkuId() != null) {
                pieces++;
            }
        }
        if (pieces == 0) {
            return 0;
        }
        BigDecimal boxPrice = box.getEndBoxPrice() != null ? box.getEndBoxPrice()
                : (box.getPrice() == null ? BigDecimal.ZERO
                : box.getPrice().multiply(BigDecimal.valueOf(hits.size())));
        return shopCartAddService.tryAddBox(userId, box.getId(), boxPrice) ? pieces : 0;
    }

    /** 谁抽的只能由登录态决定：参数能点名别人就等于替别人抽 */
    private long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            throw new ServiceException("请先登录后再抽盒");
        }
    }

    private void assertOpen(ShopTrendyBox box) {
        if (!Integer.valueOf(2).equals(box.getStatus())) {
            throw new ServiceException("这个盲盒当前不在进行中");
        }
        LocalDateTime now = LocalDateTime.now();
        if (box.getStartTime() != null && now.isBefore(box.getStartTime())) {
            throw new ServiceException("这个盲盒还没开始");
        }
        if (box.getEndTime() != null && now.isAfter(box.getEndTime())) {
            throw new ServiceException("这个盲盒已经结束了");
        }
    }

    /** 候选池：启用中的款式，按 sort、id 排（一番赏就按这个顺序发） */
    private List<ShopTrendyBoxItem> listPool(Long boxId) {
        List<ShopTrendyBoxItem> pool = itemMapper.selectList(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .eq(ShopTrendyBoxItem::getBoxId, boxId)
                .eq(ShopTrendyBoxItem::getStatus, 1)
                .orderByAsc(ShopTrendyBoxItem::getSort)
                .orderByAsc(ShopTrendyBoxItem::getId));
        if (pool.isEmpty()) {
            throw new ServiceException("这个盲盒还没配置款式");
        }
        return pool;
    }

    /**
     * 本趟要重查一次余量：drawn_count 是别的请求随时在写的，池子里那份是开趟时的旧值。
     * 同时带上 status=1 —— 只按 id 批量查会把中途被禁用的款式又放回池子里抽出去。
     */
    private List<ShopTrendyBoxItem> freshPool(List<ShopTrendyBoxItem> pool) {
        List<Long> ids = new ArrayList<>(pool.size());
        for (ShopTrendyBoxItem item : pool) {
            ids.add(item.getId());
        }
        return itemMapper.selectList(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .in(ShopTrendyBoxItem::getId, ids)
                .eq(ShopTrendyBoxItem::getStatus, 1));
    }

    private int countWithStock(List<ShopTrendyBoxItem> pool) {
        int count = 0;
        for (ShopTrendyBoxItem item : pool) {
            if (hasStock(item)) {
                count++;
            }
        }
        return count;
    }

    /** total_count 为 0 表示这一款不限量 */
    private boolean hasStock(ShopTrendyBoxItem item) {
        return item.getTotalCount() == null || item.getTotalCount() == 0 || item.getDrawnCount() < item.getTotalCount();
    }

    /** 每人上限：盒上写了 max_draw_count 才判，算的是「这个人在这个盒里已有的记录数 + 本趟」；
     *  只有"抽"那一类走这一道 —— 整盒买（端盒/购买整盒）不受它限（见 draw 里那句 !endBox 的判据） */
    private void assertQuota(ShopTrendyBox box, long userId, int times) {
        if (box.getMaxDrawCount() == null) {
            return;
        }
        Long done = recordMapper.selectCount(new LambdaQueryWrapper<ShopTrendyBoxUser>()
                .eq(ShopTrendyBoxUser::getBoxId, box.getId())
                .eq(ShopTrendyBoxUser::getUserId, userId));
        long already = done == null ? 0 : done;
        if (already + times > box.getMaxDrawCount()) {
            throw new ServiceException("这个盲盒每人最多抽 " + box.getMaxDrawCount() + " 次，你已经抽了 " + already + " 次");
        }
    }

    /** 保底：最近连续 guarantee_count 次里没出过隐藏款，这一趟第一次就给隐藏款 */
    private boolean needGuarantee(ShopTrendyBox box, long userId) {
        if (box.getGuaranteeCount() == null || box.getGuaranteeCount() < 1) {
            return false;
        }
        List<ShopTrendyBoxUser> recent = recordMapper.selectList(new LambdaQueryWrapper<ShopTrendyBoxUser>()
                .select(ShopTrendyBoxUser::getId, ShopTrendyBoxUser::getIsHidden)
                .eq(ShopTrendyBoxUser::getBoxId, box.getId())
                .eq(ShopTrendyBoxUser::getUserId, userId)
                .orderByDesc(ShopTrendyBoxUser::getId)
                .last("LIMIT " + Math.min(box.getGuaranteeCount(), GUARANTEE_LOOKBACK_LIMIT)));
        if (recent.size() < box.getGuaranteeCount()) {
            // 还没抽够保底那一档的次数，谈不上触发（一次都没抽过也不给白送隐藏款）
            return false;
        }
        for (ShopTrendyBoxUser one : recent) {
            if (one != null && Integer.valueOf(1).equals(one.getIsHidden())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 挑一款出来。返回 null 表示这一趟到此为止（池子里没有可给的了）。
     *
     * @param forceHidden 本趟第一次且触发了保底：直接给隐藏款
     * @param takenItems  端盒要求不重复，已经拿过的款不再选
     */
    private ShopTrendyBoxItem pick(ShopTrendyBox box, List<ShopTrendyBoxItem> pool, boolean forceHidden, Set<Long> takenItems) {
        int type = box.getBoxType() == null ? TYPE_SINGLE : box.getBoxType();
        List<ShopTrendyBoxItem> candidates = new ArrayList<>();
        for (ShopTrendyBoxItem item : pool) {
            if (item == null || !hasStock(item) || takenItems.contains(item.getId())) {
                continue;
            }
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (forceHidden) {
            for (ShopTrendyBoxItem item : candidates) {
                if (Integer.valueOf(1).equals(item.getIsHidden())) {
                    return item;
                }
            }
            // 池子里根本没有隐藏款（或都没余量）：保底无从兑现，正常抽，别卡在这
        }
        if (type == TYPE_ICHIBAN) {
            // 一番赏按顺序发：candidates 已经跟着 pool 的 sort、id 排好
            return candidates.get(0);
        }
        return pickByProbability(candidates);
    }

    /** 按各款概率加权随机。概率不必凑满 100%，只按相对权重取 */
    private ShopTrendyBoxItem pickByProbability(List<ShopTrendyBoxItem> candidates) {
        double total = 0;
        for (ShopTrendyBoxItem item : candidates) {
            total += weight(item);
        }
        if (total <= 0) {
            // 一款都没配概率：退化成等概率抽，至少不抛
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (ShopTrendyBoxItem item : candidates) {
            roll -= weight(item);
            if (roll < 0) {
                return item;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private double weight(ShopTrendyBoxItem item) {
        BigDecimal probability = item.getProbability();
        return probability == null ? 0 : Math.max(0, probability.doubleValue());
    }

    /**
     * 扣一件款式库存：条件 UPDATE，抢不到就返回 false（调用方换款）。
     * remain_count 跟着刷成 total-drawn（total=0 是不限量，那一格给 0）。
     * MySQL 单表 UPDATE 的 SET 从左往右算，所以 drawn_count 先加、remain_count 那句拿到的就是新值。
     */
    private boolean takeItem(Long itemId) {
        return itemMapper.update(null, new LambdaUpdateWrapper<ShopTrendyBoxItem>()
                .setSql("drawn_count = drawn_count + 1")
                .setSql("remain_count = CASE WHEN total_count = 0 THEN 0 ELSE GREATEST(total_count - drawn_count, 0) END")
                .eq(ShopTrendyBoxItem::getId, itemId)
                .and(w -> w.eq(ShopTrendyBoxItem::getTotalCount, 0).or().apply("drawn_count < total_count"))) == 1;
    }

    /** 盒自己的可抽次数同样条件 UPDATE：total_count=0 不限，否则加上本趟不能超 */
    private void takeBox(Long boxId, int hits) {
        int updated = boxMapper.update(null, new LambdaUpdateWrapper<ShopTrendyBox>()
                .setSql("drawn_count = drawn_count + " + hits)
                .eq(ShopTrendyBox::getId, boxId)
                .and(w -> w.eq(ShopTrendyBox::getTotalCount, 0).or().apply("drawn_count + " + hits + " <= total_count")));
        if (updated != 1) {
            throw new ServiceException("这一盒的可抽次数刚好被抽完了，请刷新后重试");
        }
    }

    private ShopTrendyBoxUser snapshot(Long boxId, long userId, ShopTrendyBoxItem picked) {
        ShopTrendyBoxUser record = new ShopTrendyBoxUser();
        record.setBoxId(boxId);
        record.setItemId(picked.getId());
        record.setSkuId(picked.getSkuId());
        record.setUserId(userId);
        // 这三格存快照：款式后来改名、换图、被删掉，历史记录还得说清当时抽到的是什么
        record.setStyleName(picked.getStyleName());
        record.setStyleImage(picked.getStyleImage());
        record.setIsHidden(picked.getIsHidden() == null ? 0 : picked.getIsHidden());
        return record;
    }

    private ShopTrendyBoxDrawVo result(ShopTrendyBox box, int type, List<ShopTrendyBoxUser> hits, int cartCount,
                                       boolean endBox) {
        ShopTrendyBoxDrawVo vo = new ShopTrendyBoxDrawVo();
        vo.setBoxId(box.getId());
        vo.setBoxNo(box.getBoxNo());
        vo.setBoxName(box.getBoxName());
        vo.setBoxType(type);
        vo.setDrawnCount(hits.size());
        // 本趟真进了购物车几件（抽签 0；挂不上规格或那款商品已下架的也不算），页面按它念那一句
        vo.setCartCount(cartCount);
        vo.setIsProbabilityShow(box.getIsProbabilityShow());
        vo.setTotalCount(box.getTotalCount());
        vo.setRecords(hits);
        // 整盒那一趟有整盒价就按整盒价（这才叫「端一盒的钱」），没填才退回单抽价 × 个数；逐抽那一趟一律单抽价 × 个数
        if (endBox && box.getEndBoxPrice() != null) {
            vo.setCost(box.getEndBoxPrice());
        } else {
            vo.setCost(box.getPrice() == null ? BigDecimal.ZERO : box.getPrice().multiply(BigDecimal.valueOf(hits.size())));
        }
        // 这一趟是不是一整盒：页面的结果弹窗按它决定那一句怎么说（「买了一整盒」vs「抽中 N 件」）
        vo.setWholeBox(endBox);
        // 盒的已抽次数就地加本趟这一份就够（公网库多一趟往返不值当），并发下别人那一趟不体现在这里
        int before = box.getDrawnCount() == null ? 0 : box.getDrawnCount();
        vo.setBoxDrawnCount(before + hits.size());
        return vo;
    }
}
