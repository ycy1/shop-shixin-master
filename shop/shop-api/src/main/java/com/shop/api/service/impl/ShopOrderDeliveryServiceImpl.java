package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopOrderDeliveryService;
import com.shop.entity.ShopConsumerAddress;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopDelivery;
import com.shop.entity.ShopDeliveryFeeRule;
import com.shop.entity.SysOrder;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.ShopErpSaleOutboundMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopDeliveryFeeRuleMapper;
import com.shop.mapper.ShopDeliveryMapper;
import com.shop.mapper.SysOrderMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.DeliveryFeeUtils;
import com.shop.utils.GeneratorNoUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单侧的配送单 服务实现
 *
 * 取数口径（和这一轮之前的"出库页手工生成"是同一套，只是提前到了下单那一刻、也没了人工那一步）：
 * · 重量 = Σ(shop_product_sku.weight × shop_order_item.quantity)，按**这一条规格**的单件重量汇总，
 *   没称过的行不参与汇总，
 *   一行都没称过就是空（不是 0 —— 0 会让"这一单不要重量费"看起来像规则算出来的结论）；
 * · 发货点 = 这一单第一个定了仓的商品行那一家仓库（shop_order_item.warehouse_id），
 *   一单一配送，几行分属几家仓的本来就只能拼出一条配送，先按第一行的那一家发；
 * · 收件点 = 这一单选的那条收货地址（sys_order.address_id 背后那一条），
 *   被删了或这一单本来没选地址才回落到订单快照 + 买家档案；
 * · 距离 = 两个点之间的球面直线距离（ShopDeliveryMapper#selectDistanceKm，和分仓同一个算法）；
 * · 配送类型 = 这两个点是否同城（两边 area_code 的市级那段一样就是 1 平台配送，不一样就是 3 快递发货）；
 * · 配送费 = 「这一档类型里启用、优先级最高」那条规则（ShopDeliveryFeeRuleMapper#selectTopRule）
 *   喂给 DeliveryFeeUtils 算出来的那一个数。
 *
 * 为什么写在 shop-api：这一轮的配送单是**统一下单**和**支付回调**这两步各动一次的，
 * 而 shop-api 只依赖 shop-commom 的 mapper，摸不到后台那套 service（反过来 shop-admin 依赖 shop-api，
 * 更不可能让 api 去调它）。算钱那一段因此也搬到了 commom 的 DeliveryFeeUtils，两边共用一份。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderDeliveryServiceImpl implements ShopOrderDeliveryService {

    private final ShopDeliveryMapper shopDeliveryMapper;

    /** 重量在这一条规格上（shop_product_sku.weight），订单明细只抄了价格、名称和规格文本 */
    private final ShopProductSkuMapper shopProductSkuMapper;

    /** 发货那一组（发货人/电话/地址/坐标）抄这一家仓库的档案 */
    private final ShopErpWarehouseMapper shopErpWarehouseMapper;

    /** 收件那一组优先读这一条，姓名/电话/地址/坐标全以它为准 */
    private final ShopConsumerAddressMapper shopConsumerAddressMapper;

    /** 这一单没选收货地址（或那条已被删）时的兜底：买家档案上的姓名/电话/地址/坐标 */
    private final SysUserMapper sysUserMapper;

    /** 挑规则那一次查库在这儿的 mapper 里，加钱那一段在 DeliveryFeeUtils（两条路共用同一个算法） */
    private final ShopDeliveryFeeRuleMapper shopDeliveryFeeRuleMapper;

    /** 补建老订单那一条配送单时要按订单把明细捞回来 */
    private final ShopOrderItemMapper shopOrderItemMapper;

    /** 取消订单那一趟要按订单号核对配送单绑的是哪一头（表上没有单独的订单列） */
    private final SysOrderMapper sysOrderMapper;

    /** 同上：这一单名下已经换绑过的配送单，槽里装的是出库单那两个值，得先把出库单捞出来才配得上号 */
    private final ShopErpSaleOutboundMapper shopErpSaleOutboundMapper;

    /** 配送方式（字典 delivery_type）：1 平台配送 —— 发货仓和收货地址同城时是它（和计价规则挑的是同一档类型） */
    private static final int DELIVERY_TYPE_PLATFORM = 1;

    /**
     * 配送方式（字典 delivery_type）：3 快递发货 —— 两个点不同城时切成它。
     * 这一路以前恒是 1，于是上海的仓寄深圳也要按"同城每公里加价"那套乘到底（直线距离 1200 公里 ⇒ 运费上万）；
     * 类型跟着是否同城走之后，跨城的单子挑的是「3 快递发货」那一档规则（后台没配这一档启用规则时是 0 元，
     * 口径同 DeliveryFeeUtils 里"一条启用规则都没有"那一句）。
     */
    private static final int DELIVERY_TYPE_EXPRESS = 3;

    /** 派单方式（字典 delivery_assign_type）：2 抢单 —— 系统建的这一路默认就是它（2026-09-22 他的口径）：
     *  这一条从一开始就没有指名的人，「确认」之后进的是送货员的抢单池，谁先点谁送 */
    private static final int ASSIGN_TYPE_GRAB = 2;

    /** 出库来源（字典 sale_outbound_source）：1 线上购买 —— 只有这一路的出库单 order_id 才是 sys_order.id */
    private static final int OUTBOUND_SOURCE_ONLINE = 1;

    /** 配送状态（字典 delivery_status）：0 待派单 —— 下单建出来就是这一档，要等商品出库页点「确认」才进抢单池 */
    private static final int STATUS_WAIT_ASSIGN = 0;

    /** 配送状态（字典 delivery_status）：1 待接单 —— 点「确认」之后进这一档，送货员才看得见 */
    private static final int STATUS_WAIT_ACCEPT = 1;

    /** 配送状态（字典 delivery_status）：5 已取消 —— 来源订单没了就是这一档，行留着备查 */
    private static final int STATUS_CANCELLED = 5;

    /** 经纬度值域：超出就别拿去算距离了（ST_Distance_Sphere 会直接抛库里的 range 异常） */
    private static final BigDecimal LNG_RANGE = new BigDecimal("180");

    private static final BigDecimal LAT_RANGE = new BigDecimal("90");

    @Override
    public ShopDelivery buildDraft(SysOrder order, List<ShopOrderItem> items, BigDecimal goodsAmount) {
        ShopDelivery delivery = new ShopDelivery();
        delivery.setDeliveryNo(GeneratorNoUtils.getDeliveryNo());
        delivery.setStatus(STATUS_WAIT_ASSIGN);
        delivery.setTipFee(BigDecimal.ZERO);
        // 派单方式默认「2 抢单」（2026-09-22 他一句"下单时生成的配送单 配送方式 默认抢单"）：
        // 系统建的这一条从出生起就没有指名的人，「确认」之后进的就是送货员的抢单池，谁先点谁送，
        // 所以这一格不是"还没定"而是"定成抢单"。assign_time 仍然留 NULL —— 那一格记的是点「确认」放它进池子的时刻。
        // 预计时长和预计送达更晚：那两格要按接单那个送货员的交通工具才算（ShopDeliveryAcceptServiceImpl）
        delivery.setAssignType(ASSIGN_TYPE_GRAB);
        delivery.setWeight(sumWeight(items));
        String receiverAreaCode = applyReceiver(delivery, order);
        String senderAreaCode = applySender(delivery, items);
        // 类型要在挑规则之前定：selectTopRule 是按 delivery_type 分桶的，先定了桶才知道用哪条规则计价
        delivery.setDeliveryType(sameCity(senderAreaCode, receiverAreaCode) ? DELIVERY_TYPE_PLATFORM : DELIVERY_TYPE_EXPRESS);
        fillDistance(delivery);
        fillDeliveryFee(delivery, goodsAmount);
        return delivery;
    }

    /**
     * 下单那一刻落库：outbound_id / outbound_no 这一槽里装的还是**订单**的那两个值
     * （这一会儿出库单根本没生成，他那句"直接使用出库单号和出库id，这时候出库单还没有生成"），
     * 支付回调再把它原地换成出库单的（{@link #rebindToOutbound}）。
     */
    @Override
    public void createOnOrder(SysOrder order, ShopDelivery draft) {
        draft.setOutboundId(order.getId());
        draft.setOutboundNo(order.getOrderNo());
        try {
            shopDeliveryMapper.insert(draft);
        } catch (DuplicateKeyException e) {
            // 只剩 uk_delivery_no 会撞了：outbound_id 上那道唯一键这一轮删的（同一个数会先被一张订单占着、
            // 再被另一张真正的出库单占着，留着它下单会莫名其妙失败）。
            // 单号是时间戳 + 三位随机，撞上的话整单回滚重来比改号简单
            throw new ServiceException("配送单号生成冲突，请重新提交订单");
        }
    }

    @Override
    public void rebindToOutbound(SysOrder order, ShopErpSaleOutbound outbound) {
        if (order == null || outbound == null) {
            return;
        }
        ShopDelivery delivery = findByBinding(order.getId(), order.getOrderNo());
        List<ShopOrderItem> items = shopOrderItemMapper.selectList(new LambdaQueryWrapper<ShopOrderItem>()
                .eq(ShopOrderItem::getOrderId, order.getId())
                .orderByAsc(ShopOrderItem::getId));
        if (delivery == null) {
            // 统一下单建配送单这套上线之前下的单：钱已经收了，这一会儿才补上一条，补出来直接绑在出库单上
            // （金额那几格按货值现算，和下单那一刻是同一口径）
            ShopDelivery draft = buildDraft(order, items, paidGoodsAmount(items));
            bind(draft, outbound, items);
            return;
        }
        if (delivery.getStatus() == null || delivery.getStatus() != STATUS_WAIT_ASSIGN) {
            // 已经确认过（1 待接单及以后）或者是已取消的那一条不再动：那边已经有人在跑这一趟了
            return;
        }
        bind(delivery, outbound, items);
    }

    /**
     * 来源订单没了：这一单名下**还停在 0 待派单 / 1 待接单**的那一条置成 5 已取消。
     *
     * 找它要顺着两个槽找：还没换绑的那一条装的是订单 id + 订单号，已经换绑过的装的是出库单 id + 出库单号，
     * 表上没有单独的订单列，所以后者只能先把这一单名下的出库单捞出来再按那两个值配（来源不是 1 线上购买 的
     * 那张配的是采购单，不在这条路上）。
     *
     * 为什么连 1 也一起收：1 待接单是已经在抢单池里挂着的了，订单没了却还让送货员抢得到，
     * 抢到了才发现没货可取 —— 所以取消要把它一并捞回来。已经 2 已接单及以后的不归这里动
     *（真在路上就得到「配送管理」里改 6 异常 或人工取消，那边看得见送货员）。
     * 查不到、或者那几条都过了这两档，都安静地什么都不做：取消一张订单不该被它的配送单卡住。
     */
    @Override
    public void cancelByOrder(Long orderId, String reason) {
        if (orderId == null) {
            return;
        }
        List<ShopDelivery> pending = new ArrayList<>();
        SysOrder order = sysOrderMapper.selectById(orderId);
        if (order != null) {
            ShopDelivery notRebound = findCancellable(order.getId(), order.getOrderNo());
            if (notRebound != null) {
                pending.add(notRebound);
            }
        }
        for (ShopErpSaleOutbound outbound : shopErpSaleOutboundMapper.selectList(
                new LambdaQueryWrapper<ShopErpSaleOutbound>()
                        .eq(ShopErpSaleOutbound::getOrderId, orderId)
                        .eq(ShopErpSaleOutbound::getOutboundSource, OUTBOUND_SOURCE_ONLINE))) {
            ShopDelivery rebound = findCancellable(outbound.getId(), outbound.getOutboundNo());
            if (rebound != null) {
                pending.add(rebound);
            }
        }
        for (ShopDelivery delivery : pending) {
            delivery.setStatus(STATUS_CANCELLED);
            delivery.setCancelTime(LocalDateTime.now());
            delivery.setCancelReason(StringUtils.defaultIfBlank(reason, "来源订单已取消/关闭"));
            // 派单那两格一起清掉：抢单池按 status 筛，这两格只是备查，留着会让人以为真派给过谁
            delivery.setAssignType(null);
            delivery.setAssignTime(null);
            shopDeliveryMapper.updateById(delivery);
        }
    }

    /**
     * 换绑真正落的那一句：把那一槽从订单的两个值覆盖成出库单的两个值，再按改派后的发货仓刷一遍发货点和距离，
     * 然后落库。配送费和 fee_rule_id 不在这里改（钱已经收了，见接口注释）。
     */
    private void bind(ShopDelivery delivery, ShopErpSaleOutbound outbound, List<ShopOrderItem> items) {
        ShopDelivery bound = findByBinding(outbound.getId(), outbound.getOutboundNo());
        if (bound != null && !bound.getId().equals(delivery.getId())) {
            // uk_outbound_id 删了（那一列现在两种 id 混着装，唯一键会误伤下单），"一出库单一配送"就靠这一句判：
            // 真撞上了就是这张单已经被别的配送单绑过了（或另一次回调抢过），整单回滚让人去查清楚，别默默留两条
            throw new ServiceException("出库单「" + outbound.getOutboundNo() + "」已经绑着配送单「"
                    + bound.getDeliveryNo() + "」，不再重复换绑，请到「配送管理」确认");
        }
        delivery.setOutboundId(outbound.getId());
        delivery.setOutboundNo(outbound.getOutboundNo());
        applySender(delivery, items);
        fillDistance(delivery);
        try {
            if (delivery.getId() == null) {
                shopDeliveryMapper.insert(delivery);
            } else {
                shopDeliveryMapper.updateById(delivery);
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("配送单号生成冲突，请稍后重试：" + outbound.getOutboundNo());
        }
    }

    /**
     * 按绑定那一槽查配送单：**id 和号两个一起比**才算绑上了这一张。
     * 只比 id 会把"还没换绑、槽里装的是订单 id"的那一条错认成某个同号出库单的配送单（两张表的自增 id 必然撞）。
     */
    private ShopDelivery findByBinding(Long id, String no) {
        if (id == null || StringUtils.isBlank(no)) {
            return null;
        }
        return shopDeliveryMapper.selectOne(new LambdaQueryWrapper<ShopDelivery>()
                .eq(ShopDelivery::getOutboundId, id)
                .eq(ShopDelivery::getOutboundNo, no)
                .orderByAsc(ShopDelivery::getId)
                .last("LIMIT 1"));
    }

    /** 同上那一条，但只认还停在 0 待派单 / 1 待接单 的（过了这两档的已经在别人手上了，取消够不着） */
    private ShopDelivery findCancellable(Long id, String no) {
        ShopDelivery delivery = findByBinding(id, no);
        if (delivery == null || delivery.getStatus() == null
                || (delivery.getStatus() != STATUS_WAIT_ASSIGN && delivery.getStatus() != STATUS_WAIT_ACCEPT)) {
            return null;
        }
        return delivery;
    }

    /**
     * Σ(单件重量 × 购买数量)。重量在**这一条规格**上（shop_product_sku.weight），
     * 同一商品下几个规格各自称过各自的，所以按 skuId 取，不能按商品取。
     * 一行都没称重就返回 null：
     * 配送费的重量那一档拿 null 是"跳过"，拿 0 是"这单不超重"，两者结论不一样，别糊过去。
     */
    private BigDecimal sumWeight(List<ShopOrderItem> items) {
        Set<Long> skuIds = new HashSet<>();
        for (ShopOrderItem item : items) {
            if (item.getSkuId() != null) {
                skuIds.add(item.getSkuId());
            }
        }
        if (skuIds.isEmpty()) {
            return null;
        }
        Map<Long, BigDecimal> weightBySku = new HashMap<>();
        for (ShopProductSku sku : shopProductSkuMapper.selectBatchIds(skuIds)) {
            if (sku.getWeight() != null) {
                weightBySku.put(sku.getId(), sku.getWeight());
            }
        }
        if (weightBySku.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ShopOrderItem item : items) {
            BigDecimal unit = weightBySku.get(item.getSkuId());
            if (unit != null && item.getQuantity() != null) {
                total = total.add(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 收件那一组：这一单选的那条收货地址优先（下单就是按它分的仓），
     * 那一条被删了、或者这一单本来没选地址，才回落到订单上的姓名电话快照 + 买家档案现在的值。
     * 姓名/电话/地址三格是这一张表的必填列，凑不齐就不填（下面留空，配送管理里人工补）；
     * 坐标只认"两个都有"的那一边，只有经度没有纬度算不出距离，这一格就留空（距离为空 ⇒ 超里程那一档不参与计价）。
     *
     * 有地址时**地区一律取地址那一份**：它是货真正要寄去的地方，同城/异地判的是它，
     * 下单分仓用的也是它（那种地址没录坐标，只有地区 —— 这时候坐标留空、不拿买家档案里那个别处的点凑数，
     * 拿它算出来的里程跟这一趟要跑的路线无关，宁可不算超里程那一档）。
     *
     * 返回真正被采用的那一份 area_code，调用方拿它判是否同城。
     */
    private String applyReceiver(ShopDelivery delivery, SysOrder order) {
        ShopConsumerAddress address = order.getAddressId() == null ? null
                : shopConsumerAddressMapper.selectById(order.getAddressId());
        SysUser buyer = order.getUserId() == null ? null : sysUserMapper.selectById(order.getUserId());
        delivery.setReceiverName(firstNotBlank(address == null ? null : address.getReceiverName(),
                order.getUserName(), buyer == null ? null : buyer.getNickname(),
                buyer == null ? null : buyer.getUsername()));
        delivery.setReceiverPhone(firstNotBlank(address == null ? null : address.getReceiverPhone(),
                order.getMobile(), buyer == null ? null : buyer.getMobile()));
        String receiverAddress = address == null ? null : joinAddress(address.getAreaZh(), address.getDetail());
        if (StringUtils.isBlank(receiverAddress)) {
            receiverAddress = joinAddress(buyer == null ? null : buyer.getAreaZh(),
                    buyer == null ? null : buyer.getAddress());
        }
        delivery.setReceiverAddress(receiverAddress);
        if (address == null) {
            delivery.setReceiverLng(buyer == null ? null : buyer.getLongitude());
            delivery.setReceiverLat(buyer == null ? null : buyer.getLatitude());
            return buyer == null ? null : buyer.getAreaCode();
        }
        boolean addressHasPoint = address.getLongitude() != null && address.getLatitude() != null;
        delivery.setReceiverLng(addressHasPoint ? address.getLongitude() : null);
        delivery.setReceiverLat(addressHasPoint ? address.getLatitude() : null);
        return address.getAreaCode();
    }

    /**
     * 发货那一组抄仓库的联系人/电话/地址/经纬度（仓库这三样是必填），落到本单上就是快照：
     * 之后仓库改了地址也不回头刷历史单。取的是第一个定了仓的商品行那一家
     * （一单一配送，几行分属几家仓时只能挑一家）。
     * 那一家查不到（仓被删了/明细上没落仓）时这一组留空，距离跟着空 —— 不拦下单。
     *
     * 返回这一家仓库的 area_code（判是否同城用；挑不出仓就是 null）
     */
    private String applySender(ShopDelivery delivery, List<ShopOrderItem> items) {
        Set<Long> warehouseIds = new HashSet<>();
        for (ShopOrderItem item : items) {
            if (item.getWarehouseId() != null) {
                warehouseIds.add(item.getWarehouseId());
            }
        }
        if (warehouseIds.isEmpty()) {
            return null;
        }
        // selectBatchIds 回来的顺序不保证跟入参一致，所以还是按明细自己的顺序挑第一个查得到的那一家
        Map<Long, ShopErpWarehouse> warehouseById = new HashMap<>();
        for (ShopErpWarehouse warehouse : shopErpWarehouseMapper.selectBatchIds(warehouseIds)) {
            warehouseById.put(warehouse.getId(), warehouse);
        }
        ShopErpWarehouse warehouse = null;
        for (ShopOrderItem item : items) {
            warehouse = warehouseById.get(item.getWarehouseId());
            if (warehouse != null) {
                break;
            }
        }
        if (warehouse == null) {
            return null;
        }
        delivery.setSenderName(warehouse.getContactName());
        delivery.setSenderPhone(warehouse.getContactPhone());
        delivery.setSenderAddress(joinAddress(warehouse.getAreaZh(), warehouse.getAddress()));
        delivery.setSenderLng(warehouse.getLongitude());
        delivery.setSenderLat(warehouse.getLatitude());
        return warehouse.getAreaCode();
    }

    /**
     * 距离 = 发货点到收件点的球面直线距离（km，SQL 里 ROUND 到 2 位）。
     * 两个点缺任何一边就置空 —— 宁可为空也不留上一次算完的旧值，那比没有更误导人。
     * 预计时长和预计送达这一路不生成：那要按接单那个送货员的交通工具才算。
     */
    private void fillDistance(ShopDelivery delivery) {
        BigDecimal senderLng = delivery.getSenderLng();
        BigDecimal senderLat = delivery.getSenderLat();
        BigDecimal receiverLng = delivery.getReceiverLng();
        BigDecimal receiverLat = delivery.getReceiverLat();
        if (senderLng == null || senderLat == null || receiverLng == null || receiverLat == null) {
            delivery.setDistance(null);
            return;
        }
        // 超出值域的话 ST_Distance_Sphere 会直接抛 MySQL 的 range 异常，那样报错的人看不懂是哪一格填错了
        if (outOfRange(senderLng, senderLat) || outOfRange(receiverLng, receiverLat)) {
            delivery.setDistance(null);
            return;
        }
        delivery.setDistance(shopDeliveryMapper.selectDistanceKm(senderLng, senderLat, receiverLng, receiverLat));
    }

    /**
     * 是否同城：发货仓和收货地两边的 area_code 各取出**市级那一段**比一下（"310000,310100,310101" ⇒ 310100）。
     *
     * 任何一边拿不出市码就判不出同城，这种情况按同城处理、留 1 平台配送 —— 地区没录全是数据问题，
     * 不该因此把单子切到一条可能压根没配过的「3 快递发货」规则上（那一档没启用规则时配送费是 0，等于白送一趟）。
     */
    private boolean sameCity(String senderAreaCode, String receiverAreaCode) {
        String sender = cityCode(senderAreaCode);
        String receiver = cityCode(receiverAreaCode);
        return sender == null || receiver == null || sender.equals(receiver);
    }

    /** area_code 的第二段（市级代码）；只录到省或整格是空就返回 null，不拿省码冒充市码 */
    private String cityCode(String areaCode) {
        if (StringUtils.isBlank(areaCode)) {
            return null;
        }
        String[] parts = areaCode.split(",");
        return parts.length < 2 ? null : StringUtils.trimToNull(parts[1]);
    }

    /**
     * 配送费按规则生成，不给买家和前端碰：类型（同城/异地那一步定下来的）决定去哪一档里挑规则，
     * 怎么挑见 selectTopRule，哪几档相加见 DeliveryFeeUtils。
     * 一条启用的规则都没有时是 0、id 也是 NULL —— 不拦下单，去「配送费规则」配好就是以后的单按新价收，
     * 已经收过的这一单不回改（要改只能退款重下）。
     */
    private void fillDeliveryFee(ShopDelivery delivery, BigDecimal goodsAmount) {
        ShopDeliveryFeeRule rule = shopDeliveryFeeRuleMapper.selectTopRule(delivery.getDeliveryType());
        delivery.setFeeRuleId(rule == null ? null : rule.getId());
        delivery.setFeeRuleName(rule == null ? null : rule.getRuleName());
        delivery.setDeliveryFee(DeliveryFeeUtils.calculateFee(
                rule, delivery.getDistance(), delivery.getWeight(), goodsAmount));
    }

    /** 这一单的货值实付（Σ 各行小计 − Σ 各行优惠）：减免那一档看的是它，不是含了配送费的那个金额 */
    private BigDecimal paidGoodsAmount(List<ShopOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShopOrderItem item : items) {
            if (item.getTotalAmount() == null) {
                continue;
            }
            BigDecimal discount = item.getDiscountAmount() == null ? BigDecimal.ZERO : item.getDiscountAmount();
            total = total.add(item.getTotalAmount().subtract(discount));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean outOfRange(BigDecimal lng, BigDecimal lat) {
        return lng.abs().compareTo(LNG_RANGE) > 0 || lat.abs().compareTo(LAT_RANGE) > 0;
    }

    /** 地区中文 + 详细地址拼成一格（收件/发货两边同一口径），两边都空就返回空串 */
    private String joinAddress(String areaZh, String detail) {
        return (StringUtils.trimToEmpty(areaZh) + StringUtils.trimToEmpty(detail)).trim();
    }

    /** 第一个有值的：这一单填过的那份快照优先，缺了才回落到买家档案现在的值 */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
