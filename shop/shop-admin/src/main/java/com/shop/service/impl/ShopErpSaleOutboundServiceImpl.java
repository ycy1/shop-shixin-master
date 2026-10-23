package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpInventoryLog;
import com.shop.entity.ShopErpPurchase;
import com.shop.entity.ShopErpPurchaseItem;
import com.shop.entity.ShopErpSale;
import com.shop.entity.ShopErpSaleItem;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopErpSaleOutboundItem;
import com.shop.entity.ShopOrderItem;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpPurchaseItemMapper;
import com.shop.mapper.ShopErpPurchaseMapper;
import com.shop.mapper.ShopErpSaleItemMapper;
import com.shop.mapper.ShopErpSaleMapper;
import com.shop.mapper.ShopErpSaleOutboundItemMapper;
import com.shop.mapper.ShopErpSaleOutboundMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.service.ShopErpInventoryService;
import com.shop.service.ShopErpSaleOutboundService;
import com.shop.service.ShopDeliveryService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 销售出库单 服务实现（形状照 ShopErpPurchaseInServiceImpl 的 receive 那一段，这里只说出库自己的口径）
 *
 * 1. 这一版没有「修改」：明细的行由来源单据决定，几条来源各有一条建单的路 ——
 *    1 线上购买 由支付回调按订单明细自动生成（见 shop-api 的 ShopOrderPaidService）；
 *    3 采购退货 由「采购单」页那颗「采购退货」自动生成（见 ShopErpPurchaseService#createPurchaseReturn，
 *    退的是采购单进来的货，所以那一路的行没有 order_item_id，主表的 order_id / order_no 存的是采购单）；
 *    2 销售出库 由本页那颗「新建」按对外销售单建（{@link #insert}，行 = 销售明细里还有余量的那几行，
 *    一路走的是后台手工建单，所以 order_item_id 同样为空、行按 规格(sku_id) + 仓库 记）；
 *    4 调拨出库 / 5 报损出库 / 6 其他 也从同一颗「新建」建，但没有来源单据（order_id / order_no 为 NULL），
 *    规格逐行手挑、也就没有额度那道上限。
 *    建错了就删掉重建（只有货一件都没出过的单子删得掉）。
 *    删除按来源分档（deleteByIds）：线上购买（source=1）那一路是买家已付款订单的发货凭证，一律不许删；
 *    其它来源（2/3/4/5/6）删了就是撤销一张错单，只受"出过货就不许删"那两道约束
 *    —— 没出过货就被删掉，等于把它占的那份额度还回来源单据（那边算上限时按未删除的单据汇总）。
 * 2. 仓库在**明细**上、不在主表：这一行从哪家仓出库是支付回调生成出库单时按商品的门店定的
 *    （门店仓优先，其次拿这一单的收货地址就近找有货的启用仓 —— 地址用不上才退回门店坐标，
 *    见 ShopOrderPaidServiceImpl#routeWarehouse），
 *    一张单的几行可能从几个仓分别发。所以出库是逐行按各自的仓减账，不像入库那样整单一个仓。
 * 3. quantity 是"这单登记要出多少"（来自订单明细的购买数量），outbound_quantity 是"实际出了多少"，
 *    差值就是还能出多少。真正动库存的是 outbound（列表上那个「出库」按钮）：按每行的本次出库数
 *    给库存减账，并顺手把统一下单锁住的那份可用量还回来（见 ShopErpInventoryServiceImpl#outbound）。
 * 4. 出库成本价按来源分两种：线上购买（source=1）的成本价和小计在**支付回调**那一刻就按订单明细的
 *    实付金额定死了（= 小计−优惠，单价按件摊），出库只是登记数量，那一格前端禁用、传了也不认，
 *    也绝不拿该仓库存的加权进货成本去覆盖它；其它来源由操作员填，出库时按"计划数 × 成本价"结转金额。
 *    两种都不改库存行的 cost_price（加权平均是进货时算的事）。
 * 5. 主表 status 不由人手工选：按明细进度算（一件没出 0 / 全出满 2 / 其余 1），
 *    outbound_time 第一次进 2 时补。
 * 6. 出库不碰 sys_order.status（那一列没有"已出库/已发货"，2026-09-21 起挪到明细行了）：
 *    出满哪几行，就把那几行 shop_order_item.ship_status 从 0 待发货推成 3 已出库
 *    （markOrderItemsOutbound）。一张订单几行可能从几个仓分批出，本来就是按行出的事。
 * 7. 配送单**不由这里建**（2026-09-21 第二轮改的口径）：它在**统一下单**那一刻就跟着订单建好了（0 待派单，
 *    重量/距离/配送费都在那一次定死，买家付的钱里已含配送费），支付回调再把它换绑到这张出库单上
 *    （shop-api 的 ShopOrderDeliveryService）。列表操作栏因此只剩两颗：
 *    「确认」把那条 0 待派单 推成 1 待接单（→ ShopDeliveryService#confirmDelivery，货真出齐了才让派），
 *    「配送单」看那一条的详情（DeliveryDetail，只读）。
 *    「生成配送单」只在这张单全部出库完之后、且它绑着的那条还是 0 待派单时出现 ——
 *    判据是列表 SQL 带回来的 deliveryId / deliveryNo / deliveryStatus（配送单没换绑前 outbound_id 是空的，
 *    也就是"钱还没收"，那时候本来也没什么可确认的）。
 *    这一颗只服务"把货发给买家"那一路：来源不是 1 线上购买 的单子（3 采购退货、4/5/6 手工建的那三路）
 *    没有收货人，也没有绑定的配送单，前端按来源把那两颗藏掉，
 *    服务端 confirmDelivery 拿不到绑定的配送单也一样报出去。
 *
 * 数据范围：**故意不按 user_id 收窄**（和采购单/仓库/入库单那三件套相反，理由见实体注释）。
 * 线上订单的单子由支付回调建，那一刻没有后台登录人、user_id 恒为 NULL，按它收窄的话列表就空了。
 * 所以 user_id 在这里只记"最后是谁点的出库"，删单和出库也都不判归属 —— 能进这个页面就能出这批货。
 */
@Service
@RequiredArgsConstructor
public class ShopErpSaleOutboundServiceImpl extends ServiceImpl<ShopErpSaleOutboundMapper, ShopErpSaleOutbound>
        implements ShopErpSaleOutboundService {

    /** 状态：待出库（字典 sale_outbound_status） */
    private static final int STATUS_PENDING = 0;

    /** 状态：部分出库 */
    private static final int STATUS_PARTIAL = 1;

    /** 状态：已出库（整单出满，第一次进入时补 outbound_time） */
    private static final int STATUS_OUT = 2;

    /** 采购单状态：待审核（字典 purchase_status）—— 没人审的单子谈不上退货，回写时跳过 */
    private static final int PURCHASE_STATUS_PENDING = 0;

    /** 采购单状态：已退货（这张单进来的货全部退回供应商，由退货出库单出满时回写，见 markPurchaseReturned） */
    private static final int PURCHASE_STATUS_RETURNED = 3;

    /** 销售单状态：已审核（字典 sale_status）—— 和「待出库」是仅有的两档能建出库单的 */
    private static final int SALE_STATUS_AUDITED = 1;

    /** 销售单状态：待出库（每个规格的销售量都被名下出库单登记满了，还没出完；由 refreshSaleStatus 推） */
    private static final int SALE_STATUS_WAIT_OUT = 2;

    /** 销售单状态：已出库（实出数追平销售量；由 refreshSaleStatus 推） */
    private static final int SALE_STATUS_SHIPPED = 3;

    private final ShopErpSaleOutboundItemMapper itemMapper;

    /** 库存落库的唯一入口：减多少件、还多少锁定、成本价取哪一版，全在它那边 */
    private final ShopErpInventoryService inventoryService;

    /** 只回写 shop_order_item.ship_status 这一列（货出满的那几行标成已出库），订单主表一律不碰 */
    private final ShopOrderItemMapper orderItemMapper;

    /** 「确认」那颗按钮真正的状态推进在那边（判据也写在那儿），这里只把出库单ID转过去 */
    private final ShopDeliveryService deliveryService;

    // 来源=3 采购退货 那一路出满时要回写来源采购单的状态（见 markPurchaseReturned）。
    // 只注 DAO、不注 ShopErpPurchaseService：那边建退货单注的是本表的两个 mapper，互相注服务就成了环
    private final ShopErpPurchaseMapper purchaseMapper;

    private final ShopErpPurchaseItemMapper purchaseItemMapper;

    // 来源=2 销售出库 那一路同理：建单要读销售明细、出库要回写销售单，也只注 DAO（ShopErpSaleServiceImpl
    // 反过来注了本表的服务，两边互相注服务就成了环）
    private final ShopErpSaleMapper saleMapper;

    private final ShopErpSaleItemMapper saleItemMapper;

    @Override
    public IPage<ShopErpSaleOutbound> selectPage(ShopErpSaleOutbound query) {
        if (query == null) {
            query = new ShopErpSaleOutbound();
        }
        // 不按账号类型收窄，也不覆盖请求体里的 userId（那一格是"按操作人筛"的查询条件，不是数据范围）
        // 关键字跨出库单号/订单号/备注/下单人/操作人联表，写条件用 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectSaleOutboundPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopErpSaleOutbound selectSaleOutboundById(Long id) {
        ShopErpSaleOutbound outbound = baseMapper.selectSaleOutboundById(id);
        if (outbound == null) {
            throw new ServiceException("出库单不存在");
        }
        outbound.setItems(itemMapper.selectItemsByOutboundId(id));
        return outbound;
    }

    /**
     * 一张销售单名下的全部出库单，每张连明细一起给（「销售单」页那颗「出库单」弹窗）
     *
     * 形状照抄 ShopErpPurchaseInServiceImpl#selectPurchaseInByPurchase，两处不一样：
     * 1. 不按登录账号收窄 —— 见接口注释，那张"刚建好还没出库"的单子在 ERP 账号眼里也必须看得见；
     * 2. 只捞来源=2 这一档（SQL 里钉死），销售单那边不会牵来线上订单和采购退货的单子。
     * 明细一次批量捞（XML 按 outbound_id, id 排，分组后每单的行序不变），不逐单查
     */
    @Override
    public List<ShopErpSaleOutbound> selectSaleOutboundBySale(Long saleId) {
        if (saleId == null) {
            throw new ServiceException("请选择销售单");
        }
        List<ShopErpSaleOutbound> docs = baseMapper.selectSaleOutboundsBySaleId(saleId);
        if (docs.isEmpty()) {
            return docs;
        }
        List<Long> ids = new ArrayList<>(docs.size());
        for (ShopErpSaleOutbound doc : docs) {
            ids.add(doc.getId());
        }
        Map<Long, List<ShopErpSaleOutboundItem>> itemsByOutbound = new HashMap<>();
        for (ShopErpSaleOutboundItem item : itemMapper.selectItemsByOutboundIds(ids)) {
            itemsByOutbound.computeIfAbsent(item.getOutboundId(), key -> new ArrayList<>()).add(item);
        }
        for (ShopErpSaleOutbound doc : docs) {
            doc.setItems(itemsByOutbound.getOrDefault(doc.getId(), new ArrayList<>()));
        }
        return docs;
    }

    /**
     * 「新建出库单」选定销售单后带出的明细候选：行就是销售明细那几行，每行多带一个
     * {@code availableQuantity}（= 这个规格的销售量 − 这张销售单名下其它 来源=2 出库单已登记的量，
     * 同一规格占几行时按行序分摊），弹窗里那一格「本次出库数」的上限就是它
     *
     * 和入库那边的 selectPurchaseItems 是镜像口径（那儿的"可入库数"= 采购量 − 其它入库单已登记的量）。
     * 这里刻意不按登录账号收窄：额度是那张销售单自己的数，别人已登记的量必须算进来，
     * 否则两个操作员各建一张就能把同一批货出两遍
     */
    @Override
    public List<ShopErpSaleItem> selectSaleItems(Long saleId, Long excludeOutboundId) {
        if (saleId == null) {
            throw new ServiceException("请选择销售单");
        }
        List<ShopErpSaleItem> items = saleItemMapper.selectItemsBySaleId(saleId);
        Map<Long, Integer> free = new HashMap<>();
        for (ShopErpSaleItem item : items) {
            free.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        Map<Long, Integer> registered = sumSaleRegisteredBySku(saleId, excludeOutboundId);
        for (Map.Entry<Long, Integer> entry : free.entrySet()) {
            entry.setValue(Math.max(0, entry.getValue() - registered.getOrDefault(entry.getKey(), 0)));
        }
        for (ShopErpSaleItem item : items) {
            int left = free.getOrDefault(item.getSkuId(), 0);
            int take = Math.min(left, item.getQuantity() == null ? 0 : item.getQuantity());
            item.setAvailableQuantity(take);
            free.put(item.getSkuId(), left - take);
        }
        return items;
    }

    /**
     * 「新建出库单」点了确定：建一张 0 待出库 的出库单，货要等在这一页点「出库」才真离仓
     *
     * 来源四档可选（弹窗里那个下拉就是这四个，默认 2）：
     * - 2 销售出库：按一张销售单建，明细从它带出来，每行受额度约束（见下），order_id/order_no 存这张销售单；
     * - 4 调拨出库 / 5 报损出库 / 6 其他：没有来源单据，规格逐行手挑（形状照 {@code ShopErpPurchaseInService}
     *   那页的非采购来源），所以不查销售单、没有额度这道闸，order_id/order_no 留 NULL，成本价必须自己填
     *   （销售那一路默认取销售单价，这三路没有默认值可取）。
     * 1 线上购买 由支付回调建、3 采购退货 由采购单页那颗「采购退货」建，这两档从这一颗进来一律挡掉。
     *
     * 请求体只认 outboundSource + orderId（销售单）+ 每行的 (skuId, warehouseId, quantity, costPrice) + remark，
     * 单号、总数、总金额、状态、order_no 一律服务端算。
     *
     * 可建几张：2 那一路的上限只有额度（销售量 − 名下已登记量），不看这张销售单名下已经有几张单子。
     * 占额度的是**登记数**不是实出数，否则两张待出库的单子能把同一批货出两遍。
     *
     * 这里刻意不判"仓里够不够货"：建出来的是待出库的单子，真扣账在出库那一步（那边逐行按
     * (规格, 仓库) 走 CAS，货不够会明确说是哪一行）。建单时就拦会把"先登记、货还在路上"堵死。
     *
     * user_id 不写：这一列在出库单上是"谁点的出库"，这张单此刻还没出库（那个动作会补上）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insert(ShopErpSaleOutbound form) {
        if (form == null) {
            throw new ServiceException("请填写出库来源和出库明细");
        }
        Integer source = form.getOutboundSource();
        if (source == null) {
            throw new ServiceException("请选择出库来源");
        }
        if (source < ShopErpSaleOutbound.SOURCE_SALE_OUTBOUND || source > ShopErpSaleOutbound.SOURCE_OTHER
                || source == ShopErpSaleOutbound.SOURCE_PURCHASE_RETURN) {
            // 这一颗只建 2/4/5/6 四路：1 由支付回调建、3 由「采购退货」建，都不是从这儿进来的
            throw new ServiceException("「新建出库单」只能建来源为「销售出库 / 调拨出库 / 报损出库 / 其他」的单据");
        }
        boolean fromSale = source == ShopErpSaleOutbound.SOURCE_SALE_OUTBOUND;
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new ServiceException("请至少选择一件要出库的商品");
        }
        // 非销售那三路不认请求体里的销售单：这一格本来就该是空的（前端换了来源就把它清掉）
        ShopErpSale sale = null;
        Map<Long, SaleLine> lines = Map.of();
        if (fromSale) {
            if (form.getOrderId() == null) {
                throw new ServiceException("出库来源是「销售出库」时请选择销售单");
            }
            sale = saleMapper.selectById(form.getOrderId());
            if (sale == null) {
                throw new ServiceException("销售单不存在");
            }
            Integer saleStatus = sale.getStatus();
            if (saleStatus == null
                    || (saleStatus != SALE_STATUS_AUDITED && saleStatus != SALE_STATUS_WAIT_OUT)) {
                throw new ServiceException("销售单「" + sale.getSaleNo() + "」当前状态不能出库，只有「已审核 / 待出库」的单据可以");
            }
            lines = saleLines(sale.getId());
        }

        // 同一规格塞两行直接拦：额度是按规格算的，分成两行就没法判谁先占（手工那几路没有额度，
        // 但同一规格拆两行也对不上"一个规格在一个仓一行"的记账形状，一并挡掉）
        Map<Long, ShopErpSaleOutboundItem> submitted = new LinkedHashMap<>();
        for (ShopErpSaleOutboundItem row : form.getItems()) {
            if (row.getSkuId() == null) {
                throw new ServiceException(fromSale
                        ? "出库明细里有没带规格的一行，请重新选择销售单" : "出库明细里有没选规格的一行，请先挑规格");
            }
            if (submitted.put(row.getSkuId(), row) != null) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 提交了两次，同一规格只能占一行");
            }
        }

        List<ShopErpSaleOutboundItem> items = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, ShopErpSaleOutboundItem> entry : submitted.entrySet()) {
            SaleLine line = lines.get(entry.getKey());
            if (fromSale && line == null) {
                throw new ServiceException("规格 #" + entry.getKey() + " 不在这张销售单里，无法出库");
            }
            ShopErpSaleOutboundItem row = entry.getValue();
            // 报错要点名这一行：销售那一路取销售明细的商品名，手工加的那一行用前端带过来的名称（非本表列）
            String name = line == null
                    ? StringUtils.defaultIfBlank(row.getProductName(), "规格 #" + entry.getKey())
                    : line.displayName();
            Integer quantity = row.getQuantity();
            if (quantity == null || quantity <= 0) {
                throw new ServiceException("请填写「" + name + "」的出库数量");
            }
            if (line != null && quantity > line.available()) {
                throw new ServiceException("「" + name + "」这张销售单共销售 " + line.quantity
                        + " 件、已登记出库 " + line.registered + " 件，本次最多还能出 " + line.available() + " 件");
            }
            if (row.getWarehouseId() == null) {
                throw new ServiceException("请选择「" + name + "」从哪个仓出库");
            }
            // 成本价：销售那一路默认取这一行规格的销售单价，前端填了就用填的；手工那三路没得默认，必填
            BigDecimal cost = row.getCostPrice() == null ? (line == null ? null : line.unitPrice())
                    : row.getCostPrice();
            if (cost == null) {
                throw new ServiceException("请填写「" + name + "」的出库成本价");
            }
            if (cost.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("「" + name + "」的出库成本价不能为负");
            }
            BigDecimal amount = cost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

            ShopErpSaleOutboundItem item = new ShopErpSaleOutboundItem();
            item.setSkuId(entry.getKey());
            item.setWarehouseId(row.getWarehouseId());
            // order_item_id 留空：这一行对不上任何订单明细（走的是后台建单，没经过下单锁定那一步）
            item.setQuantity(quantity);
            item.setOutboundQuantity(0);
            item.setCostPrice(cost);
            item.setAmount(amount);
            items.add(item);

            totalQuantity += quantity;
            totalAmount = totalAmount.add(amount);
        }

        ShopErpSaleOutbound doc = new ShopErpSaleOutbound();
        doc.setOutboundNo(GeneratorNoUtils.getSaleOutboundNo());
        doc.setOutboundSource(source);
        if (sale != null) {
            doc.setOrderId(sale.getId());
            doc.setOrderNo(sale.getSaleNo());
        }
        doc.setStatus(STATUS_PENDING);
        doc.setTotalQuantity(totalQuantity);
        doc.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        doc.setRemark(StringUtils.trimToNull(form.getRemark()));
        try {
            save(doc);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("出库单号生成冲突，请重试");
        }
        for (ShopErpSaleOutboundItem item : items) {
            item.setOutboundId(doc.getId());
            itemMapper.insert(item);
        }
        if (sale == null) {
            return doc.getId();
        }
        // 销售主表那一格「发货仓库」建单时可以不填（列注释就写着"出库时确定"）：这一批行都从同一个仓出
        // 就顺手补上，分了几个仓则不填（一列装不下几个答案）
        if (sale.getWarehouseId() == null) {
            Long warehouseId = items.get(0).getWarehouseId();
            boolean sameWarehouse = true;
            for (ShopErpSaleOutboundItem item : items) {
                if (!warehouseId.equals(item.getWarehouseId())) {
                    sameWarehouse = false;
                    break;
                }
            }
            if (sameWarehouse) {
                saleMapper.update(null, new LambdaUpdateWrapper<ShopErpSale>()
                        .set(ShopErpSale::getWarehouseId, warehouseId)
                        .eq(ShopErpSale::getId, sale.getId())
                        .isNull(ShopErpSale::getWarehouseId));
            }
        }
        refreshSaleStatus(sale.getId());
        return doc.getId();
    }

    /**
     * 按出库进度重算来源销售单的状态（建单 / 删单 / 出库动作 / 销售单改单各带一次）：
     * 实出数追平销售量 → 3 已出库；每个规格的销售量都被名下出库单登记满 → 2 待出库；
     * 还有规格没登记满 → 1 已审核（还能再建一张补货）。
     *
     * 0 待审核 / 4 已取消 不参与推导：没人审的单子谈不上出库，作废的更不该被后面的动作复活
     * （口径同 refreshPurchaseStatus）。写也只写 status/ship_time 两列，别拿整行实体回写盖掉别的列。
     */
    @Override
    public void refreshSaleStatus(Long saleId) {
        if (saleId == null) {
            return;
        }
        ShopErpSale sale = saleMapper.selectById(saleId);
        if (sale == null) {
            return;
        }
        Integer current = sale.getStatus();
        if (current == null || (current != SALE_STATUS_AUDITED && current != SALE_STATUS_WAIT_OUT
                && current != SALE_STATUS_SHIPPED)) {
            return;
        }
        Map<Long, Integer> planBySku = new HashMap<>();
        int totalShipped = 0;
        for (ShopErpSaleItem item : saleItemMapper.selectItemsBySaleId(saleId)) {
            planBySku.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(),
                    Integer::sum);
            totalShipped += item.getShippedQuantity() == null ? 0 : item.getShippedQuantity();
        }
        Map<Long, Integer> registeredBySku = sumSaleRegisteredBySku(saleId, null);
        int totalPlan = 0;
        boolean allRegistered = true;
        for (Map.Entry<Long, Integer> entry : planBySku.entrySet()) {
            int plan = entry.getValue();
            totalPlan += plan;
            if (registeredBySku.getOrDefault(entry.getKey(), 0) < plan) {
                allRegistered = false;
            }
        }
        int target;
        if (totalPlan > 0 && totalShipped >= totalPlan) {
            target = SALE_STATUS_SHIPPED;
        } else {
            target = allRegistered ? SALE_STATUS_WAIT_OUT : SALE_STATUS_AUDITED;
        }
        if (target == current) {
            return;
        }
        boolean needShipTime = target == SALE_STATUS_SHIPPED && sale.getShipTime() == null;
        saleMapper.update(null, new LambdaUpdateWrapper<ShopErpSale>()
                .set(ShopErpSale::getStatus, target)
                .set(needShipTime, ShopErpSale::getShipTime, LocalDateTime.now())
                .eq(ShopErpSale::getId, saleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<ShopErpSaleOutbound> docs = listByIds(ids);
        // 删掉的是一张还没出货的销售出库单 → 它占的那份额度要还给销售单（状态可能从 2 待出库 退回 1 已审核）
        Set<Long> saleIds = new LinkedHashSet<>();
        for (ShopErpSaleOutbound doc : docs) {
            // 线上购买那一路（source=1，支付回调建的单）一律不许删：这张单就是买家那笔已付款订单的发货凭证，
            // 删掉之后订单还在（sys_order=1 已支付、库存还锁着），却再没有单据能出库，
            // 回调那道"一单一出库单"的闸也不会再给它补一张 —— 等于把订单卡在已付款发不出去。
            // 后台手工建的（2 销售出库）那一路是操作员自己建的，删了就是撤销一张错单，往下只看有没有出过货
            if (doc.getOutboundSource() != null
                    && doc.getOutboundSource() == ShopErpSaleOutbound.SOURCE_ONLINE_ORDER) {
                throw new ServiceException("出库单「" + doc.getOutboundNo()
                        + "」来源是「线上购买」（买家已付款的订单），不能删除");
            }
            if (doc.getOutboundSource() != null
                    && doc.getOutboundSource() == ShopErpSaleOutbound.SOURCE_SALE_OUTBOUND
                    && doc.getOrderId() != null) {
                saleIds.add(doc.getOrderId());
            }
            // 已出库（status=2）的整单不许删，口径同「修改」那道闸（这一版没有修改，闸就在删除这里）：
            // 货已经按这张单减过库存、流水也记了，删单不会把货退回仓里，删完就是一笔对不上的死账
            if (doc.getStatus() != null && doc.getStatus() == STATUS_OUT) {
                throw new ServiceException("出库单「" + doc.getOutboundNo()
                        + "」已全部出库，不能删除；要调整数量请去库存里冲账");
            }
        }
        // 出过一点货的单子同样不许删（这一条管的是"部分出库"那些 —— 状态还没到 2，但已经有行进过账）
        for (ShopErpSaleOutboundItem item : itemMapper.selectList(new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                .in(ShopErpSaleOutboundItem::getOutboundId, ids))) {
            Integer out = item.getOutboundQuantity();
            if (out != null && out > 0) {
                throw new ServiceException("这张单已经出库 " + out + " 件（规格 #" + item.getSkuId()
                        + "），不能删除；货出错了要去库存里冲账");
            }
        }
        removeByIds(ids);
        // 明细跟着一起软删：留着孤儿行，以后按 outbound_id 统计出库量就会把已删除单的行算进去
        itemMapper.delete(new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                .in(ShopErpSaleOutboundItem::getOutboundId, ids));
        // 额度还回去之后刷一次来源销售单的状态（删的都是没出过货的，实出数没变，变的是登记量）
        for (Long saleId : saleIds) {
            refreshSaleStatus(saleId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outbound(ShopErpSaleOutbound form) {
        if (form == null || form.getId() == null) {
            throw new ServiceException("出库单ID不能为空");
        }
        // 直接拿库里的整单来改（不用请求体）：user_id / remark 这些列是 IGNORED，
        // 用稀疏实体更新会把没带的列写成 NULL
        ShopErpSaleOutbound doc = getById(form.getId());
        if (doc == null) {
            throw new ServiceException("出库单不存在");
        }
        if (doc.getStatus() != null && doc.getStatus() == STATUS_OUT) {
            throw new ServiceException("这张出库单已经全部出库完成，不能再出库；要调整数量请去库存里冲账");
        }
        List<ShopErpSaleOutboundItem> rows = itemMapper.selectItemsByOutboundId(doc.getId());
        if (rows.isEmpty()) {
            throw new ServiceException("这张出库单没有明细，无法出库");
        }
        // 请求体只认 (明细行 id, 本次出库数, 出库成本价)，数量/规格/仓库全以库里的行为准
        Map<Long, ShopErpSaleOutboundItem> patches = new HashMap<>();
        if (form.getItems() != null) {
            for (ShopErpSaleOutboundItem line : form.getItems()) {
                if (line.getId() == null) {
                    throw new ServiceException("出库明细缺少行ID，请重新打开出库页再提交");
                }
                if (patches.put(line.getId(), line) != null) {
                    throw new ServiceException("同一条出库明细提交了两次，请刷新出库页");
                }
            }
        }
        // 来源=1 线上购买：成本价和金额回调时就已经按订单实付金额定死了（下面不再改它们），
        // 而且这一行下单时锁过的可用量要还回去；
        // 其它来源（3 采购退货、后台手工建的那一路）没走过统一下单，仓里本来就没锁，还 0
        Integer source = doc.getOutboundSource();
        boolean online = source != null && source == ShopErpSaleOutbound.SOURCE_ONLINE_ORDER;
        // 来源=3 采购退货：货是退回供应商的，流水要记成"采购退货出库"那一档，
        // 不然库存日志那一页看过去全是"销售出库"，说不清这批货为什么离仓
        boolean purchaseReturn = source != null && source == ShopErpSaleOutbound.SOURCE_PURCHASE_RETURN;
        // 来源=2 销售出库：出的是对外销售单那几行，货离仓要累加回销售明细的 shipped_quantity
        boolean saleOut = source != null && source == ShopErpSaleOutbound.SOURCE_SALE_OUTBOUND;
        // 库存流水的变动类型一律按来源分档：1 线上购买 → 3 下单出库、2 销售出库 → 2、3 采购退货 → 9
        //（2026-09-22 他："如果出库单来源是 线上购买 则记录库存日志的变动类型为 3 下单出库"；
        // 4 调拨出库 / 5 报损出库 / 6 其他 这三口按 2 销售出库 记是他当天定的"后续实现"——
        // 等真接上调拨单/盘点单那条链路时才拆成 6 调拨出库 / 8 盘亏，改的就是这个三元式）
        int changeType = purchaseReturn ? ShopErpInventoryLog.CHANGE_TYPE_PURCHASE_RETURN_OUT
                : online ? ShopErpInventoryLog.CHANGE_TYPE_ONLINE_OUT
                : ShopErpInventoryLog.CHANGE_TYPE_SALE_OUT;
        Long operatorId = StpUtil.getLoginIdAsLong();
        int totalThisTime = 0;
        // 这次动到的订单明细行，出库完按行判"出满没有"（非线上来源的行没有 orderItemId，本来就不进这个集合）
        Set<Long> touchedOrderItemIds = new LinkedHashSet<>();
        // 来源=2 那一路按规格汇总这次的实出数，循环完再摊回销售明细的行（同一规格可能占几行）
        Map<Long, Integer> shippedBySku = new HashMap<>();
        for (ShopErpSaleOutboundItem row : rows) {
            ShopErpSaleOutboundItem patch = patches.remove(row.getId());
            if (patch == null) {
                // 没提交的行 = 这次不出，原样留着（分几次出就靠这一点）
                continue;
            }
            int quantity = row.getQuantity() == null ? 0 : row.getQuantity();
            int already = row.getOutboundQuantity() == null ? 0 : row.getOutboundQuantity();
            int thisTime = patch.getThisQuantity() == null ? 0 : patch.getThisQuantity();
            if (thisTime < 0) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 的本次出库数不能为负");
            }
            if (thisTime == 0) {
                continue;
            }
            if (thisTime > quantity - already) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 这单计划出库 " + quantity
                        + " 件、已出库 " + already + " 件，本次最多还能出 " + Math.max(quantity - already, 0) + " 件");
            }
            if (row.getWarehouseId() == null) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 这一行没有分到仓库，无法确定从哪个仓出库");
            }
            // 库存减账：总库存扣掉本次出库数，同时把下单锁住的那份可用量还回来（还多少由上面按来源定），
            // 顺带记一行库存流水（变动类型按来源分档：线上订单 3 下单出库、按销售单开的 2 销售出库、
            // 退给供应商 9 采购退货出库，数量都是负的）。库存行按 (sku_id, warehouse_id) 定位，
            // 「这一条规格在这家仓还够不够卖」的上限判据就在它那边（CAS）。
            // 它返回的该仓加权成本价这里不接：线上订单结的是订单实付价（回调就写死了），
            // 其它来源的成本价由操作员填，两种情况都不该被进货成本覆盖
            inventoryService.outbound(row.getSkuId(), row.getWarehouseId(),
                    thisTime, online ? thisTime : 0, operatorId,
                    changeType, doc.getId(), doc.getOutboundNo());
            BigDecimal cost;
            BigDecimal amount;
            if (online) {
                // 线上购买（source=1）：成本价和小计是支付回调按订单明细实付金额写进去的，出库只登记数量，
                // 前端传什么成本价都不认；金额也就沿用行上已有的那个数，不按"计划数 × 单价"重乘
                //（单价是实付金额摊到每件的，重乘会把分位舍入的差再算一遍出来）
                cost = row.getCostPrice();
                amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            } else {
                cost = patch.getCostPrice() != null ? patch.getCostPrice() : row.getCostPrice();
                if (cost == null || cost.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ServiceException("规格 #" + row.getSkuId() + " 的出库成本价不能为空且不能为负");
                }
                amount = cost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            }
            // 这行的进度累加：成本价和小计写回上面定下来的那一对值（线上那一路写回去的就是原值）
            ShopErpSaleOutboundItem itemPatch = new ShopErpSaleOutboundItem();
            itemPatch.setId(row.getId());
            itemPatch.setOutboundQuantity(already + thisTime);
            itemPatch.setCostPrice(cost);
            itemPatch.setAmount(amount);
            itemMapper.updateById(itemPatch);

            row.setOutboundQuantity(already + thisTime);
            row.setCostPrice(cost);
            row.setAmount(amount);
            if (row.getOrderItemId() != null) {
                touchedOrderItemIds.add(row.getOrderItemId());
            }
            if (saleOut) {
                shippedBySku.merge(row.getSkuId(), thisTime, Integer::sum);
            }
            totalThisTime += thisTime;
        }
        if (!patches.isEmpty()) {
            throw new ServiceException("提交的明细里有不属于这张出库单的行，请刷新出库页后重试");
        }
        if (totalThisTime == 0) {
            throw new ServiceException("本次出库数全部为 0，没有要出库的商品");
        }
        // 主表：总额按各行最新成本价重算，状态按进度往前推，操作人记成点这个按钮的人。
        // applyOutboundTime 的第二个参数要的是"改之前库里的出库时间"，这里 doc 就是刚读出来的那行
        //（其它字段上面已经改过，outboundTime 一直没动），所以传自己
        doc.setItems(rows);
        doc.setUserId(operatorId);
        recalculate(doc);
        doc.setStatus(progressStatus(rows));
        applyOutboundTime(doc, doc);
        updateById(doc);
        markOrderItemsOutbound(touchedOrderItemIds);
        if (purchaseReturn && doc.getStatus() != null && doc.getStatus() == STATUS_OUT) {
            // 整张退货单出满了，才有资格去问一句"这张采购单进来的货是不是全退回供应商了"
            markPurchaseReturned(doc.getOrderId());
        }
        // 来源=2 销售出库：先把这次的实出数摊回销售明细的 shipped_quantity，再按整单进度推销售单的状态
        // （出满没有是 refreshSaleStatus 那一处判的，这里不重复）
        if (saleOut) {
            addSaleShipped(doc.getOrderId(), shippedBySku);
            refreshSaleStatus(doc.getOrderId());
        }
    }

    /**
     * 「确认」：把这张出库单绑着的那条配送单从「0 待派单」推成「1 待接单」，交回配送那边判
     * （出满没有、绑没绑上配送单、那条还是不是 0 —— 全在 ShopDeliveryService#confirmDelivery）。
     *
     * 这里不再自己判一遍，也不带表单：配送单是统一下单建的、支付回调换绑到这张单上的，
     * 这颗按钮改的只有状态和派单那几格 —— "该不该点"和"点了能不能过"是同一件事，说两处必对不上。
     */
    @Override
    public void confirmDelivery(Long id) {
        deliveryService.confirmDelivery(id);
    }

    /**
     * 按明细重算"服务端说了算"的数：每行 amount 已在出库时算过，这里只累加主表两个总数
     * （明细没被改过时结果不变，改过成本价的那几行会一起把总额带正）。
     * 状态不在这里算（那是 progressStatus 的事）
     */
    private void recalculate(ShopErpSaleOutbound doc) {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (ShopErpSaleOutboundItem item : doc.getItems()) {
            quantity += item.getQuantity() == null ? 0 : item.getQuantity();
            amount = amount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
        }
        doc.setTotalQuantity(quantity);
        doc.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** 按明细进度算主表状态：一件都没出 0 待出库；全部出满 2 已出库；其余 1 部分出库 */
    private int progressStatus(List<ShopErpSaleOutboundItem> items) {
        int plan = 0;
        int out = 0;
        for (ShopErpSaleOutboundItem item : items) {
            int rowPlan = item.getQuantity() == null ? 0 : item.getQuantity();
            plan += rowPlan;
            out += Math.min(item.getOutboundQuantity() == null ? 0 : item.getOutboundQuantity(), rowPlan);
        }
        if (out <= 0) {
            return STATUS_PENDING;
        }
        return out >= plan ? STATUS_OUT : STATUS_PARTIAL;
    }

    /**
     * 状态第一次进入 2（已出库）时补 outbound_time，其余情况不动这一列。
     * outbound_time 用的是全局 not_empty（不是 IGNORED），null 就整列不进 UPDATE 的 SET，
     * 等于"已记过的出库时间不会被后面的操作改掉"（口径同入库单的 receive_time）
     */
    private void applyOutboundTime(ShopErpSaleOutbound doc, ShopErpSaleOutbound exists) {
        if (doc.getStatus() == null) {
            return;
        }
        LocalDateTime oldTime = exists == null ? null : exists.getOutboundTime();
        if (doc.getStatus() == STATUS_OUT && oldTime == null) {
            doc.setOutboundTime(LocalDateTime.now());
        }
    }

    /**
     * 把货出满的那几行订单明细标成「已出库」（ship_status 0 待发货 → 3 已出库）。
     *
     * 判"出满"要按**这一行**汇总它名下所有出库单，不能只看刚点的这一张：一张订单行可以拆成
     * 几张出库单分批出（回调建的单只是第一张），只看这一张会早早上标。
     * 汇总时把每行的已出库数夹到它的计划数以内（口径同 progressStatus），出过了头也不影响判断。
     *
     * 只从 0 待发货往上推：已经标成 1 已发货 / 2 已退货 / 4 部分退货 的行不往回拽
     * （退过货的行由退款回调接管状态，见 ShopOrderItem 的类注释）。
     * UPDATE 的 WHERE 再带一次 ship_status = 0，读到写之间要是被配送那边改过状态就以那边为准。
     */
    private void markOrderItemsOutbound(Set<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return;
        }
        List<ShopErpSaleOutboundItem> rows = itemMapper.selectList(
                new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                        .select(ShopErpSaleOutboundItem::getOrderItemId, ShopErpSaleOutboundItem::getQuantity,
                                ShopErpSaleOutboundItem::getOutboundQuantity)
                        .in(ShopErpSaleOutboundItem::getOrderItemId, orderItemIds));
        Map<Long, Integer> outByOrderItem = new HashMap<>();
        for (ShopErpSaleOutboundItem row : rows) {
            int plan = row.getQuantity() == null ? 0 : row.getQuantity();
            int out = row.getOutboundQuantity() == null ? 0 : row.getOutboundQuantity();
            outByOrderItem.merge(row.getOrderItemId(), Math.min(out, plan), Integer::sum);
        }
        for (ShopOrderItem line : orderItemMapper.selectBatchIds(orderItemIds)) {
            int plan = line.getQuantity() == null ? 0 : line.getQuantity();
            Integer out = outByOrderItem.get(line.getId());
            if (plan <= 0 || out == null || out < plan) {
                // 这一行还没出满，等下次出库再判
                continue;
            }
            if (line.getShipStatus() == null || line.getShipStatus() != ShopOrderItem.SHIP_STATUS_PENDING) {
                continue;
            }
            // 只写 ship_status 一列（同入库单回写采购单状态的写法）：明细行上还有金额、分仓这些列，
            // 拿半截实体回写等于把刚读到的快照覆盖回去
            orderItemMapper.update(null, new LambdaUpdateWrapper<ShopOrderItem>()
                    .set(ShopOrderItem::getShipStatus, ShopOrderItem.SHIP_STATUS_OUTBOUND)
                    .eq(ShopOrderItem::getId, line.getId())
                    .eq(ShopOrderItem::getShipStatus, ShopOrderItem.SHIP_STATUS_PENDING));
        }
    }

    /**
     * 退货单出满之后回写来源采购单的状态：这张采购单进来的货全部退回供应商 → 3 已退货。
     *
     * 判"退完没退完"要按规格逐个比，而且得把这张采购单名下**所有**退货单加起来看：可多次退货，
     * 最后那一张完全可能只退了其中几个规格。实退数取 min(计划数, 已出库数)（口径同 progressStatus，
     * 出过头也不影响判断），比的是采购明细的已入库数 —— 退货额度本来就是按那一列给的
     * （见 ShopErpPurchaseServiceImpl#returnLines）。
     *
     * 只往前推不往回拽：没退满原样留着（2 已入库 还是 2），0 待审核 谈不上退货所以不碰，
     * 已经是 3 的不用重算。UPDATE 的 WHERE 再带一次这两个条件，读到写之间被人改过就以那边为准。
     * 只写 status 一列（同上面回写订单明细的写法）：拿半截实体 updateById 会把联系人、时间那些列盖掉。
     */
    private void markPurchaseReturned(Long purchaseId) {
        if (purchaseId == null) {
            return;
        }
        ShopErpPurchase purchase = purchaseMapper.selectById(purchaseId);
        if (purchase == null) {
            // 来源采购单已经不在了：这一路建出来的退货单还能查出库存流水，状态没地方写，跳过就行
            return;
        }
        Integer status = purchase.getStatus();
        if (status != null && (status == PURCHASE_STATUS_PENDING || status == PURCHASE_STATUS_RETURNED)) {
            return;
        }
        Map<Long, Integer> receivedBySku = new HashMap<>();
        for (ShopErpPurchaseItem line : purchaseItemMapper.selectList(
                new LambdaQueryWrapper<ShopErpPurchaseItem>()
                        .select(ShopErpPurchaseItem::getSkuId, ShopErpPurchaseItem::getReceivedQuantity)
                        .eq(ShopErpPurchaseItem::getPurchaseId, purchaseId))) {
            receivedBySku.merge(line.getSkuId(),
                    line.getReceivedQuantity() == null ? 0 : line.getReceivedQuantity(), Integer::sum);
        }
        if (receivedBySku.isEmpty()) {
            return;
        }
        Map<Long, Integer> returnedBySku = sumReturnedBySku(purchaseId);
        for (Map.Entry<Long, Integer> entry : receivedBySku.entrySet()) {
            int received = entry.getValue();
            if (received <= 0) {
                // 一件都没入过库的规格退不了，也不算没退完（额度那边同样按已入库数给）
                continue;
            }
            Integer returned = returnedBySku.get(entry.getKey());
            if (returned == null || returned < received) {
                return;
            }
        }
        purchaseMapper.update(null, new LambdaUpdateWrapper<ShopErpPurchase>()
                .set(ShopErpPurchase::getStatus, PURCHASE_STATUS_RETURNED)
                .eq(ShopErpPurchase::getId, purchaseId)
                .ne(ShopErpPurchase::getStatus, PURCHASE_STATUS_RETURNED)
                .ne(ShopErpPurchase::getStatus, PURCHASE_STATUS_PENDING));
    }

    /**
     * 这张采购单名下所有退货单的实退数合计（按规格）：先捞那些 来源=3 的退货出库单，再按 outbound_id 汇总明细。
     * 两次查询、不联表 —— 一个采购单名下的退货单撑死几张（口径同采购退货额度那边算已登记数的那一段）
     */
    private Map<Long, Integer> sumReturnedBySku(Long purchaseId) {
        Map<Long, Integer> returned = new HashMap<>();
        List<ShopErpSaleOutbound> docs = baseMapper.selectList(new LambdaQueryWrapper<ShopErpSaleOutbound>()
                .select(ShopErpSaleOutbound::getId)
                .eq(ShopErpSaleOutbound::getOrderId, purchaseId)
                .eq(ShopErpSaleOutbound::getOutboundSource, ShopErpSaleOutbound.SOURCE_PURCHASE_RETURN));
        if (docs.isEmpty()) {
            return returned;
        }
        List<Long> docIds = new ArrayList<>(docs.size());
        for (ShopErpSaleOutbound doc : docs) {
            docIds.add(doc.getId());
        }
        for (ShopErpSaleOutboundItem item : itemMapper.selectList(
                new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                        .select(ShopErpSaleOutboundItem::getSkuId, ShopErpSaleOutboundItem::getQuantity,
                                ShopErpSaleOutboundItem::getOutboundQuantity)
                        .in(ShopErpSaleOutboundItem::getOutboundId, docIds))) {
            int plan = item.getQuantity() == null ? 0 : item.getQuantity();
            int out = item.getOutboundQuantity() == null ? 0 : item.getOutboundQuantity();
            returned.merge(item.getSkuId(), Math.min(out, plan), Integer::sum);
        }
        return returned;
    }

    /**
     * 这张销售单按规格汇总出来的出库额度（LinkedHashMap 保住销售明细的行序，弹窗里规格顺序和销售单一致）
     *
     * 两股数：这个规格的销售量合计（同一规格占几行时相加，顺带把加权销售单价算出来当成本价默认值）、
     * 以及这张单名下那些 来源=2 出库单已登记的量
     */
    private Map<Long, SaleLine> saleLines(Long saleId) {
        Map<Long, SaleLine> lines = new LinkedHashMap<>();
        for (ShopErpSaleItem item : saleItemMapper.selectItemsBySaleId(saleId)) {
            SaleLine line = lines.get(item.getSkuId());
            if (line == null) {
                line = new SaleLine(item.getSkuId());
                lines.put(item.getSkuId(), line);
            }
            line.quantity += item.getQuantity() == null ? 0 : item.getQuantity();
            line.price = line.price.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
            if (line.productName == null) {
                line.productName = item.getProductName();
            }
        }
        Map<Long, Integer> registered = sumSaleRegisteredBySku(saleId, null);
        for (Map.Entry<Long, SaleLine> entry : lines.entrySet()) {
            entry.getValue().registered = registered.getOrDefault(entry.getKey(), 0);
        }
        return lines;
    }

    /**
     * 这张销售单名下已登记的出库量（按规格汇总的**计划数 quantity**，不是实出数）
     *
     * 待出库的单子也得占额度，不然两张单子各按"还没出"登记一遍，加起来就把同一批货出了两遍。
     * 刻意不按登录账号收窄：这是那张销售单自己的额度（本表的数据范围也不按 user_id 收窄）。
     * excludeOutboundId 是"正在改的那一张"——这一版没有修改，调用方一律传 null，
     * 参数留着是和入库那边 sumReceivedByProduct 对齐口径
     */
    private Map<Long, Integer> sumSaleRegisteredBySku(Long saleId, Long excludeOutboundId) {
        Map<Long, Integer> registered = new HashMap<>();
        List<ShopErpSaleOutbound> docs = baseMapper.selectList(new LambdaQueryWrapper<ShopErpSaleOutbound>()
                .select(ShopErpSaleOutbound::getId)
                .eq(ShopErpSaleOutbound::getOrderId, saleId)
                .eq(ShopErpSaleOutbound::getOutboundSource, ShopErpSaleOutbound.SOURCE_SALE_OUTBOUND)
                .ne(excludeOutboundId != null, ShopErpSaleOutbound::getId, excludeOutboundId));
        if (docs.isEmpty()) {
            return registered;
        }
        List<Long> docIds = new ArrayList<>(docs.size());
        for (ShopErpSaleOutbound doc : docs) {
            docIds.add(doc.getId());
        }
        for (ShopErpSaleOutboundItem item : itemMapper.selectList(
                new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                        .select(ShopErpSaleOutboundItem::getSkuId, ShopErpSaleOutboundItem::getQuantity)
                        .in(ShopErpSaleOutboundItem::getOutboundId, docIds))) {
            registered.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        return registered;
    }

    /**
     * 把这次的实出数按规格摊回销售明细的 shipped_quantity（同规格几行按行序依次填满，每行不超过它的销售量）
     *
     * 只写明细、不动销售单的状态：状态是按整单进度推的，统一交给 refreshSaleStatus（那边一处判"跳级没有"），
     * 免得两处各写一套口径（同入库那边 addPurchaseReceived）
     */
    private void addSaleShipped(Long saleId, Map<Long, Integer> addBySku) {
        if (saleId == null || addBySku.isEmpty()) {
            return;
        }
        for (ShopErpSaleItem line : saleItemMapper.selectList(new LambdaQueryWrapper<ShopErpSaleItem>()
                .eq(ShopErpSaleItem::getSaleId, saleId)
                .orderByAsc(ShopErpSaleItem::getId))) {
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            int shipped = line.getShippedQuantity() == null ? 0 : line.getShippedQuantity();
            Integer left = addBySku.get(line.getSkuId());
            if (left == null || left <= 0) {
                continue;
            }
            int take = Math.min(left, Math.max(quantity - shipped, 0));
            if (take <= 0) {
                continue;
            }
            line.setShippedQuantity(shipped + take);
            addBySku.put(line.getSkuId(), left - take);
            // 只写这一列（同入库那边回写采购明细的写法）：拿半截实体以外的字段回写会把别的列盖掉
            ShopErpSaleItem itemPatch = new ShopErpSaleItem();
            itemPatch.setId(line.getId());
            itemPatch.setShippedQuantity(shipped + take);
            saleItemMapper.updateById(itemPatch);
        }
    }

    /** 出库额度的一行（只在「新建出库单」这一条链上用，不出服务、不落库） */
    private static final class SaleLine {

        private final Long skuId;
        private String productName;
        /** 销售量合计（Σ 明细 quantity） */
        private int quantity;
        /** 这张单名下出库单已登记的量合计 */
        private int registered;
        /** 这几行规格的销售金额合计（Σ 明细 amount），用来算加权销售单价 */
        private BigDecimal price = BigDecimal.ZERO;

        private SaleLine(Long skuId) {
            this.skuId = skuId;
        }

        /** 还能出多少 = 销售量 − 已登记量（负数按 0，出库单把额度占超了也不给负数） */
        private int available() {
            return Math.max(quantity - registered, 0);
        }

        /** 加权销售单价 = Σ 销售金额 ÷ Σ 销售数量（同一规格几行不同价时取这一版） */
        private BigDecimal unitPrice() {
            if (quantity <= 0) {
                return BigDecimal.ZERO;
            }
            return price.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }

        /** 报错文案里那个名称（名称仍是联表带出的商品名）：商品被删过联表就是空的，退到 规格 #skuId */
        private String displayName() {
            return StringUtils.isBlank(productName) ? "规格 #" + skuId : productName;
        }
    }
}
