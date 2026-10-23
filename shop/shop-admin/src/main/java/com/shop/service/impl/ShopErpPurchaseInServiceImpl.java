package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpPurchase;
import com.shop.entity.ShopErpPurchaseIn;
import com.shop.entity.ShopErpPurchaseInItem;
import com.shop.entity.ShopErpPurchaseItem;
import com.shop.entity.ShopErpInventoryLog;
import com.shop.entity.ShopErpWarehouse;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpPurchaseInItemMapper;
import com.shop.mapper.ShopErpPurchaseInMapper;
import com.shop.mapper.ShopErpPurchaseItemMapper;
import com.shop.mapper.ShopErpPurchaseMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopErpInventoryService;
import com.shop.service.ShopErpPurchaseInService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.utils.WarehouseUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库单 服务实现（主子表整单存的写法照 ShopErpPurchaseServiceImpl，这里只说入库单自己的口径）
 *
 * 1. supplier_id 不是表单列：它按 purchase_id 从来源采购单回填，所以入库单的供应商和采购单必然一致，
 *    前端那一格只读。只有 inbound_source=2 采购入库 那一路挂得上采购单，其它来源（含 1 线上退货，
 *    由退款回调建）这两列都是 NULL —— 见 validate 按 fromPurchase 的分岔。
 * 2. 明细按 sku_id 对着采购单的明细校验：规格必须在采购单里，且"这张采购单下所有入库单 + 本次"的
 *    同一规格的计划入库量合计不超过它的采购数量（明细表没有指向 shop_erp_purchase_item 的列，
 *    所以只能按规格对，同规格几行采购量会合并成一个上限）。
 * 3. quantity 是"这张单登记要入多少"，received_quantity 是"实际已经入进去多少"，差值就是还能入多少。
 *    真正动库存的是 receive（列表上那个「入库」按钮）：按每行的本次入库数给库存加账、刷加权成本价
 *    （见 ShopErpInventoryServiceImpl#inbound），再累加这单和来源采购单明细的已入库数量。
 *    所以一张单可以先入 3 件、过两天再入剩下的 7 件。
 * 4. 主表 status 不再由人手工选：新增恒 0（还没入过），修改和入库都按明细进度算
 *    （一行都没入 0 / 全部入满 2 / 其余 1），receive_time 第一次进 2 时补。
 * 5. 改单是整单明细重存（软删旧行再插新行、行的 id 会变），所以已入库数量要在重存前按规格分回新行
 *    （carryOverReceived）；已入过货的规格不许从明细里删掉、计划数也不许改到已入库数量以下，
 *    否则库存和单据对不上账。同理，入过货的单子整个不许删。
 * 6. 来源采购单的状态由这一份代码管（refreshPurchaseStatus，增 / 改 / 删 / 入库后各带一次）：
 *    每个规格的采购量都被名下入库单登记满了 → 4 待入库，实入数够了 → 2 已入库，还有规格没登记满 → 1 已审核。
 *    采购单表单的「状态」那一格只有 0/1/3 是人选的，入库单的下拉也只列 1（登记满的单子自动从下拉里消失）。
 *
 * 数据范围：ERP 操作员账号（account_type=5）只看/只改 user_id = 自己那几张（"我操作的入库单"），
 * 三件套同采购单：列表覆盖 userId、读写前 assertOwn、新增钉成登录 id / 修改钉回原值。
 * 平台等其它账号看全部、也管全部；user_id 为 NULL（没记操作人）的单 ERP 账号看不到。
 */
@Service
@RequiredArgsConstructor
public class ShopErpPurchaseInServiceImpl extends ServiceImpl<ShopErpPurchaseInMapper, ShopErpPurchaseIn>
        implements ShopErpPurchaseInService {

    /** 状态：待入库（字典 purchase_in_status） */
    private static final int STATUS_PENDING = 0;

    /** 状态：部分入库 */
    private static final int STATUS_PARTIAL = 1;

    /** 状态：已入库（第一次进入时补 receive_time） */
    private static final int STATUS_RECEIVED = 2;

    /**
     * 采购单状态（字典 purchase_status）：1 已审核 / 2 已入库 / 4 待入库。
     * 待入库 = 这张单每个规格的采购量都已经被它名下的入库单登记满了，货全在单子上、还没进仓；
     * 还有规格没登记满就退回 1，所以入库这条链只在 1 ↔ 4 ↔ 2 之间挪它，0 待审核 / 3 已退货不碰
     */
    private static final int PURCHASE_STATUS_AUDITED = 1;
    private static final int PURCHASE_STATUS_RECEIVED = 2;
    private static final int PURCHASE_STATUS_WAIT_IN = 4;

    private final ShopErpPurchaseInItemMapper itemMapper;

    /** 按 purchase_id 取来源采购单（回填供应商 + 明细查重 + 回写已入库数量），不联表回名称 */
    private final ShopErpPurchaseMapper purchaseMapper;

    private final ShopErpPurchaseItemMapper purchaseItemMapper;

    /** 校验仓库存在性；入库动作也要它（仓库被删了就不该再把货记到它头上） */
    private final ShopErpWarehouseMapper warehouseMapper;

    /** 同上，只校验操作人存在性 */
    private final SysUserMapper sysUserMapper;

    /** 库存落库的唯一入口：入多少件、成本价怎么加权，全在它那边 */
    private final ShopErpInventoryService inventoryService;

    @Override
    public IPage<ShopErpPurchaseIn> selectPage(ShopErpPurchaseIn query) {
        if (query == null) {
            query = new ShopErpPurchaseIn();
        }
        // 一律覆盖 userId：ERP 账号换成登录 id，其它账号置空。列表上没有"按操作人筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人操作的单子
        query.setUserId(LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null);
        // 关键字跨五列搜 + 采购单号/供应商名/仓库名/操作员名联表，写条件用 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectPurchaseInPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopErpPurchaseIn selectPurchaseInById(Long id) {
        ShopErpPurchaseIn purchaseIn = baseMapper.selectPurchaseInById(id);
        if (purchaseIn == null) {
            throw new ServiceException("入库单不存在");
        }
        assertOwn(purchaseIn);
        purchaseIn.setItems(itemMapper.selectItemsByReceiptId(id));
        return purchaseIn;
    }

    @Override
    public List<ShopErpPurchaseIn> selectPurchaseInByPurchase(Long purchaseId) {
        if (purchaseId == null) {
            throw new ServiceException("请选择采购单");
        }
        assertPurchaseOwn(purchaseId);
        // ERP 账号只列自己操作的那几张（同 selectPage 覆盖 userId 那一刀）
        Long scopeUserId = LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null;
        List<ShopErpPurchaseIn> receipts = baseMapper.selectReceiptsByPurchaseId(purchaseId, scopeUserId);
        if (receipts.isEmpty()) {
            return receipts;
        }
        List<Long> receiptIds = new ArrayList<>(receipts.size());
        for (ShopErpPurchaseIn receipt : receipts) {
            receiptIds.add(receipt.getId());
        }
        // 明细一次批量捞（XML 里按 receipt_id, id 排好，这里分组后每单的行序不变）
        Map<Long, List<ShopErpPurchaseInItem>> itemsByReceipt = new HashMap<>();
        for (ShopErpPurchaseInItem item : itemMapper.selectItemsByReceiptIds(receiptIds)) {
            itemsByReceipt.computeIfAbsent(item.getReceiptId(), key -> new ArrayList<>()).add(item);
        }
        for (ShopErpPurchaseIn receipt : receipts) {
            receipt.setItems(itemsByReceipt.getOrDefault(receipt.getId(), new ArrayList<>()));
        }
        return receipts;
    }

    @Override
    public List<ShopErpPurchaseItem> selectPurchaseItems(Long purchaseId, Long excludeReceiptId) {
        if (purchaseId == null) {
            throw new ServiceException("请选择采购单");
        }
        assertPurchaseOwn(purchaseId);
        List<ShopErpPurchaseItem> items = purchaseItemMapper.selectItemsByPurchaseId(purchaseId);
        // 同一规格可能占几行，先按规格把采购量合并、扣掉别人已经登记的，再按行序分摊回各行
        Map<Long, Integer> free = new HashMap<>();
        for (ShopErpPurchaseItem item : items) {
            free.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        Map<Long, Integer> registered = sumReceivedBySku(purchaseId, excludeReceiptId);
        for (Map.Entry<Long, Integer> entry : free.entrySet()) {
            entry.setValue(Math.max(0, entry.getValue() - registered.getOrDefault(entry.getKey(), 0)));
        }
        for (ShopErpPurchaseItem item : items) {
            int left = free.getOrDefault(item.getSkuId(), 0);
            int take = Math.min(left, item.getQuantity() == null ? 0 : item.getQuantity());
            item.setAvailableQuantity(take);
            free.put(item.getSkuId(), left - take);
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopErpPurchaseIn purchaseIn) {
        // 新增时表单那栏是禁用的、不会传单号，和采购单号一样由系统生成
        if (StringUtils.isBlank(purchaseIn.getReceiptNo())) {
            purchaseIn.setReceiptNo(GeneratorNoUtils.getPurchaseInNo());
        }
        validate(purchaseIn, null, Collections.emptyMap());
        // 新建的单子还没有任何进度，已入库数量一律 0（顺带把请求体里可能带的值冲掉）
        carryOverReceived(purchaseIn.getItems(), new HashMap<>());
        recalculate(purchaseIn);
        purchaseIn.setStatus(progressStatus(purchaseIn.getItems()));
        // 入库时间不由表单决定：新增一律从 null 起（下面 applyReceiveTime 才按状态补）。
        // 钉这一道是防"编辑完一张单再点新增"时，reactive 表单里残留的 receiveTime 被原样插进新单
        purchaseIn.setReceiveTime(null);
        applyReceiveTime(purchaseIn, null);
        // 操作人：ERP 账号录的单只能记自己名下（表单那一格对他禁用了，服务端再钉一道）。
        // 其它账号没指定就留 NULL（= 没记是谁操作的）
        if (LoginUserUtils.isErp()) {
            purchaseIn.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(purchaseIn);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_receipt_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("入库单号「" + purchaseIn.getReceiptNo()
                    + "」已被占用，可能来自一张已删除的入库单");
        }
        saveItems(purchaseIn);
        // 登记量变了，来源采购单的状态要重新对一遍（明细全登记满就是「待入库」）
        refreshPurchaseStatus(purchaseIn.getPurchaseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopErpPurchaseIn purchaseIn) {
        if (purchaseIn.getId() == null) {
            throw new ServiceException("入库单ID不能为空");
        }
        ShopErpPurchaseIn exists = getById(purchaseIn.getId());
        if (exists == null) {
            throw new ServiceException("入库单不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 已入库的整单不能再改：库存已经按这张单的明细加过账（还记了流水），
        // 这里改数量/删行就等于把单据改成和账对不上的样子 —— 入错了去库存冲账，别回来改单
        // （口径同 deleteByIds 拦已入过货的行；部分入库的单子仍可改，那些行由 validate 按已入数挡）
        if (exists.getStatus() != null && exists.getStatus() == STATUS_RECEIVED) {
            throw new ServiceException("这张入库单已入库，不能再修改；要调整数量请去库存里冲账");
        }
        // 操作人只有非 ERP 账号能换；ERP 账号钉回原值，免得改个备注把单子挪到别人名下
        // （这一列是 IGNORED，给 null 就会真写出 null，所以是钉值不是留空）
        if (LoginUserUtils.isErp()) {
            purchaseIn.setUserId(exists.getUserId());
        }
        // 单号允许不传（表单里是只读的），不传时沿用原值参与查重
        if (StringUtils.isBlank(purchaseIn.getReceiptNo())) {
            purchaseIn.setReceiptNo(exists.getReceiptNo());
        }
        // 这单已入库的量按规格汇总出来：一是校验要用（计划数不能小于已入数、入过货的规格不能删行），
        // 二是整单重存之后要把进度搬回新行。validate 只读它，carryOverReceived 会改写它，所以传两份
        Map<Long, Integer> receivedBySku = sumReceivedOfThisReceipt(purchaseIn.getId());
        validate(purchaseIn, purchaseIn.getId(), receivedBySku);
        carryOverReceived(purchaseIn.getItems(), new HashMap<>(receivedBySku));
        recalculate(purchaseIn);
        purchaseIn.setStatus(progressStatus(purchaseIn.getItems()));
        // 入库时间同样钉回库里的值（同仓库的 capacity_used）：请求体带什么都不认，
        // 只有第一次进入"已入库"时由 applyReceiveTime 补
        purchaseIn.setReceiveTime(exists.getReceiveTime());
        applyReceiveTime(purchaseIn, exists);
        try {
            updateById(purchaseIn);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("入库单号「" + purchaseIn.getReceiptNo()
                    + "」已被占用，可能来自一张已删除的入库单");
        }
        // 明细整单重存：先把这单现有的软删掉再插新的。
        // 没有 (receipt_id, sku_id) 唯一键，所以重插不会撞逻辑删除留下的旧行（购物车那边撞过，这里不会）
        removeItems(purchaseIn.getId());
        saveItems(purchaseIn);
        // 登记量变了（改数量、增删行都算），来源采购单的状态要重新对一遍；
        // 这张单改到过别的采购单，原来那张腾出的额度也得一起重算
        refreshPurchaseStatus(purchaseIn.getPurchaseId());
        if (exists.getPurchaseId() != null && !exists.getPurchaseId().equals(purchaseIn.getPurchaseId())) {
            refreshPurchaseStatus(exists.getPurchaseId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先按 id 捞出要删的几张：一是给归属校验用，二是删完得知道该重算哪几张采购单的状态
        List<ShopErpPurchaseIn> receipts = listByIds(ids);
        // ERP 账号删的必须是自己操作的：堵住直接拿别人单号打接口这条路
        if (LoginUserUtils.isErp()) {
            for (ShopErpPurchaseIn purchaseIn : receipts) {
                assertOwn(purchaseIn);
            }
        }
        // 已入库（status=2）的整单不许删，口径同「修改」那道闸：货已经按这张单进库存了，
        // 删单不会把库存减回去，删完就是一笔对不上的死账
        for (ShopErpPurchaseIn purchaseIn : receipts) {
            if (purchaseIn.getStatus() != null && purchaseIn.getStatus() == STATUS_RECEIVED) {
                throw new ServiceException("入库单「" + purchaseIn.getReceiptNo()
                        + "」已入库，不能删除；要调整数量请去库存里冲账");
            }
        }
        // 已经入过货的单子不许删：库存那边已经加了账，删单不会把库存减回去，删完就是一笔对不上的死账
        // （这一条管的是"部分入库"那些 —— 状态还没到 2，但已经有行进过账）
        for (ShopErpPurchaseInItem item : itemMapper.selectList(new LambdaQueryWrapper<ShopErpPurchaseInItem>()
                .in(ShopErpPurchaseInItem::getReceiptId, ids))) {
            Integer received = item.getReceivedQuantity();
            if (received != null && received > 0) {
                throw new ServiceException("这张单已经入库 " + received + " 件（规格 #" + item.getSkuId()
                        + "），不能删除；货入错了要去库存里冲账");
            }
        }
        removeByIds(ids);
        // 明细跟着一起软删：留着孤儿行，以后按 receipt_id 统计入库量就会把已删除单的行算进去
        itemMapper.delete(new LambdaQueryWrapper<ShopErpPurchaseInItem>()
                .in(ShopErpPurchaseInItem::getReceiptId, ids));
        // 这几张单占的登记额度腾出来了，来源采购单可能从「待入库」退回「已审核」
        for (ShopErpPurchaseIn purchaseIn : receipts) {
            refreshPurchaseStatus(purchaseIn.getPurchaseId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(ShopErpPurchaseIn form) {
        if (form == null || form.getId() == null) {
            throw new ServiceException("入库单ID不能为空");
        }
        // 直接拿库里的整单来改（不用请求体）：user_id / purchase_id / remark 这些列是 IGNORED，
        // 用稀疏实体更新会把没带的列写成 NULL
        ShopErpPurchaseIn purchaseIn = getById(form.getId());
        if (purchaseIn == null) {
            throw new ServiceException("入库单不存在");
        }
        assertOwn(purchaseIn);
        // 来源采购单可以没有（inbound_source=1 线上退货 就是退款回调建的、没有采购单那一路）：
        // 没有它下面就少两道闸 —— "规格必须在采购单里"和"不能入超采购量"，
        // 这一路的数量是回调按订单明细算死的，够不够由那张退款单管
        ShopErpPurchase purchase = purchaseIn.getPurchaseId() == null
                ? null : purchaseMapper.selectById(purchaseIn.getPurchaseId());
        if (purchaseIn.getPurchaseId() != null && purchase == null) {
            throw new ServiceException("来源采购单不存在或已被删除，无法入库");
        }
        ShopErpWarehouse warehouse = warehouseMapper.selectById(purchaseIn.getWarehouseId());
        if (warehouse == null) {
            throw new ServiceException("入库仓库不存在或已被删除，无法入库");
        }
        // 盘点中/调拨中的仓账是冻住的：这一张单等到仓里腾出来再入，先把入库动作拦在这儿
        // （库存那一层还有一道同样的总闸，见 ShopErpInventoryServiceImpl#inbound）
        if (WarehouseUtils.frozen(warehouse)) {
            throw new ServiceException(WarehouseUtils.frozenMessage(warehouse, "入库"));
        }
        List<ShopErpPurchaseInItem> rows = itemMapper.selectItemsByReceiptId(purchaseIn.getId());
        if (rows.isEmpty()) {
            throw new ServiceException("这张入库单没有明细，先在修改里补上要入库的商品再入库");
        }
        // 请求体只认 (明细行 id, 本次入库数, 本次单价)，数量/规格/仓库全以库里的行为准
        Map<Long, ShopErpPurchaseInItem> patches = new HashMap<>();
        if (form.getItems() != null) {
            for (ShopErpPurchaseInItem line : form.getItems()) {
                if (line.getId() == null) {
                    throw new ServiceException("入库明细缺少行ID，请重新打开入库页再提交");
                }
                if (patches.put(line.getId(), line) != null) {
                    throw new ServiceException("同一条入库明细提交了两次，请刷新入库页");
                }
            }
        }
        // 采购单那边每个规格的采购量和已入库量（同规格几行合并，口径同 validate：只能按规格对）。
        // 没有来源采购单的那一路这两个表留空，下面那道"不能入超采购量"的闸跟着跳过
        List<ShopErpPurchaseItem> purchaseItems = purchase == null
                ? Collections.emptyList() : purchaseItemMapper.selectItemsByPurchaseId(purchase.getId());
        Map<Long, Integer> purchasedQty = new HashMap<>();
        Map<Long, Integer> purchaseReceived = new HashMap<>();
        for (ShopErpPurchaseItem purchaseItem : purchaseItems) {
            purchasedQty.merge(purchaseItem.getSkuId(),
                    purchaseItem.getQuantity() == null ? 0 : purchaseItem.getQuantity(), Integer::sum);
            purchaseReceived.merge(purchaseItem.getSkuId(),
                    purchaseItem.getReceivedQuantity() == null ? 0 : purchaseItem.getReceivedQuantity(), Integer::sum);
        }

        Long operatorId = StpUtil.getLoginIdAsLong();
        // 流水的变动类型跟着入库来源走：1 线上退货 那一路进的货是买家退回来的，记成 4 退货入库，
        // 别和 2 采购入库 混成一档（这页以外的账看不出来货是从哪来的）。其余来源照旧记 1
        int changeType = Integer.valueOf(ShopErpPurchaseIn.SOURCE_ONLINE_RETURN).equals(purchaseIn.getInboundSource())
                ? ShopErpInventoryLog.CHANGE_TYPE_RETURN_IN : ShopErpInventoryLog.CHANGE_TYPE_PURCHASE_IN;
        Map<Long, Integer> addBySku = new HashMap<>();
        int totalThisTime = 0;
        for (ShopErpPurchaseInItem row : rows) {
            ShopErpPurchaseInItem patch = patches.remove(row.getId());
            if (patch == null) {
                // 没提交的行 = 这次不入，原样留着（分几次入就靠这一点）
                continue;
            }
            int quantity = row.getQuantity() == null ? 0 : row.getQuantity();
            int received = row.getReceivedQuantity() == null ? 0 : row.getReceivedQuantity();
            int thisTime = patch.getThisQuantity() == null ? 0 : patch.getThisQuantity();
            if (thisTime < 0) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 的本次入库数不能为负");
            }
            if (thisTime == 0) {
                continue;
            }
            BigDecimal price = patch.getPrice() == null ? row.getPrice() : patch.getPrice();
            if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 的入库单价（成本价）不能为空且不能为负");
            }
            if (thisTime > quantity - received) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 这单计划入库 " + quantity
                        + " 件、已入库 " + received + " 件，本次最多还能入 " + Math.max(quantity - received, 0) + " 件");
            }
            int limit = purchasedQty.getOrDefault(row.getSkuId(), 0);
            int already = purchaseReceived.getOrDefault(row.getSkuId(), 0);
            if (purchase != null && thisTime > limit - already) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 采购 " + limit
                        + " 件，这张采购单已入库 " + already + " 件，本次最多还能入 " + Math.max(limit - already, 0) + " 件");
            }
            // 库存加账：这个规格在这个仓库没有库存行就顺手建一行，成本价按加权平均刷；
            // 顺带记一行库存流水，来源单据就是这张入库单（变动类型按入库来源定，见上面 changeType）
            inventoryService.inbound(row.getSkuId(), purchaseIn.getWarehouseId(), thisTime, price, operatorId,
                    changeType, purchaseIn.getId(), purchaseIn.getReceiptNo());
            // 这行的进度累加；单价按这次实际入库的成本价覆盖，小计跟着重算（主表总额循环完一起刷）
            BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            ShopErpPurchaseInItem itemPatch = new ShopErpPurchaseInItem();
            itemPatch.setId(row.getId());
            itemPatch.setReceivedQuantity(received + thisTime);
            itemPatch.setPrice(price);
            itemPatch.setAmount(amount);
            itemMapper.updateById(itemPatch);

            row.setReceivedQuantity(received + thisTime);
            row.setPrice(price);
            row.setAmount(amount);
            addBySku.merge(row.getSkuId(), thisTime, Integer::sum);
            purchaseReceived.merge(row.getSkuId(), thisTime, Integer::sum);
            totalThisTime += thisTime;
        }
        if (!patches.isEmpty()) {
            throw new ServiceException("提交的明细里有不属于这张入库单的行，请刷新入库页后重试");
        }
        if (totalThisTime == 0) {
            throw new ServiceException("本次入库数全部为 0，没有要入库的规格");
        }
        addPurchaseReceived(purchaseItems, addBySku);
        // 主表：登记金额按各行最新单价重算，状态按进度往前推。
        // applyReceiveTime 的第二个参数要的是"改之前库里的入库时间"，这里 purchaseIn 就是刚读出来的那行
        // （其它字段上面已经改过，receiveTime 一直没动），所以传自己
        purchaseIn.setItems(rows);
        recalculate(purchaseIn);
        purchaseIn.setStatus(progressStatus(rows));
        applyReceiveTime(purchaseIn, purchaseIn);
        updateById(purchaseIn);
        // 来源采购单跟着这次进度重算：实入够了就是 2 已入库，还没入满则按登记进度落在 4 / 1
        refreshPurchaseStatus(purchaseIn.getPurchaseId());
    }

    /**
     * ERP 账号的归属校验，口径同采购单：非 ERP 直接放过（平台等看全部、也管全部）。
     * 这里补的是"绕过列表直接拿别人入库单 id 打接口"那条路。
     * user_id 为 NULL 的单（平台录的、没记操作人）ERP 账号本来就看不到，这里也一律改不动。
     */
    private void assertOwn(ShopErpPurchaseIn purchaseIn) {
        if (!LoginUserUtils.isErp()) {
            return;
        }
        Long userId = purchaseIn.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张入库单不是你操作的，无权查看或修改");
        }
    }

    /**
     * 按采购单查东西前的那一道闸：单要真在（已删的不给查），而且 ERP 账号只能碰自己负责的那几张。
     * 口径照 ShopErpPurchaseServiceImpl#assertOwn；下拉给 ERP 看的本来就是自己那几张，
     * 这里堵的是拿别人单号直接打只读接口这条路。两个只读接口（明细候选、按采购单查入库单）共用
     */
    private void assertPurchaseOwn(Long purchaseId) {
        ShopErpPurchase purchase = purchaseMapper.selectById(purchaseId);
        if (purchase == null) {
            throw new ServiceException("所选采购单不存在或已被删除");
        }
        if (LoginUserUtils.isErp() && (purchase.getUserId() == null
                || purchase.getUserId().longValue() != StpUtil.getLoginIdAsLong())) {
            throw new ServiceException("这张采购单不是你负责的，无权查看或修改");
        }
    }

    /**
     * 明细落库：receiptId 一律以主表 id 为准（请求体里带什么都不认），
     * 已入库数量在 carryOverReceived 里算好、金额在 recalculate 里算好，这里只插
     */
    private void saveItems(ShopErpPurchaseIn purchaseIn) {
        for (ShopErpPurchaseInItem item : purchaseIn.getItems()) {
            // 详情接口的字段会被原样塞回表单，明细带着原来的 id 回来；不清零 INSERT 就会显式带上主键
            item.setId(null);
            item.setReceiptId(purchaseIn.getId());
            itemMapper.insert(item);
        }
    }

    private void removeItems(Long receiptId) {
        itemMapper.delete(new LambdaQueryWrapper<ShopErpPurchaseInItem>()
                .eq(ShopErpPurchaseInItem::getReceiptId, receiptId));
    }

    /**
     * 把这单已入库的数量按规格分回明细行（整单重存会换行 id，所以要在重存前搬一次）。
     * 同一规格几行就按行序依次填满，每行不超过它自己的计划数，保证 0 ≤ received_quantity ≤ quantity。
     * 会改写传进来的那个 map（每分配掉一行就扣掉对应数量），所以调用方要给一份能造的副本
     */
    private void carryOverReceived(List<ShopErpPurchaseInItem> items, Map<Long, Integer> receivedBySku) {
        for (ShopErpPurchaseInItem item : items) {
            int carry = receivedBySku.getOrDefault(item.getSkuId(), 0);
            int got = Math.min(Math.max(carry, 0), item.getQuantity());
            item.setReceivedQuantity(got);
            item.setThisQuantity(null);
            receivedBySku.put(item.getSkuId(), carry - got);
        }
    }

    /**
     * 按明细重算"服务端说了算"的数：每行 amount = 数量 × 单价，主表 total 累加。
     * 单价/数量的合法性在 validate 里已经保证，所以这里不做 null 兜底。
     * 状态不在这里算（那是 progressStatus 的事）
     */
    private void recalculate(ShopErpPurchaseIn purchaseIn) {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (ShopErpPurchaseInItem item : purchaseIn.getItems()) {
            item.setAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            quantity += item.getQuantity();
            amount = amount.add(item.getAmount());
        }
        purchaseIn.setTotalQuantity(quantity);
        purchaseIn.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** 按明细进度算主表状态：一行都没入 0 待入库；全部入满 2 已入库；其余 1 部分入库 */
    private int progressStatus(List<ShopErpPurchaseInItem> items) {
        int plan = 0;
        int received = 0;
        for (ShopErpPurchaseInItem item : items) {
            int rowPlan = item.getQuantity() == null ? 0 : item.getQuantity();
            plan += rowPlan;
            received += Math.min(item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity(), rowPlan);
        }
        if (received <= 0) {
            return STATUS_PENDING;
        }
        return received >= plan ? STATUS_RECEIVED : STATUS_PARTIAL;
    }

    /**
     * 状态第一次进入 2（已入库）时补 receive_time，其余情况一律留 null。
     * 留 null 而不是回填旧值是有意的：receive_time 用的是全局 not_empty（不是 IGNORED），
     * null 就整列不进 UPDATE 的 SET，等于"已记过的时间不会被改单改掉"（同采购单的 audit_time）
     */
    private void applyReceiveTime(ShopErpPurchaseIn purchaseIn, ShopErpPurchaseIn exists) {
        if (purchaseIn.getStatus() == null) {
            return;
        }
        LocalDateTime oldReceiveTime = exists == null ? null : exists.getReceiveTime();
        if (purchaseIn.getStatus() == STATUS_RECEIVED && oldReceiveTime == null) {
            purchaseIn.setReceiveTime(LocalDateTime.now());
        }
    }

    /**
     * 把这次的入库量按规格分配回采购单明细的 received_quantity（同规格几行按行序依次填满，
     * 每行不超过它的采购数量）。
     *
     * 只写明细、不动采购单的状态：状态是按全单进度推出来的，统一交给 refreshPurchaseStatus
     * （receive 末尾带一次），免得两处各写一套口径。0待审核 / 3已退货 不参与推导，
     * 那种"别替它跳级"的判断也收在那里。
     */
    private void addPurchaseReceived(List<ShopErpPurchaseItem> purchaseItems, Map<Long, Integer> addBySku) {
        for (ShopErpPurchaseItem purchaseItem : purchaseItems) {
            int quantity = purchaseItem.getQuantity() == null ? 0 : purchaseItem.getQuantity();
            int received = purchaseItem.getReceivedQuantity() == null ? 0 : purchaseItem.getReceivedQuantity();
            Integer left = addBySku.get(purchaseItem.getSkuId());
            if (left != null && left > 0) {
                int take = Math.min(left, Math.max(quantity - received, 0));
                if (take > 0) {
                    received += take;
                    addBySku.put(purchaseItem.getSkuId(), left - take);
                    ShopErpPurchaseItem itemPatch = new ShopErpPurchaseItem();
                    itemPatch.setId(purchaseItem.getId());
                    itemPatch.setReceivedQuantity(received);
                    purchaseItemMapper.updateById(itemPatch);
                    purchaseItem.setReceivedQuantity(received);
                }
            }
        }
    }

    /**
     * 来源采购单的状态按入库进度重算（入库单的增 / 改 / 删和入库动作都会带一次，采购单改单也带一次）。
     *
     * 三档："每个规格的采购量都被名下入库单登记满了" -> 4 待入库；实入数够了采购数 -> 2 已入库；
     * 还有规格没登记满 -> 1 已审核（还能再建入库单补货）。0 待审核 / 3 已退货 不参与推导，原样留着。
     *
     * 写只写 status/receive_time 这两列：这张表上有好几个 IGNORED 列（expect_date / contact_* / remark /
     * user_id），拿整行实体 updateById 回写就等于把调用方刚改的值按自己读到的那份覆盖一遍，纯属找事。
     */
    @Override
    public void refreshPurchaseStatus(Long purchaseId) {
        if (purchaseId == null) {
            return;
        }
        ShopErpPurchase purchase = purchaseMapper.selectById(purchaseId);
        if (purchase == null) {
            return;
        }
        Integer current = purchase.getStatus();
        if (current == null || (current != PURCHASE_STATUS_AUDITED && current != PURCHASE_STATUS_WAIT_IN
                && current != PURCHASE_STATUS_RECEIVED)) {
            return;
        }
        Map<Long, Integer> planBySku = new HashMap<>();
        int totalReceived = 0;
        for (ShopErpPurchaseItem item : purchaseItemMapper.selectItemsByPurchaseId(purchaseId)) {
            planBySku.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(),
                    Integer::sum);
            totalReceived += item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
        }
        // 按规格汇总名下入库单登记的计划量（口径同 validate：登记量不会超过采购量）
        Map<Long, Integer> registeredBySku = sumReceivedBySku(purchaseId, null);
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
        if (totalPlan > 0 && totalReceived >= totalPlan) {
            target = PURCHASE_STATUS_RECEIVED;
        } else {
            target = allRegistered ? PURCHASE_STATUS_WAIT_IN : PURCHASE_STATUS_AUDITED;
        }
        if (target == current) {
            return;
        }
        boolean needReceiveTime = target == PURCHASE_STATUS_RECEIVED && purchase.getReceiveTime() == null;
        purchaseMapper.update(null, new LambdaUpdateWrapper<ShopErpPurchase>()
                .set(ShopErpPurchase::getStatus, target)
                .set(needReceiveTime, ShopErpPurchase::getReceiveTime, LocalDateTime.now())
                .eq(ShopErpPurchase::getId, purchaseId));
    }

    /** 这张入库单自己已入库的数量，按规格汇总（改单时要拿它校验和搬进度） */
    private Map<Long, Integer> sumReceivedOfThisReceipt(Long receiptId) {
        Map<Long, Integer> received = new HashMap<>();
        for (ShopErpPurchaseInItem item : itemMapper.selectItemsByReceiptId(receiptId)) {
            received.merge(item.getSkuId(),
                    item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity(), Integer::sum);
        }
        return received;
    }

    /**
     * 主表 + 明细一起校验：来源采购单必须真有其单（供应商从它带）、仓库和操作人填了要真有其人、
     * 明细至少一行且每行规格都得在采购单里、同一规格的计划入库量（这张采购单下所有入库单累加）
     * 不能超过采购数量，另外已入过货的规格不能改小计划数、也不能被删掉（receivedBySku 是这单自己的进度）。
     *
     * 不校验采购单的状态：候选范围由前端下拉（只列已审核的单）决定，服务端挡结构不挡流转，
     * 免得回改一张已经入过库的历史单子时被自己下的状态规则卡住。
     *
     * 另外新建的那一张必须是采购入库且挂得住采购单（见下面的 excludeId == null）。
     */
    private void validate(ShopErpPurchaseIn purchaseIn, Long excludeId, Map<Long, Integer> receivedBySku) {
        if (StringUtils.isBlank(purchaseIn.getReceiptNo())) {
            throw new ServiceException("入库单号不能为空");
        }
        purchaseIn.setReceiptNo(purchaseIn.getReceiptNo().trim());
        // 入库来源：不传按 2 采购入库 算（后台新增那格的下拉默认就是它，改单时这一格禁用、沿库里的值回来）。
        // 1 线上退货 那几张是退款回调建的，走到这儿只可能是后台在「修改」里动它 —— 那一路没有采购单，
        // 所以下面"规格必须在采购单里、合计不超过采购量"那两道闸整个跳过，改按"有没有来源采购单"分岔
        int source = purchaseIn.getInboundSource() == null
                ? ShopErpPurchaseIn.SOURCE_PURCHASE : purchaseIn.getInboundSource();
        purchaseIn.setInboundSource(source);
        boolean fromPurchase = source == ShopErpPurchaseIn.SOURCE_PURCHASE;
        // 后台建单只走采购这一路：其余来源的单据由各自的回调按来源单据生成（1 线上退货 ← 退款单，
        // 那种单 purchase_id 就是 NULL）。excludeId 为 null 就是新建，改单不挡 —— 回调建的那几张
        // 已经躺在库里，后台还得能改它们的计划数
        if (!fromPurchase && excludeId == null) {
            throw new ServiceException("新建入库单必须绑定采购单，其它来源的入库单由系统按来源单据自动生成");
        }
        ShopErpPurchase purchase = null;
        if (!fromPurchase) {
            // 非采购来源不认请求体里的采购单和供应商：这两格本来就该是空的
            purchaseIn.setPurchaseId(null);
            purchaseIn.setSupplierId(null);
        } else {
            if (purchaseIn.getPurchaseId() == null) {
                throw new ServiceException("入库来源是「采购入库」时请选择采购单");
            }
            purchase = purchaseMapper.selectById(purchaseIn.getPurchaseId());
            if (purchase == null) {
                throw new ServiceException("所选采购单不存在或已被删除");
            }
            // 供应商由采购单决定：请求体里带了别的值也一律覆盖，免得两张单的供应商对不上
            purchaseIn.setSupplierId(purchase.getSupplierId());
        }
        if (purchaseIn.getWarehouseId() == null) {
            throw new ServiceException("请选择入库仓库");
        }
        // selectById 带 is_deleted = 0，禁用（status=0）的仓库行还在、照样能选——
        // 已经发生的入库不该因为仓库后来被禁用就存不下来。盘点中/调拨中（2/3）也照样能建单改单：
        // 建单不动账，真正拦它的是上面 #receive 那颗闸（还有库存那层的总闸），
        // 在这儿拦会把"仓刚进盘点、单还没填完"这种正在录的单子直接锁死
        if (warehouseMapper.selectById(purchaseIn.getWarehouseId()) == null) {
            throw new ServiceException("所选仓库不存在或已被删除");
        }
        if (purchaseIn.getUserId() != null && sysUserMapper.selectById(purchaseIn.getUserId()) == null) {
            throw new ServiceException("所选入库操作人不存在");
        }
        List<ShopErpPurchaseInItem> items = purchaseIn.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("入库明细不能为空，请至少保留一行商品");
        }
        // 采购单里每个规格的可入库上限（同规格几行采购量合并；明细表没有指向采购明细的列，只能按规格对）
        Map<Long, Integer> purchasedQty = new HashMap<>();
        if (fromPurchase) {
            for (ShopErpPurchaseItem pi : purchaseItemMapper.selectItemsByPurchaseId(purchase.getId())) {
                purchasedQty.merge(pi.getSkuId(), pi.getQuantity() == null ? 0 : pi.getQuantity(), Integer::sum);
            }
        }
        // 这张采购单**别的**入库单已经登记的量（excludeId 是正在改的那一张，不能把自己算成别人的）：
        // 不累计的话，同一张单反复建两张入库单就能把 100 件的货登记成 200 件
        Map<Long, Integer> receivedQty = fromPurchase
                ? sumReceivedBySku(purchase.getId(), excludeId) : Map.of();
        Map<Long, Integer> inboundQty = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ShopErpPurchaseInItem item = items.get(i);
            String row = "第 " + (i + 1) + " 行明细";
            if (item.getSkuId() == null) {
                throw new ServiceException(fromPurchase
                        ? row + "：缺少规格，请重新选择采购单带出明细" : row + "：缺少规格");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new ServiceException(row + "：入库数量必须大于 0");
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException(row + "：单价不能为空且不能为负");
            }
            if (!fromPurchase) {
                // 没有来源采购单就没有"采购了多少"这道上限：退货入库的上限是买家实际退回来的件数，
                // 那一份退款回调建单时就按订单明细定死了，后台要把数量改大只能去改那张退款单
                inboundQty.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
                continue;
            }
            Integer limit = purchasedQty.get(item.getSkuId());
            if (limit == null) {
                throw new ServiceException(row + "：该规格不在采购单「" + purchase.getPurchaseNo() + "」里，不能入库");
            }
            int total = inboundQty.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
            int already = receivedQty.getOrDefault(item.getSkuId(), 0);
            if (already + total > limit) {
                throw new ServiceException("规格 #" + item.getSkuId() + " 采购 " + limit
                        + " 件，其它入库单已登记 " + already + " 件、本次 " + total + " 件，超过可入库数量");
            }
        }
        // 这一单自己已经入进去的货不能凭空消失：规格行不能删、计划数不能改到比已入库还小
        for (Map.Entry<Long, Integer> entry : receivedBySku.entrySet()) {
            Integer already = entry.getValue();
            if (already == null || already <= 0) {
                continue;
            }
            Integer plan = inboundQty.get(entry.getKey());
            if (plan == null) {
                throw new ServiceException("规格 #" + entry.getKey() + " 已入库 " + already
                        + " 件，不能把这行明细删掉；货入错了要去库存里冲账");
            }
            if (plan < already) {
                throw new ServiceException("规格 #" + entry.getKey() + " 已入库 " + already
                        + " 件，计划入库数不能小于它");
            }
        }
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpPurchaseIn>()
                .eq(ShopErpPurchaseIn::getReceiptNo, purchaseIn.getReceiptNo())
                .ne(excludeId != null, ShopErpPurchaseIn::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("入库单号「" + purchaseIn.getReceiptNo() + "」已存在");
        }
    }

    /**
     * 按规格汇总这张采购单已登记的入库量：先捞出该单下其它入库单（excludeReceiptId 是正在改的那一张，
     * 不能把自己已录的量算成别人的），再按 receipt_id 汇总明细。两次查询、不联表 ——
     * 一个采购单下的入库单撑死几张，没有联表的必要
     *
     * 这里**刻意不按登录账号收窄**：上限是采购单自己的数，别人已登记的量必须算进来，
     * 否则两个操作员各登记一次就能把同一张采购单入超
     */
    private Map<Long, Integer> sumReceivedBySku(Long purchaseId, Long excludeReceiptId) {
        Map<Long, Integer> received = new HashMap<>();
        List<ShopErpPurchaseIn> receipts = list(new LambdaQueryWrapper<ShopErpPurchaseIn>()
                .select(ShopErpPurchaseIn::getId)
                .eq(ShopErpPurchaseIn::getPurchaseId, purchaseId)
                .ne(excludeReceiptId != null, ShopErpPurchaseIn::getId, excludeReceiptId));
        if (receipts.isEmpty()) {
            return received;
        }
        List<Long> receiptIds = new ArrayList<>(receipts.size());
        for (ShopErpPurchaseIn receipt : receipts) {
            receiptIds.add(receipt.getId());
        }
        for (ShopErpPurchaseInItem item : itemMapper.selectList(new LambdaQueryWrapper<ShopErpPurchaseInItem>()
                .in(ShopErpPurchaseInItem::getReceiptId, receiptIds))) {
            received.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        return received;
    }
}
