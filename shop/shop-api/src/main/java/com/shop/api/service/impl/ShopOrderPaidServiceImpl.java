package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.api.service.ShopOrderDeliveryService;
import com.shop.api.service.ShopOrderPaidService;
import com.shop.api.service.ShopOrderPointsService;
import com.shop.api.service.ShopOrderWalletService;
import com.shop.dto.order.ShopOrderPaidDto;
import com.shop.entity.ShopConsumerAddress;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopErpSaleOutboundItem;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopStore;
import com.shop.entity.SysOrder;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.ShopErpInventoryMapper;
import com.shop.mapper.ShopErpSaleOutboundItemMapper;
import com.shop.mapper.ShopErpSaleOutboundMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysOrderMapper;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.order.ShopOrderPaidVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付结果通知 服务实现
 *
 * 一次调用落的这几处：sys_order 主单 0 待支付 → 1 已支付（补 pay_time），
 * 每一行明细的发货仓重定一次（routeWarehouse：先这家门店的门店仓，再就近找有货的启用仓 ——
 * 就近的起点是**这一单的收货地址**（sys_order.address_id 背后那一条，下单就是按它分的仓），
 * 地址用不上（老订单没落 address_id、或者那条地址已被删/没坐标）才退回按门店经纬度算，
 * 几条都挑不出来才沿用它下单时分到的那一家），
 * 外加按 shop_order_item 逐行生成一张 outbound_source=1 的出库单（0 待出库、明细一件没出，
 * 但成本价和金额已经按订单明细的实付金额定死了 —— 见 createOutbound），
 * 以及规格表上那个冗余的 sales_count（同一处按订单明细每一行买了几个这个规格累加，见 createOutbound 末尾），
 * 最后是积分账户：下单冻住的那一笔分在这一步真扣走（frozen → used），再按每一行选的规格送分（同一处，见 createOutbound 末尾）。
 * 下单选的是余额支付的话，钱包也在同一处结转：占住的那一笔从冻结余额转进累计消费（可用余额没再动，所以不记流水）。
 * 全程一个事务：出库单建不出来就等于这次通知没处理，主单的"已支付"也别留下。
 *
 * 徽章不在这一刻判（2026-10-18 起自动那两条全住进定时任务）：门店/消费两类比的都是这个人名下的累计件数、
 * 不是这一单买了多少，挂在收钱这条链上只是多背几趟公网 SQL，见 {@code ShopActivityBadgeGrantService#grantPurchaseBadges}。
 *
 * 不动总库存。下单时锁住的那份可用量一直锁着，直到后台「商品出库」页点「出库」才真减总库存
 * （那边顺手把锁定还回来，见 ShopErpInventoryServiceImpl#outbound）。
 * 改仓那一步动的也只是 locked_stock：新仓先锁上、旧仓那份才还掉，两笔在同一次调用里，
 * 所以任何时刻这一行的货都有一份锁定指着它 —— 后台出库还锁定时才对得上账。
 * 也就是说"付了钱但一直没人出库"的单子会把货锁在仓里 —— 这是这一版的既定口径，
 * 要放开得另做超时释放/取消归还，别在这里偷偷减库存。
 *
 * 为什么这些代码在 shop-api 而不是复用后台那套 service：
 * shop-api 只依赖 shop-commom，摸不到后台的 service（也摸不到库存 service），
 * 所以这里只用 commom 的 mapper 写单据、减库存一律不碰（改仓只挪锁定，走的是 commom 那两条
 * lockStock / unlockStock 的条件增量）。
 * 成本价抄的是订单明细上的数，不是库存行上的加权成本，所以摸不到库存 service 也不影响这一格。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderPaidServiceImpl implements ShopOrderPaidService {

    private final SysOrderMapper sysOrderMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    private final ShopErpSaleOutboundMapper saleOutboundMapper;

    private final ShopErpSaleOutboundItemMapper saleOutboundItemMapper;

    /** 分仓的三条 SQL 都挂在这儿：门店仓 / 就近仓两个候选查询，加锁定和解锁那一进一出 */
    private final ShopErpInventoryMapper shopErpInventoryMapper;

    /** 门店自己那一份经纬度：这一单没落到收货地址（老订单）或那条地址用不上时，拿它当就近找仓的起点 */
    private final ShopStoreMapper shopStoreMapper;

    /** 就近找仓的起点优先取这一单的收货地址：下单就是按它的坐标分的仓，回调沿用同一个地点才不会两头判 */
    private final ShopConsumerAddressMapper shopConsumerAddressMapper;

    /** 下单那一刻建出来的那条配送单（0 待派单、只绑了订单）在这一刻换绑到刚建出来的出库单上 */
    private final ShopOrderDeliveryService shopOrderDeliveryService;

    /** 找单、捞明细行、算这一行实付多少 —— 统一下单和统一退货要的是同一份口径，收成一处 */
    private final ShopOrderSupport shopOrderSupport;

    /** 只用来累加规格表上的冗余销量（这一行买了几个这个规格），SKU 表其余读写都在后台那侧 */
    private final ShopProductSkuMapper shopProductSkuMapper;

    /** 钱收到这一刻才把下单冻住的分真扣走，并按这一单买的东西送分（见 ShopOrderPointsService#settle） */
    private final ShopOrderPointsService shopOrderPointsService;

    /** 下单选的是余额支付的话，这一刻把占住的那一笔真花掉（见 ShopOrderWalletService#settle） */
    private final ShopOrderWalletService shopOrderWalletService;

    /**
     * 订单状态（字典 order_status）：0 待支付 / 1 已支付 / 2 已完成 / 3 已取消 / 4 已退款 / 5 已关闭。
     * 这里只用到 0 和 1，其余几档是为了把"不能再按已支付处理"说清楚。
     * 主表没有"已出库/已发货"这两档（2026-09-21 下线，挪到明细行 shop_order_item.ship_status），
     * 所以回调把单子推到 1 就是终点，后面出到哪一步看各行
     */
    private static final int ORDER_STATUS_UNPAID = 0;
    private static final int ORDER_STATUS_PAID = 1;
    private static final int ORDER_STATUS_CANCELLED = 3;
    private static final int ORDER_STATUS_REFUNDED = 4;
    private static final int ORDER_STATUS_CLOSED = 5;

    /** 出库单状态（字典 sale_outbound_status）：0 待出库 —— 回调建出来的单恒是这个值 */
    private static final int OUTBOUND_STATUS_PENDING = 0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderPaidVo paid(ShopOrderPaidDto form) {
        //主单和出库单必须同生共死，事务压根没开就别往下写：不然出了错会留下"已支付但没有出库单"的单子
        TransactionUtils.requireActive("支付回调");
        SysOrder order = resolveOrder(form);
        int status = order.getStatus() == null ? -1 : order.getStatus();
        if (status < 0) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」状态为空，无法处理支付结果");
        }
        // 已取消 / 已退款 / 已关闭：这三种单子不能因为收到一条"支付成功"就复活。
        // 真收到这种通知说明上游有问题（关单和支付撞车，或者通知是伪造的），报出去让人查，别默默记账
        if (status == ORDER_STATUS_CANCELLED || status == ORDER_STATUS_REFUNDED || status == ORDER_STATUS_CLOSED) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」已取消/已退款/已关闭，不能按已支付处理");
        }
        if (form.getPayAmount() != null && order.getPayAmount() != null
                && form.getPayAmount().setScale(2, RoundingMode.HALF_UP)
                .compareTo(order.getPayAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new ServiceException("支付金额 " + form.getPayAmount() + " 与订单应付金额 "
                    + order.getPayAmount() + " 不一致，已拒绝处理");
        }

        // 0 → 1 用条件更新当"这张单由我来处理"的凭据：网关会重试通知，两个并发回调里只有一个能把这一格翻过来。
        // 已经是 1/6/7/2 的（钱早就收过了）这一句 WHERE status = 0 不成立、不动任何东西，
        // pay_time 也就不会被后到的重复通知刷成新时间
        sysOrderMapper.update(null, new LambdaUpdateWrapper<SysOrder>()
                .set(SysOrder::getStatus, ORDER_STATUS_PAID)
                .set(form.getPayType() != null, SysOrder::getPayType, form.getPayType())
                .set(SysOrder::getPayTime, LocalDateTime.now())
                .eq(SysOrder::getId, order.getId())
                .eq(SysOrder::getStatus, ORDER_STATUS_UNPAID));
        int after = status == ORDER_STATUS_UNPAID ? ORDER_STATUS_PAID : status;

        // 一张订单只自动建一张出库单：重复回调拿回原来那一张。
        // 再建一张会把同一批订单明细重复出两遍库存，所以这一道闸必须在这条路径的最前面
        ShopErpSaleOutbound existing = saleOutboundMapper.selectOne(new LambdaQueryWrapper<ShopErpSaleOutbound>()
                .eq(ShopErpSaleOutbound::getOrderId, order.getId())
                .orderByAsc(ShopErpSaleOutbound::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return buildVo(order.getOrderNo(), after, existing, false);
        }
        ShopErpSaleOutbound created = createOutbound(order, form.getPayType());
        // 配送单在下单那一刻就已经建好了（那时它只绑了订单、出库单那一格还空着）。
        // 这里把它换绑到刚建出来的出库单上，并按改派后的发货仓刷一遍发货点和距离 ——
        // 配送费不动（买家付的那个金额里已经含了它）。换绑之后这一条要等商品出库页「生成配送单」弹框里点「确认」才进抢单池
        shopOrderDeliveryService.rebindToOutbound(order, created);
        return buildVo(order.getOrderNo(), after, created, true);
    }

    /**
     * 找单：orderId 优先，没给才按 orderNo 查（支付通知里带的就是下单时给网关的那个商户单号）。
     * 「找不到就拒」那一段和统一下单、统一退货是同一句，收在 {@link ShopOrderSupport#requireOrder}
     */
    private SysOrder resolveOrder(ShopOrderPaidDto form) {
        if (form == null) {
            throw new ServiceException("缺少支付通知参数");
        }
        return shopOrderSupport.requireOrder(form.getOrderId(), form.getOrderNo());
    }

    /**
     * 按订单明细逐行建出库单：一行订单明细对应一行出库明细（同一商品不同规格本来就是两行），
     * 仓库按 routeWarehouse 现算（门店仓优先，就近那一档的起点是这一单的收货地址），
     * 只有它没改派时才等于下单时分好的那一个。
     *
     * 成本和金额也在这一刻定死：出库成本价 = 这一行订单明细的实付金额 ÷ 购买数量，
     * 小计 = 实付金额，主表总金额 = 各行实付相加 = 这一单的货值实付。
     * 配送费不出货、不进成本，免邮券抵的又是运费，所以这一格和 sys_order.pay_amount 本来就差着那一笔运费
     *（积分抵掉的那一份两边都扣，同一式子，见 ShopOrderSupport#paidAmount）。
     * 也就是说线上单结的是卖出去的价，不是该仓库存的加权进货成本，
     * 所以后台点「出库」时那一格成本价禁用、传什么都不认（见 ShopErpSaleOutboundServiceImpl#outbound）。
     *
     * @param notifiedPayType 支付通知里报上来的渠道（上面刚拿它覆盖过主单那一格）：
     *                        余额支付的那一单要拿它和主单上原本那一格对一次账，见 ShopOrderWalletService#settle
     */
    private ShopErpSaleOutbound createOutbound(SysOrder order, Integer notifiedPayType) {
        List<ShopOrderItem> lines = shopOrderSupport.orderLines(order.getId());
        if (lines.isEmpty()) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」没有商品明细，无法生成出库单");
        }
        List<ShopErpSaleOutboundItem> items = new ArrayList<>(lines.size());
        int total = 0;
        BigDecimal paidTotal = BigDecimal.ZERO;
        // 收货地址整张单只查一次：它是明细行"就近找仓"的起点，逐行查就是每行多一次没有意义的往返
        ShopConsumerAddress receiver = resolveReceiver(order);
        for (int i = 0; i < lines.size(); i++) {
            ShopOrderItem line = lines.get(i);
            String row = "第 " + (i + 1) + " 行商品「" + line.getProductName() + "」";
            if (line.getSkuId() == null) {
                throw new ServiceException(row + "缺少规格，无法生成出库单");
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new ServiceException(row + "的购买数量异常，无法生成出库单");
            }
            Long warehouseId = routeWarehouse(line, row, receiver);
            if (warehouseId == null) {
                // 走到这儿是"门店没仓、离收货地（老订单是离门店）最近的那些仓也都没货、下单时又没分到仓"三头都空
                //（统一下单之前的老订单才会这样），这种单没有对应的那行锁定可以还，
                // 要人工先补分仓，别让回调默默建一张出不了货的单
                throw new ServiceException(row + "定不出发货仓库（没有门店仓，就近也没找到有货的启用仓），无法生成出库单");
            }
            BigDecimal paid = shopOrderSupport.paidAmount(line, row, "无法生成出库单");
            ShopErpSaleOutboundItem item = new ShopErpSaleOutboundItem();
            // outboundId 等主表插完再回填
            item.setOrderItemId(line.getId());
            item.setWarehouseId(warehouseId);
            item.setSkuId(line.getSkuId());
            item.setQuantity(line.getQuantity());
            item.setOutboundQuantity(0);
            // 单价按实付金额摊到每件（两位小数四舍五入），小计用实付金额本身而不是"单价 × 数量"：
            // 摊的时候总有分位舍入，用实付金额才保证各行加起来正好等于订单应付
            item.setCostPrice(paid.divide(BigDecimal.valueOf(line.getQuantity()), 2, RoundingMode.HALF_UP));
            item.setAmount(paid);
            items.add(item);
            total += line.getQuantity();
            paidTotal = paidTotal.add(paid);
        }

        ShopErpSaleOutbound doc = new ShopErpSaleOutbound();
        doc.setOutboundNo(GeneratorNoUtils.getSaleOutboundNo());
        doc.setOrderId(order.getId());
        doc.setOrderNo(order.getOrderNo());
        doc.setOutboundSource(ShopErpSaleOutbound.SOURCE_ONLINE_ORDER);
        doc.setTotalQuantity(total);
        doc.setTotalAmount(paidTotal.setScale(2, RoundingMode.HALF_UP));
        doc.setStatus(OUTBOUND_STATUS_PENDING);
        // user_id / remark / outbound_time 留 NULL：回调这一刻没有后台登录人，也还没出货。
        // 这一列恒 NULL 正是这张表故意不按 user_id 收窄的原因（见实体注释）
        try {
            saleOutboundMapper.insert(doc);
        } catch (DuplicateKeyException e) {
            // uk_outbound_no：单号是时间戳 + 三位随机，撞上的概率低，但撞上了整单回滚重来比改号简单
            throw new ServiceException("出库单号生成冲突，请重新处理支付通知");
        }
        for (ShopErpSaleOutboundItem item : items) {
            item.setOutboundId(doc.getId());
            saleOutboundItemMapper.insert(item);
        }
        // 销量在这一刻记：下单不算（没付钱的单子不该占销量），收到支付通知才加，
        // 而出库单"一张订单只建一次"那道闸（paid 里查到 existing 就原样返回）正好也是这一下的唯一闸门，
        // 网关重复推送不会把它加两遍。明细一行本来就是一个具体规格，所以逐行发一条条件增量，不再按商品并
        for (ShopOrderItem line : lines) {
            shopProductSkuMapper.increaseSalesCount(line.getSkuId(), line.getQuantity());
        }
        // 积分在这一刻真扣走：下单冻住的那一笔从 frozen_points 转成 used_points（可用余额没再动，所以不记流水），
        // 再按这一单买的东西送分（Σ 件数 × shop_product_sku.gain_points，记一笔「2 购物」的流水）。
        // 加在这里而不是别处，是因为上面那道"一张订单只建一张出库单"的闸正好也是这一拍唯一的闸门 ——
        // 网关重复推送在 existing 那里就返回了，走不到这一步，分和送分都不会加两遍
        shopOrderPointsService.settle(order, lines);
        // 余额支付的那一单在这一刻把钱真花掉：下单占住的那一笔从 frozen_balance 转进 total_consume
        //（可用余额不再动，所以这一拍不记流水）。通知里的渠道和主单上原本那一格对不上就当场拒 ——
        // 上面那一句 update 已经把那一格覆盖过了，不拦的话"这一单冻着钱包的钱"那个记号就丢了，
        // 那一笔钱会一直留在 frozen_balance 里出不来（整单回滚，主单的"已支付"也留不下）
        shopOrderWalletService.settle(order, notifiedPayType);
        // 徽章不在这里判：门店/消费两类比的是这个人名下的累计件数，2026-10-18 起和签到那一档一样
        // 由定时任务一轮一条 SQL 全平台比完（见 ShopActivityBadgeGrantService#grantPurchaseBadges）
        return doc;
    }

    /**
     * 这一单的收货地址（sys_order.address_id 背后那一条，统一下单时用户选的就是它）。
     * 只有经纬度齐了才返回，下面三种情况都返回 null 让调用方回落到门店坐标：
     * 老订单没落这一列、地址后来被人删了、或者那条地址只有地区没设坐标
     * （那种单下单是按地区分的仓，这里拿不到收件坐标就照旧那一条链走，最后沿用下单分到的那一家）。
     */
    private ShopConsumerAddress resolveReceiver(SysOrder order) {
        if (order.getAddressId() == null) {
            return null;
        }
        ShopConsumerAddress address = shopConsumerAddressMapper.selectById(order.getAddressId());
        if (address == null || address.getLongitude() == null || address.getLatitude() == null) {
            return null;
        }
        return address;
    }

    /**
     * 这一行从哪家仓出库：先看这一行所属**门店的仓**（shop_erp_warehouse.store_id 命中这家门店、
     * 启用中、且这一条规格在那家仓还够卖）；门店没建仓或者货不够，就**就近**找一家有货的启用仓 ——
     * 就近的起点优先用**这一单的收货地址**（那才是货真正要寄去的地点，下单分仓用的也是它），
     * 地址用不上才退回拿**门店自己的经纬度**当起点；几条都挑不出来就沿用它下单时分到的那一家
     * （下单就是按这个收货地分的仓，那份锁定本来就在它身上）。
     *
     * 改派了仓就要把锁定一起挪过去，否则后台点「出库」时还锁定的那一家和下单锁住的那一家不是同一个，
     * 会出现"这边的货被减了、那边的可用量还被白白锁着"。挪的顺序是**先锁新仓、锁到了才还旧仓**：
     * 新仓这一刻不够卖（下单之后又被人锁走了一批）就整行退回原仓出库，货始终有一份锁定指着它，
     * 不会出现空档。还旧仓那一条返回 0 行属于数据不对（锁定丢了），报出去让整单回滚重来，
     * 绝不能只锁新不还不旧 —— 那是凭空多锁一份货。
     *
     * 真正改派成功时会把 shop_order_item.warehouse_id 一并改掉：那一列是"这一行的货锁在哪家仓"的唯一说法，
     * 出库动作减库存/还锁定、我的订单详情显示发货仓库，读的都是它。
     *
     * @param receiver 这一单的收货地址，用不上时是 null（见 {@link #resolveReceiver}）
     * @return 这一行的发货仓ID；一家都定不出来返回 null（由调用方报错，不建出不了货的单）
     */
    private Long routeWarehouse(ShopOrderItem line, String row, ShopConsumerAddress receiver) {
        Long allocated = line.getWarehouseId();
        Long target = null;
        if (line.getStoreId() != null) {
            target = shopErpInventoryMapper.selectStoreWarehouseId(line.getSkuId(),
                    line.getStoreId(), line.getQuantity());
        }
        if (target == null) {
            if (receiver != null) {
                target = shopErpInventoryMapper.selectNearestWarehouseId(line.getSkuId(),
                        receiver.getLongitude(), receiver.getLatitude(), line.getQuantity());
            } else if (line.getStoreId() != null) {
                ShopStore store = shopStoreMapper.selectById(line.getStoreId());
                if (store != null && store.getLongitude() != null && store.getLatitude() != null) {
                    target = shopErpInventoryMapper.selectNearestWarehouseId(line.getSkuId(),
                            store.getLongitude(), store.getLatitude(), line.getQuantity());
                }
            }
        }
        if (target == null || target.equals(allocated)) {
            return allocated;
        }
        if (shopErpInventoryMapper.lockStock(line.getSkuId(), target, line.getQuantity()) == 0) {
            // 挑仓到锁仓之间被别人抢走了，这一行照旧仓发（旧仓那份锁定还在）
            return allocated;
        }
        if (allocated != null && shopErpInventoryMapper.unlockStock(line.getSkuId(), allocated,
                line.getQuantity()) == 0) {
            throw new ServiceException(row + "改派到新的发货仓时，还不清下单时锁在旧仓的那份库存，已中止处理");
        }
        shopOrderItemMapper.update(null, new LambdaUpdateWrapper<ShopOrderItem>()
                .set(ShopOrderItem::getWarehouseId, target)
                .eq(ShopOrderItem::getId, line.getId()));
        return target;
    }

    private ShopOrderPaidVo buildVo(String orderNo, int status, ShopErpSaleOutbound doc, boolean created) {
        ShopOrderPaidVo vo = new ShopOrderPaidVo();
        vo.setOrderId(doc.getOrderId());
        vo.setOrderNo(orderNo);
        vo.setStatus(status);
        vo.setOutboundId(doc.getId());
        vo.setOutboundNo(doc.getOutboundNo());
        vo.setOutboundCreated(created);
        return vo;
    }
}
