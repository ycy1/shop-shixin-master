package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpPurchase;
import com.shop.entity.ShopErpPurchaseIn;
import com.shop.entity.ShopErpPurchaseInItem;
import com.shop.entity.ShopErpPurchaseItem;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopErpSaleOutboundItem;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopSupplierProduct;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpPurchaseInMapper;
import com.shop.mapper.ShopErpPurchaseItemMapper;
import com.shop.mapper.ShopErpPurchaseMapper;
import com.shop.mapper.ShopErpSaleOutboundItemMapper;
import com.shop.mapper.ShopErpSaleOutboundMapper;
import com.shop.mapper.ShopErpSupplierMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.ShopSupplierProductMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopErpPurchaseInService;
import com.shop.service.ShopErpPurchaseService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购单 服务实现（主子表一起存的写法参考 ShopProductServiceImpl 的"主表 + SKU 子表"，但明细是多行，所以整单重存）
 *
 * 四件事由服务端说了算，前端传什么都不认：
 *   1. purchase_no —— 新增不传就生成 PURCHASE_+时间戳；
 *   2. total_amount / total_quantity 和明细的 amount —— 一律按明细重算（recalculate）；
 *   3. audit_time / receive_time —— 状态第一次进入 1/4/2 时补，之后不覆盖（applyStatusTimes）；
 *   4. 明细的 received_quantity —— 只由入库动作累加，改单时从库里搬回新行（carryReceivedQuantity）。
 *
 * 单据只有「待审核（0）」这一档能改、能删（update / deleteByIds 各一道闸，口径同一个 isFrozen）：
 * 审核过就等于对外承诺过了，之后要动只能作废重开。删除另外还有一道闸：被入库单引用过的采购单删不掉
 * （deleteByIds），否则入库单成了孤儿、进度也一起没了。
 *
 * 状态里只有 0待审核 / 1已审核 是人在这一页选的（「审核」就是在待审核那张单的修改弹窗里把状态
 * 选成 1，选完这张单就冻结）；4 待入库 和 2 已入库 由入库那条链按进度回写
 * （ShopErpPurchaseInServiceImpl#refreshPurchaseStatus，改单时也重算一次）：每条规格的采购量都被名下入库
 * 单登记满了就是 4，还有规格没登记满退回 1，实入数够了才是 2。入库单表单的采购单下拉只列 1。
 * 3 已退货 同样不是手选的：那张采购单进来的货全部退回供应商、最后一批退货出库时由出库动作回写
 * （ShopErpSaleOutboundServiceImpl#markPurchaseReturned）。
 *
 * 「采购退货」那两颗接口（purchaseReturnDraft / createPurchaseReturn）也归本服务管：退的就是这张单进来的货，
 * 额度按规格汇总（明细行只记 sku_id，已入库数 − 这张单名下退货单已登记的量），建出来的是「商品出库」那一页的
 * 一张 来源=3 的待出库单 —— 货要等仓库在那边点「出库」才真离仓。可多次退货：一张采购单名下能有几张退货单，
 * 前几张没出满也照样占额度。
 * 删除那一道闸不用管退货单：要有退货单先得有入库记录，而被入库单引用的采购单本来就删不掉（deleteByIds）。
 *
 * 数据范围：ERP 操作员账号（account_type=5）只看/只改 user_id = 自己那几条，其它账号（平台、门店等）看全部。
 * user_id 是表单里的「ERP操作员」，所以实体上是 IGNORED（换绑、清空都要能写回库）；ERP 账号自己改不动，钉回原值。
 *
 * 明细的规格候选也归本服务管（selectProductOptions）：能进这张单的规格＝所选供应商在 shop_supplier_product 里
 * 标着「合作中」的那些供货规格（供应商没建供货关系就是没规格可选，先去「供应商商品」那页建关系）。
 * 一行 = 一个规格，要商品名/编码这些 SPU 属性得经 shop_product_sku 那一跳回到 shop_product。
 */
@Service
@RequiredArgsConstructor
public class ShopErpPurchaseServiceImpl extends ServiceImpl<ShopErpPurchaseMapper, ShopErpPurchase>
        implements ShopErpPurchaseService {

    /** 状态：待审核（字典 purchase_status）。只有这一档的单子还能改、还能删，见 update / deleteByIds */
    private static final int STATUS_PENDING = 0;

    /** 状态：已审核（字典 purchase_status，第一次进入时补 audit_time） */
    private static final int STATUS_AUDITED = 1;

    /** 状态：已入库（第一次进入时补 receive_time，同时补上没记过的 audit_time） */
    private static final int STATUS_RECEIVED = 2;

    /** 状态：待入库（货全登记进入库单了、还没入满，由入库那边推出来，不是人手选的） */
    private static final int STATUS_WAIT_IN = 4;

    /** 「采购退货」建的那张出库单的初始状态：0 待出库（字典 sale_outbound_status，之后归商品出库那一页推） */
    private static final int OUTBOUND_STATUS_PENDING = 0;

    /** 规格候选一次最多返回多少条（远程搜索的下拉，同前端原来调商品列表的 pageSize） */
    private static final int PRODUCT_OPTION_LIMIT = 20;

    private final ShopErpPurchaseItemMapper itemMapper;

    // 只用来在删除前问一句"有没有入库单挂着这几张采购单"（见 deleteByIds）
    private final ShopErpPurchaseInMapper purchaseInMapper;

    /** 改单后重算这张单的状态（采购量一改，名下入库单的登记进度就得重新对一遍） */
    private final ShopErpPurchaseInService purchaseInService;

    // 「采购退货」那两颗接口写的就是出库单那两张表（来源=3 的那一路）：这里只当 DAO 用，
    // 判据（额度）在 returnLines，出库那一步的减账和状态推进仍然归商品出库那边
    private final ShopErpSaleOutboundMapper saleOutboundMapper;

    private final ShopErpSaleOutboundItemMapper saleOutboundItemMapper;

    /** 退货单那几行的退回仓库：查状态拦盘点中/调拨中的仓，也是默认仓候选要避开的仓 */
    private final ShopErpWarehouseMapper warehouseMapper;

    private final ShopErpSupplierMapper supplierMapper;

    /** 「所属门店」那一格查存在性用（建入库单时仓库候选要按它收窄，见 ShopErpPurchaseIn 那边） */
    private final ShopStoreMapper storeMapper;

    /** 明细行只记 sku_id：要商品名/编码这些 SPU 属性，先按这一格查 SKU、再按 SKU 上的 product_id 查商品（两跳） */
    private final ShopProductSkuMapper productSkuMapper;

    private final ShopProductMapper productMapper;

    private final ShopSupplierProductMapper supplierProductMapper;

    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopErpPurchase> selectPage(ShopErpPurchase query) {
        if (query == null) {
            query = new ShopErpPurchase();
        }
        // 一律覆盖 userId：ERP 账号换成登录 id，其它账号置空。列表上没有"按操作员筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人负责的单据
        query.setUserId(LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null);
        IPage<ShopErpPurchase> result = baseMapper.selectPurchasePage(PageUtil.getPage(), query);
        // 列表不带明细：一单几十行的子表跟着分页查会打出 N+1，要看明细点开详情就行
        return result;
    }

    @Override
    public List<ShopSupplierProduct> selectProductOptions(Long supplierId, String keyword) {
        if (supplierId == null) {
            throw new ServiceException("请先选择供应商");
        }
        if (supplierMapper.selectById(supplierId) == null) {
            throw new ServiceException("所选供应商不存在");
        }
        // 候选＝这家供应商「合作中」的供货关系里的规格（SQL 见 ShopSupplierProductMapper）：
        // 不再按供应商的分类推范围，那格分类这一版已经从 shop_erp_supplier 删掉了
        return supplierProductMapper.selectProductOptionsBySupplier(supplierId,
                StringUtils.trimToNull(keyword), PRODUCT_OPTION_LIMIT);
    }

    @Override
    public ShopErpPurchase selectPurchaseById(Long id) {
        ShopErpPurchase purchase = baseMapper.selectPurchaseById(id);
        if (purchase == null) {
            throw new ServiceException("采购单不存在");
        }
        assertOwn(purchase);
        purchase.setItems(itemMapper.selectItemsByPurchaseId(id));
        return purchase;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopErpPurchase purchase) {
        // 新增时表单那栏是禁用的、不会传单号，和供应商编码一样由系统生成
        if (StringUtils.isBlank(purchase.getPurchaseNo())) {
            purchase.setPurchaseNo(GeneratorNoUtils.getPurchaseNo());
        }
        validate(purchase, null);
        // 新增的单还没入过货，这一列只能由入库动作往后累加，所以一律写 0（同样不认请求体）
        for (ShopErpPurchaseItem item : purchase.getItems()) {
            item.setReceivedQuantity(0);
        }
        recalculate(purchase);
        applyStatusTimes(purchase, null);
        // 负责的操作员：ERP 账号录的单只能记自己名下。其它账号没指定就留 NULL（= 还没指派），
        // 不能兜底成登录人 —— 这列的语义是"哪个 ERP 操作员负责"，不是创建人留痕（那个看 create_by）
        if (LoginUserUtils.isErp()) {
            purchase.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(purchase);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_purchase_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("采购单号「" + purchase.getPurchaseNo()
                    + "」已被占用，可能来自一张已删除的采购单");
        }
        saveItems(purchase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopErpPurchase purchase) {
        if (purchase.getId() == null) {
            throw new ServiceException("采购单ID不能为空");
        }
        ShopErpPurchase exists = getById(purchase.getId());
        if (exists == null) {
            throw new ServiceException("采购单不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 只有「待审核」的单子还能改：审核过就等于对外承诺过了（入库单是照着它建的），
        // 这时再改明细/数量，名下入库单登记的采购量就对不上。「审核」这个动作本身还是在待审核这张单的
        // 「修改」弹窗里把状态选成 1，选完这张单就冻结（要动只能作废重开）
        if (isFrozen(exists)) {
            throw new ServiceException("采购单「" + exists.getPurchaseNo() + "」已经不在待审核状态，不能再修改");
        }
        // 操作员只有非 ERP 账号能换；ERP 账号钉回原值，免得改个备注把单子挪到别人名下
        // （这一列是 IGNORED，给 null 就会真写出 null，所以是钉值不是留空）
        if (LoginUserUtils.isErp()) {
            purchase.setUserId(exists.getUserId());
        }
        // 单号允许不传（表单里是只读的），不传时沿用原值参与查重
        if (StringUtils.isBlank(purchase.getPurchaseNo())) {
            purchase.setPurchaseNo(exists.getPurchaseNo());
        }
        validate(purchase, purchase.getId());
        carryReceivedQuantity(purchase);
        recalculate(purchase);
        applyStatusTimes(purchase, exists);
        try {
            updateById(purchase);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("采购单号「" + purchase.getPurchaseNo()
                    + "」已被占用，可能来自一张已删除的采购单");
        }
        // 明细整单重存：先把这单现有的软删掉再插新的。
        // 没有 (purchase_id, sku_id) 唯一键，所以重插不会撞逻辑删除留下的旧行（购物车那边撞过，这里不会）
        removeItems(purchase.getId());
        saveItems(purchase);
        // 采购量、行数、规格一改，名下入库单登记得满不满就换了个说法：状态按新明细重算
        // （手填的那格只是起点，4 待入库 / 2 已入库 本来就归入库那条链管）
        purchaseInService.refreshPurchaseStatus(purchase.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 一次按 id 捞出来：归属校验和状态校验都用这份库里的行，请求体里带什么都不算数
        List<ShopErpPurchase> purchases = listByIds(ids);
        // ERP 账号删的必须是自己负责的：逐条校验，堵住直接拿别人 id 打接口这条路
        if (LoginUserUtils.isErp()) {
            for (ShopErpPurchase purchase : purchases) {
                assertOwn(purchase);
            }
        }
        // 同样只有「待审核」能删（口径见 update）：审核过的单子删掉，名下入库单就成了引用悬空的孤儿
        for (ShopErpPurchase purchase : purchases) {
            if (isFrozen(purchase)) {
                throw new ServiceException("采购单「" + purchase.getPurchaseNo()
                        + "」已经不在待审核状态，不能删除");
            }
        }
        // 被入库单引用过就不能删：采购单没了，那些入库单就成了孤儿（供应商、采购量都对不上），
        // 而且这条链路上的"已入库"进度会跟着消失，同一批货还能再入一遍
        List<ShopErpPurchaseIn> receipts = purchaseInMapper.selectList(new LambdaQueryWrapper<ShopErpPurchaseIn>()
                .select(ShopErpPurchaseIn::getReceiptNo)
                .in(ShopErpPurchaseIn::getPurchaseId, ids));
        if (!receipts.isEmpty()) {
            String receiptNos = receipts.stream().map(ShopErpPurchaseIn::getReceiptNo).distinct()
                    .collect(Collectors.joining("、"));
            throw new ServiceException("这些采购单已被入库单「" + receiptNos + "」引用，请先到「商品入库」删除对应入库单");
        }
        removeByIds(ids);
        // 明细跟着一起软删：留着孤儿行，以后按 purchase_id 统计采购量就会把已删除单的行算进去
        itemMapper.delete(new LambdaQueryWrapper<ShopErpPurchaseItem>()
                .in(ShopErpPurchaseItem::getPurchaseId, ids));
    }

    /**
     * 「采购退货」弹窗的草稿：不落库，只把"能退哪些货、每行还能退多少"算给前端填。
     *
     * 行按**规格**汇总（采购明细同一个规格可以有几行，但退货上限是按 sku_id 判的，合成一行好填），
     * 每行带 receivedQuantity（已入库）和 returnableQuantity（还能退），quantity 给 0 由操作员填。
     * 仓库默认带出"这条规格当初主要从哪家仓入的库"，弹窗里可以改 —— 真改到没货的仓，出库那一行
     * 的库存 CAS 会拦下来（口径同商品出库那边，上限不靠前端兜）。
     *
     * 一件能退的都没有就抛异常：宁可不弹窗，也别弹开一张填不了的单
     */
    @Override
    public ShopErpSaleOutbound purchaseReturnDraft(Long purchaseId) {
        ShopErpPurchase purchase = requireReturnable(purchaseId);
        Map<Long, ReturnLine> lines = returnLines(purchase.getId());

        List<ShopErpSaleOutboundItem> items = new ArrayList<>();
        for (ReturnLine line : lines.values()) {
            if (line.returnable() <= 0) {
                // 已经退满的规格不再列（这一路的单子可以开好几张，前几张就把货退完是常态）
                continue;
            }
            ShopErpSaleOutboundItem item = new ShopErpSaleOutboundItem();
            item.setSkuId(line.skuId);
            item.setProductName(line.productName);
            item.setProductCode(line.productCode);
            // 规格文本带出去：一个商品在退货弹窗里现在可能占好几行，光有商品名分不出哪行是哪条规格
            item.setSpecText(line.specText);
            item.setWarehouseId(line.warehouseId);
            // 本次退多少由人在弹窗里填，草稿给 0（口径同「确认入库」那格的默认值）
            item.setQuantity(0);
            // 默认单价 = 这条规格在这张单里的加权采购单价，退回给供应商按进价结，可改
            item.setCostPrice(line.unitCost());
            item.setReceivedQuantity(line.received);
            item.setReturnableQuantity(line.returnable());
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new ServiceException("采购单「" + purchase.getPurchaseNo()
                    + "」没有已入库、且还没退完的商品规格");
        }

        ShopErpSaleOutbound draft = new ShopErpSaleOutbound();
        draft.setOutboundSource(ShopErpSaleOutbound.SOURCE_PURCHASE_RETURN);
        // 来源单据共用 order_id / order_no 两列（3 采购退货 这一路存的是采购单，见实体类注释）
        draft.setOrderId(purchase.getId());
        draft.setOrderNo(purchase.getPurchaseNo());
        draft.setSupplierName(purchase.getSupplierName());
        // 0 待出库：草稿上没有数量，总额自然是 0，提交时按明细重算
        draft.setStatus(OUTBOUND_STATUS_PENDING);
        draft.setTotalQuantity(0);
        draft.setTotalAmount(BigDecimal.ZERO);
        draft.setItems(items);
        return draft;
    }

    /**
     * 「采购退货」弹窗点了确定：建一张来源=3 采购退货 的出库单（0 待出库），货要等仓库在那一页点「出库」才真离仓。
     *
     * 可多次退货：判据只有额度，不看这张单名下已经有几张退货单 —— 上限口径见 {@link #returnLines}
     * （已入库数扣掉名下退货单**已登记**的量；占额度的是登记数不是实出数，否则两张单子能把同一批货退两遍）。
     *
     * 这里刻意不判"仓里够不够货"：这一路建的是待出库的单子，真扣账在出库那一步，
     * 那边逐行按 (规格, 仓库) 走 CAS，货不够会明确说是哪一行。建单时就拦会把"先登记、货还在路上"那种正常用法堵死。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseReturn(Long purchaseId, ShopErpSaleOutbound form) {
        ShopErpPurchase purchase = requireReturnable(purchaseId);
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new ServiceException("请至少选择一件要退的商品规格");
        }
        Map<Long, ReturnLine> lines = returnLines(purchaseId);

        // 请求体只认每行的 (skuId, warehouseId, quantity, costPrice)，其余一律以库里的采购明细为准
        Map<Long, ShopErpSaleOutboundItem> submitted = new LinkedHashMap<>();
        for (ShopErpSaleOutboundItem row : form.getItems()) {
            if (row.getSkuId() == null) {
                throw new ServiceException("退货明细里有没带商品规格的一行，请重新打开弹窗");
            }
            if (submitted.put(row.getSkuId(), row) != null) {
                // 草稿就是一条规格一行，重复只可能是前端把同一规格塞了两行 —— 额度是按 sku_id 算的，
                // 分成两行就没法判谁先占，所以直接拦
                throw new ServiceException("规格 #" + row.getSkuId() + " 提交了两次，请重新打开弹窗");
            }
        }

        List<ShopErpSaleOutboundItem> items = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, ShopErpSaleOutboundItem> entry : submitted.entrySet()) {
            ReturnLine line = lines.get(entry.getKey());
            if (line == null) {
                throw new ServiceException("规格 #" + entry.getKey() + " 不在这张采购单里，无法退货");
            }
            ShopErpSaleOutboundItem row = entry.getValue();
            Integer quantity = row.getQuantity();
            if (quantity == null || quantity <= 0) {
                throw new ServiceException("请填写「" + line.displayName() + "」的退货数量");
            }
            if (quantity > line.returnable()) {
                throw new ServiceException("「" + line.displayName() + "」这张采购单已入库 " + line.received
                        + " 件、已登记退货 " + line.returned + " 件，本次最多还能退 " + line.returnable() + " 件");
            }
            if (row.getWarehouseId() == null) {
                throw new ServiceException("请选择「" + line.displayName() + "」从哪个仓退回");
            }
            // 下拉里的候选只有启用中的仓，但退货弹窗可能开着不动、那边把仓置成了盘点中/调拨中，
            // 提交时按这一格再判一次：登记一张从冻结仓退货的出库单，出库那一步必然打回来
            ShopErpWarehouse warehouse = warehouseMapper.selectById(row.getWarehouseId());
            if (warehouse == null) {
                throw new ServiceException("「" + line.displayName() + "」的退回仓库不存在或已被删除");
            }
            if (WarehouseUtils.frozen(warehouse)) {
                throw new ServiceException(WarehouseUtils.frozenMessage(warehouse, "退回"));
            }
            // 成本价默认取这条规格的加权采购单价，前端填了就用填的（退货按进价结，不是该仓的加权成本）
            BigDecimal cost = row.getCostPrice() == null ? line.unitCost() : row.getCostPrice();
            if (cost.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("「" + line.displayName() + "」的退货单价不能为负");
            }
            BigDecimal amount = cost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

            ShopErpSaleOutboundItem item = new ShopErpSaleOutboundItem();
            item.setSkuId(line.skuId);
            item.setWarehouseId(row.getWarehouseId());
            // order_item_id 留空：这一行对不上任何订单明细（退的是采购单进来的货），行按 规格 + 仓库 记
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
        doc.setOutboundSource(ShopErpSaleOutbound.SOURCE_PURCHASE_RETURN);
        doc.setOrderId(purchase.getId());
        doc.setOrderNo(purchase.getPurchaseNo());
        doc.setStatus(OUTBOUND_STATUS_PENDING);
        doc.setTotalQuantity(totalQuantity);
        doc.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        doc.setRemark(StringUtils.trimToNull(form.getRemark()));
        // user_id 不写：这一列在出库单上是"谁点的出库"，这张单此刻还没出库（那个动作会补上）
        try {
            saleOutboundMapper.insert(doc);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("出库单号生成冲突，请重试");
        }
        for (ShopErpSaleOutboundItem item : items) {
            item.setOutboundId(doc.getId());
            saleOutboundItemMapper.insert(item);
        }
        return doc.getId();
    }

    /**
     * 退货这一路的前置校验：单子在不在 + 是不是我负责的（口径同 selectPage / update，
     * 堵住直接拿别人单号打接口这条路）。状态不在这里判：待审核的单子本来就没有已入库的货，
     * 走到 {@link #returnLines} 那一层自然是"没有可退的规格"
     */
    private ShopErpPurchase requireReturnable(Long purchaseId) {
        if (purchaseId == null) {
            throw new ServiceException("采购单ID不能为空");
        }
        ShopErpPurchase purchase = baseMapper.selectPurchaseById(purchaseId);
        if (purchase == null) {
            throw new ServiceException("采购单不存在");
        }
        assertOwn(purchase);
        return purchase;
    }

    /**
     * 这张采购单按规格（sku_id）汇总出来的退货额度（LinkedHashMap 保住采购明细的行序，弹窗里规格顺序和采购单一致）
     *
     * 三股数拼起来：
     * 1. 已入库数 —— 采购明细自己的 received_quantity（由入库动作累加），同一规格的几行相加；
     *    顺带把加权采购单价算出来（Σ amount ÷ Σ quantity），当退货单价的默认值
     * 2. 已登记退货数 —— 这张单名下那些 来源=3 的出库单明细的 quantity 合计（见 sumReturnedBySku）
     * 3. 默认仓库 —— 这条规格当初从哪家仓入的货最多（见 mainWarehouseBySku）
     *
     * 和入库那边的 selectPurchaseItems 是镜像的口径（那儿的"可入库数"= 采购量 − 其它入库单已登记的量）
     */
    private Map<Long, ReturnLine> returnLines(Long purchaseId) {
        Map<Long, ReturnLine> lines = new LinkedHashMap<>();
        for (ShopErpPurchaseItem item : itemMapper.selectItemsByPurchaseId(purchaseId)) {
            ReturnLine line = lines.get(item.getSkuId());
            if (line == null) {
                line = new ReturnLine(item.getSkuId());
                lines.put(item.getSkuId(), line);
            }
            line.quantity += item.getQuantity() == null ? 0 : item.getQuantity();
            line.received += item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
            line.cost = line.cost.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
            if (line.productName == null) {
                line.productName = item.getProductName();
                line.productCode = item.getProductCode();
                // 规格文本和名称是同一跳联表带出来的（明细行只记 sku_id，文本在 shop_product_sku 那一行上）
                line.specText = item.getSpecText();
            }
        }
        Map<Long, Integer> returned = sumReturnedBySku(purchaseId);
        for (Map.Entry<Long, Long> entry : mainWarehouseBySku(purchaseId).entrySet()) {
            ReturnLine line = lines.get(entry.getKey());
            if (line != null) {
                line.warehouseId = entry.getValue();
            }
        }
        for (Map.Entry<Long, ReturnLine> entry : lines.entrySet()) {
            entry.getValue().returned = returned.getOrDefault(entry.getKey(), 0);
        }
        return lines;
    }

    /**
     * 这张采购单名下已登记的退货量（按规格汇总）：先捞出来源是它的退货出库单，再按 outbound_id 汇总明细。
     * 两次查询、不联表 —— 一个采购单名下的退货单撑死几张（口径同 sumReceivedBySku，那边也是这么算额度的）
     *
     * 汇总的是**登记数 quantity** 而不是实出数 outbound_quantity：待出库的退货单也得占额度，
     * 不然两张单子各按"还没退"登记一遍，加起来就把同一批货退了两遍。
     * 刻意不按登录账号收窄：这是这张采购单自己的额度，别人登记的必须算进来，否则两个操作员能各退一遍。
     * 软删的退货单不在这里（wrapper 查询走全局逻辑删除）—— 删掉一张退货单就等于把它的额度还回来，
     * 而"出过货的退货单"在商品出库那边本来就删不掉（deleteByIds 三道闸）
     */
    private Map<Long, Integer> sumReturnedBySku(Long purchaseId) {
        Map<Long, Integer> returned = new HashMap<>();
        List<ShopErpSaleOutbound> docs = saleOutboundMapper.selectList(new LambdaQueryWrapper<ShopErpSaleOutbound>()
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
        for (ShopErpSaleOutboundItem item : saleOutboundItemMapper.selectList(
                new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                        .select(ShopErpSaleOutboundItem::getSkuId, ShopErpSaleOutboundItem::getQuantity)
                        .in(ShopErpSaleOutboundItem::getOutboundId, docIds))) {
            returned.merge(item.getSkuId(), item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        return returned;
    }

    /**
     * 每条规格"当初主要从哪家仓入库"：拿这张采购单名下的入库单（连明细）按 (规格, 仓库) 汇总实入数，
     * 取最多的那一仓当退货单的默认仓库。只是默认值，弹窗里那一格能改
     *
     * 取的是入库单的 warehouse_id（主表列，一张入库单一个仓）× 明细的 received_quantity（实际入进来的数），
     * 只登记还没入库的行不算 —— 那些货还没进仓，从那儿退必然是空的
     */
    private Map<Long, Long> mainWarehouseBySku(Long purchaseId) {
        Map<Long, Map<Long, Integer>> bySku = new HashMap<>();
        for (ShopErpPurchaseIn receipt : purchaseInService.selectPurchaseInByPurchase(purchaseId)) {
            if (receipt.getWarehouseId() == null || receipt.getItems() == null) {
                continue;
            }
            for (ShopErpPurchaseInItem line : receipt.getItems()) {
                int received = line.getReceivedQuantity() == null ? 0 : line.getReceivedQuantity();
                if (received <= 0) {
                    continue;
                }
                bySku.computeIfAbsent(line.getSkuId(), key -> new HashMap<>())
                        .merge(receipt.getWarehouseId(), received, Integer::sum);
            }
        }
        Map<Long, Long> warehouseBySku = new HashMap<>();
        Set<Long> frozen = frozenWarehouseIds(bySku);
        for (Map.Entry<Long, Map<Long, Integer>> entry : bySku.entrySet()) {
            Long best = null;
            int bestQuantity = 0;
            for (Map.Entry<Long, Integer> candidate : entry.getValue().entrySet()) {
                // 盘点中/调拨中的仓不当默认值：那几家仓的货这会儿动不了，填进去只会被提交那一拍打回来，
                // 宁可留空让操作员在弹窗里另挑一家（下拉里只有启用中的仓）
                if (frozen.contains(candidate.getKey())) {
                    continue;
                }
                // 并列时取先碰到的那一家（入库单是新单在前，就是"最近一次入得多"的那批里先排到的）
                if (candidate.getValue() > bestQuantity) {
                    best = candidate.getKey();
                    bestQuantity = candidate.getValue();
                }
            }
            if (best != null) {
                warehouseBySku.put(entry.getKey(), best);
            }
        }
        return warehouseBySku;
    }

    /** 候选仓里被冻住的那几家（盘点中/调拨中）：一次按主键批量查，别按规格反复查库 */
    private Set<Long> frozenWarehouseIds(Map<Long, Map<Long, Integer>> bySku) {
        Set<Long> ids = new HashSet<>();
        for (Map<Long, Integer> candidates : bySku.values()) {
            ids.addAll(candidates.keySet());
        }
        Set<Long> frozen = new HashSet<>();
        if (ids.isEmpty()) {
            return frozen;
        }
        for (ShopErpWarehouse warehouse : warehouseMapper.selectBatchIds(ids)) {
            if (WarehouseUtils.frozen(warehouse)) {
                frozen.add(warehouse.getId());
            }
        }
        return frozen;
    }

    /** 退货额度的一行（一行 = 一条规格，只在退货草稿和建单这一条链上用，不出服务、不落库） */
    private static final class ReturnLine {

        private final Long skuId;
        private String productName;
        private String productCode;
        /** 规格文本（采购明细联 shop_product_sku 带出来的，同一个商品的几行靠它分得开） */
        private String specText;
        /** 采购数量合计（只用来算加权采购单价） */
        private int quantity;
        /** 已入库合计 */
        private int received;
        /** 这张单名下退货单已登记的退货量合计 */
        private int returned;
        /** 这些规格的采购金额合计（Σ 明细 amount） */
        private BigDecimal cost = BigDecimal.ZERO;
        /** 默认退货仓库：这条规格主要从哪家仓入的库，没有入库记录时为 null */
        private Long warehouseId;

        private ReturnLine(Long skuId) {
            this.skuId = skuId;
        }

        /** 还能退多少 = 已入库 − 已登记退货（负数按 0，退货单把额度占超了也不给负数） */
        private int returnable() {
            return Math.max(received - returned, 0);
        }

        /** 加权采购单价 = Σ 采购金额 ÷ Σ 采购数量（同一规格几行不同价时取这一版） */
        private BigDecimal unitCost() {
            if (quantity <= 0) {
                return BigDecimal.ZERO;
            }
            return cost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }

        /** 报错文案里那一格：有规格文本就报「商品名 规格」，默认规格（spec_text 为空）退化成只报商品名；
         *  规格或所属商品被删过联表就是空的，退到 规格 #id */
        private String displayName() {
            if (StringUtils.isBlank(productName)) {
                return "规格 #" + skuId;
            }
            String spec = StringUtils.trimToEmpty(specText);
            return spec.isEmpty() ? productName : productName + " " + spec;
        }
    }


    /**
     * 这张单是不是已经"过了待审核"（status 有值且不是 0）：过了就整单冻结，改和删都拦。
     * 已审核(1) / 待入库(4) / 已入库(2) / 已退货(3) 全在这一刀后面 —— 已退货的也不给删，
     * 留个凭证。status 为 null 的行按待审核放行（列本身 NOT NULL DEFAULT 0，正常不会出现）
     */
    private boolean isFrozen(ShopErpPurchase purchase) {
        Integer status = purchase.getStatus();
        return status != null && status != STATUS_PENDING;
    }

    /**
     * ERP 账号的归属校验，口径同 selectPage：非 ERP 直接放过（平台等看全部、也管全部）。
     * 这里补的是"绕过列表直接拿别人单号打接口"那条路。
     * user_id 为 NULL 的老单（平台录的、没指定操作员）ERP 账号本来就看不到，这里也一律改不动。
     */
    private void assertOwn(ShopErpPurchase purchase) {
        if (!LoginUserUtils.isErp()) {
            return;
        }
        Long userId = purchase.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张采购单不是你负责的，无权查看或修改");
        }
    }

    /**
     * 明细落库：purchaseId 一律以主表 id 为准（请求体里带什么都不认），金额已经由 recalculate 算好，
     * 已入库数量已经由 insert（写 0）/ carryReceivedQuantity（从库里搬）定好
     */
    private void saveItems(ShopErpPurchase purchase) {
        for (ShopErpPurchaseItem item : purchase.getItems()) {
            // 详情接口的字段会被原样塞回表单，明细带着原来的 id 回来；不清零 INSERT 就会显式带上主键
            item.setId(null);
            item.setPurchaseId(purchase.getId());
            itemMapper.insert(item);
        }
    }

    private void removeItems(Long purchaseId) {
        itemMapper.delete(new LambdaQueryWrapper<ShopErpPurchaseItem>()
                .eq(ShopErpPurchaseItem::getPurchaseId, purchaseId));
    }

    /**
     * 改单时把库里已有的"已入库数量"搬回这次要重存的明细行：请求体里那一列一律不认
     * （页面上已经改成只读回显）。
     *
     * 非搬不可的原因：明细是整单重存（removeItems + saveItems 按请求体插），不搬就等于把
     * 已经入进去的 6 件写成 0 —— 入库进度、单据状态全跟着错，同一批货还能再入一遍。
     *
     * 同一个规格可以出现在几行里（validate 没拦重复行），所以先按 sku_id 汇总库里那份，
     * 再按行序分摊到这次的新行上；分摊口径和入库时的累加（入库单服务也是按行序分回明细）对齐。
     */
    private void carryReceivedQuantity(ShopErpPurchase purchase) {
        Map<Long, Integer> received = new HashMap<>();
        for (ShopErpPurchaseItem old : itemMapper.selectList(new LambdaQueryWrapper<ShopErpPurchaseItem>()
                .eq(ShopErpPurchaseItem::getPurchaseId, purchase.getId())
                .orderByAsc(ShopErpPurchaseItem::getId))) {
            received.merge(old.getSkuId(), old.getReceivedQuantity() == null ? 0 : old.getReceivedQuantity(),
                    Integer::sum);
        }
        Map<Long, Integer> planned = new HashMap<>();
        for (ShopErpPurchaseItem item : purchase.getItems()) {
            planned.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        // 货已经进仓了，不能靠改单把它变没：这条规格的采购总量不能小于已入库量（删行、改小都算）
        for (Map.Entry<Long, Integer> entry : received.entrySet()) {
            int already = entry.getValue();
            int plan = planned.getOrDefault(entry.getKey(), 0);
            if (plan < already) {
                throw new ServiceException("规格「" + skuDisplayName(entry.getKey()) + "」已入库 " + already
                        + " 件，采购数量不能改到比它小，也不能删掉这行；货入错了要去库存里冲账");
            }
        }
        for (ShopErpPurchaseItem item : purchase.getItems()) {
            int left = received.getOrDefault(item.getSkuId(), 0);
            int take = Math.min(left, item.getQuantity());
            item.setReceivedQuantity(take);
            received.put(item.getSkuId(), left - take);
        }
    }

    /** 只给报错文案用：这条规格的「商品名 规格文本」（明细行上只有 sku_id，名称要经 SKU 那一跳取），
     *  规格或所属商品真被删了就退回 #id —— 只在抛错那一路调，最多两跳各查一次，不参与正常流程的批量查 */
    private String skuDisplayName(Long skuId) {
        ShopProductSku sku = productSkuMapper.selectById(skuId);
        ShopProduct product = sku == null ? null : productMapper.selectById(sku.getProductId());
        if (product == null) {
            return "#" + skuId;
        }
        String specText = StringUtils.trimToEmpty(sku.getSpecText());
        return specText.isEmpty() ? product.getProductName() : product.getProductName() + " " + specText;
    }

    /**
     * 按明细重算三个"服务端说了算"的数：每行 amount = 数量 × 单价，主表 total 累加。
     * 单价/数量的合法性在 validate 里已经保证，所以这里不做 null 兜底；状态为空按"待审核"。
     */
    private void recalculate(ShopErpPurchase purchase) {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (ShopErpPurchaseItem item : purchase.getItems()) {
            item.setAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            quantity += item.getQuantity();
            amount = amount.add(item.getAmount());
        }
        purchase.setTotalQuantity(quantity);
        purchase.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP));
        if (purchase.getStatus() == null) {
            purchase.setStatus(0);
        }
    }

    /**
     * 状态第一次进入 1/4/2 时补对应时间，其余情况一律留 null。
     * 留 null 而不是回填旧值是有意的：audit_time / receive_time 用的是全局 not_empty（不是 IGNORED），
     * null 就整列不进 UPDATE 的 SET，等于"已记过的时间不会被改单改掉"。
     */
    private void applyStatusTimes(ShopErpPurchase purchase, ShopErpPurchase exists) {
        if (purchase.getStatus() == null) {
            return;
        }
        int status = purchase.getStatus();
        LocalDateTime now = LocalDateTime.now();
        // 4 待入库是入库那条链推出来的（见 ShopErpPurchaseInServiceImpl#refreshPurchaseStatus），
        // 改单时表单里那格原样提交回来，也要按"已经审核过"对待，别把 audit_time 留成空
        boolean needAudit = status == STATUS_AUDITED || status == STATUS_WAIT_IN || status == STATUS_RECEIVED;
        if (needAudit && (exists == null || exists.getAuditTime() == null)) {
            purchase.setAuditTime(now);
        }
        if (status == STATUS_RECEIVED && (exists == null || exists.getReceiveTime() == null)) {
            purchase.setReceiveTime(now);
        }
    }

    /**
     * 主表 + 明细一起校验：供应商和所属门店都必填、要真有其人，明细必须至少一行，每行的规格要真有其（所属商品
     * 也得在）、数量要正、单价不能为负。
     * 规格/商品/供应商/门店只查存在性，不联表回名称（名称是查询时现联的，见 XML）。
     * 已入库数量不在这儿校验：请求体里的值根本不进库，由 carryReceivedQuantity 从库里搬。
     */
    private void validate(ShopErpPurchase purchase, Long excludeId) {
        if (StringUtils.isBlank(purchase.getPurchaseNo())) {
            throw new ServiceException("采购单号不能为空");
        }
        purchase.setPurchaseNo(purchase.getPurchaseNo().trim());
        if (purchase.getSupplierId() == null) {
            throw new ServiceException("请选择供应商");
        }
        if (supplierMapper.selectById(purchase.getSupplierId()) == null) {
            throw new ServiceException("所选供应商不存在");
        }
        // 所属门店必填：建入库单时「入库仓库」的候选是按这张单的门店收窄的，没门店就定不了货往哪家仓放。
        // 列本身留 NULL 可空（库里那几张老单没这个信息，不加 NOT NULL），所以这道闸不能指望数据库
        if (purchase.getStoreId() == null) {
            throw new ServiceException("请选择所属门店");
        }
        if (storeMapper.selectById(purchase.getStoreId()) == null) {
            throw new ServiceException("所选所属门店不存在");
        }
        // 操作员填了就必须真有其人（口径同供应商的绑定账号）；账号类型只在 UI 收窄，服务端不校验类型
        if (purchase.getUserId() != null && sysUserMapper.selectById(purchase.getUserId()) == null) {
            throw new ServiceException("所选ERP操作员不存在");
        }
        List<ShopErpPurchaseItem> items = purchase.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("采购明细不能为空，至少添加一行商品规格");
        }
        // 明细行只记 sku_id，"规格在不在、所属商品在不在"是两跳。一单几十行，逐行 selectById 就是 2×N 次查询，
        // 所以这里批量捞两次：先按 sku_id 捞 SKU，再按 SKU 上的 product_id 捞商品，行上只从这两个 Map 里取
        List<Long> skuIds = items.stream().map(ShopErpPurchaseItem::getSkuId).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        Map<Long, ShopProductSku> skuById = skuIds.isEmpty() ? Map.of()
                : productSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ShopProductSku::getId, sku -> sku));
        List<Long> productIds = skuById.values().stream().map(ShopProductSku::getProductId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ShopProduct> productById = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ShopProduct::getId, product -> product));
        for (int i = 0; i < items.size(); i++) {
            ShopErpPurchaseItem item = items.get(i);
            String row = "第 " + (i + 1) + " 行明细";
            if (item.getSkuId() == null) {
                throw new ServiceException(row + "：请选择商品规格");
            }
            // selectBatchIds 带 is_deleted = 0，所以真删过的规格/商品查不出来；下架商品（status=0）行还在，仍然可以采购
            ShopProductSku sku = skuById.get(item.getSkuId());
            if (sku == null) {
                throw new ServiceException(row + "：所选商品规格不存在或已被删除");
            }
            if (productById.get(sku.getProductId()) == null) {
                throw new ServiceException(row + "：这条规格所属的商品不存在或已被删除");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new ServiceException(row + "：采购数量必须大于 0");
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException(row + "：采购单价不能为空且不能为负");
            }
        }
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpPurchase>()
                .eq(ShopErpPurchase::getPurchaseNo, purchase.getPurchaseNo())
                .ne(excludeId != null, ShopErpPurchase::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("采购单号「" + purchase.getPurchaseNo() + "」已存在");
        }
    }
}
