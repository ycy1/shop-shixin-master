package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpSale;
import com.shop.entity.ShopErpSaleItem;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopStore;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpSaleItemMapper;
import com.shop.mapper.ShopErpSaleMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopErpSaleOutboundService;
import com.shop.service.ShopErpSaleService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.product.ShopProductVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 对外销售单 服务实现（形状照采购单那套：主子表整单重存，四组数由服务端说了算）
 *
 * 四件事不由表单决定：
 *   1. sale_no —— 新增不传就生成 SALE_+时间戳；
 *   2. total_amount / total_quantity 和明细的 amount —— 一律按明细重算（recalculate）；
 *   3. audit_time / ship_time —— 状态第一次进入 1/3 时补，之后不覆盖（applyStatusTimes）；
 *   4. 明细的 shipped_quantity —— 只由出库动作累加，改单时从库里搬回新行（carryShippedQuantity）。
 *
 * 单据只有「待审核（0）」这一档能改（update 一道闸 isFrozen）：审核过就等于对客户承诺过了，
 * 之后要动只能作废重开（在修改弹窗里把状态选成 4，选完这张单就冻结、但还留着当凭证）。
 * 删除放宽一档：0 待审核 和 4 已取消 能删 —— 这两档都还没建过出库单（建出库单只认 1/2，
 * 见 ShopErpSaleOutboundServiceImpl#insert），删了不会留下引用悬空的孤儿单。
 *
 * 状态里只有 0待审核 / 1已审核 / 4已取消 是人在这一页选的；2 待出库 和 3 已出库 由出库那条链按进度回写
 * （ShopErpSaleOutboundServiceImpl#refreshSaleStatus，改单后这里也重算一次）：
 * 每条规格的销售量都被名下出库单登记满了就是 2，还有规格没登记满退回 1，实出数追平销售量才是 3。
 * 「商品出库」新建单时的销售单下拉只列 1 已审核 那一张（=2 说明每行都已经被名下出库单登记满了，
 * 再建一张也是 0 量，口径同入库单那边只列 1 已审核 的采购单）。
 *
 * 明细一行是一条规格（shop_erp_sale_item.sku_id → shop_product_sku.id），要商品名得走两跳：
 * SKU 那一行上有单价/规格文本/能不能单卖，名称/编码/门店归属还在 shop_product 那一行。
 * 明细的候选按门店收窄（selectProductOptions）：只有"属于这家门店 + 这条规格标了可销售"的规格能进这张单；
 * 表单上选了发货仓库，候选还要再过一档"那个仓里有这一条规格的可用库存"（EXISTS 到 shop_erp_inventory，见 XML）。
 * 这一格门店（store_id）是这张单的口径线，而门店挂在规格所属的那件商品上，所以 validate 里会复核每行规格
 * 对应的商品确实属于这家门店；「可销售」和"这个仓有没有货"都只在候选里挡，不进 validate ——
 * 规格后来被标成不可销售、仓里的货被线上订单锁走，都不该把已经开出去的单子卡死改不了
 * （发货仓库本来就可以不选，出库时才按明细那几行定仓）。
 *
 * 数据范围：ERP 操作员账号（account_type=5）只看/只改 user_id = 自己那几条，其它账号（平台、门店等）看全部。
 * user_id 是表单里的「ERP操作员」，所以实体上是 IGNORED（换绑、清空都要能写回库）；ERP 账号自己改不动，钉回原值。
 */
@Service
@RequiredArgsConstructor
public class ShopErpSaleServiceImpl extends ServiceImpl<ShopErpSaleMapper, ShopErpSale>
        implements ShopErpSaleService {

    /** 状态：待审核（字典 sale_status）。只有这一档的单子还能改，见 update */
    private static final int STATUS_PENDING = 0;

    /** 状态：已审核（第一次进入时补 audit_time） */
    private static final int STATUS_AUDITED = 1;

    /** 状态：待出库（货全登记进出库单了、还没出满，由出库那边推出来，不是人手选的） */
    private static final int STATUS_WAIT_OUT = 2;

    /** 状态：已出库（每行实出数追平销售量，第一次进入时补 ship_time） */
    private static final int STATUS_SHIPPED = 3;

    /** 状态：已取消（待审核时作废。列是手填的，所以作废后这张单既改不动也出不了货） */
    private static final int STATUS_CANCELLED = 4;

    /** 明细候选一次最多返回多少条（远程搜索的下拉，同采购单）。现在一行是一条规格，所以卡的也是规格行数 */
    private static final int PRODUCT_OPTION_LIMIT = 20;

    private final ShopErpSaleItemMapper itemMapper;

    /** 改单后重算这张单的状态（销售量一改，名下出库单登记得满不满就得重新对一遍） */
    private final ShopErpSaleOutboundService saleOutboundService;

    private final ShopStoreMapper storeMapper;

    private final ShopErpWarehouseMapper warehouseMapper;

    /** 明细那两跳的第二跳：门店归属、商品名都在这一侧，也是"这条规格所属商品还在不在"的闸门 */
    private final ShopProductMapper productMapper;

    /** 明细那两跳的第一跳：明细行只记 sku_id，单价/规格文本/能不能单卖在这一行上 */
    private final ShopProductSkuMapper productSkuMapper;

    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopErpSale> selectPage(ShopErpSale query) {
        if (query == null) {
            query = new ShopErpSale();
        }
        // 一律覆盖 userId：ERP 账号换成登录 id，其它账号置空。列表上没有"按操作员筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人负责的单子
        query.setUserId(LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null);
        return baseMapper.selectSalePage(PageUtil.getPage(), query);
        // 列表不带明细：一单几十行的子表跟着分页查会打出 N+1，要看明细点开修改弹窗（同采购单）
    }

    @Override
    public List<ShopProductVo> selectProductOptions(Long storeId, Long warehouseId, String keyword) {
        // 候选一行是一条规格：VO 上回显的 skuId / specText / productPrice 都是这一条规格自己的值，
        // 选下来落进明细那行的是 skuId。判据全在 XML（门店 + 这条规格的 sale_enable + 选了仓库时
        // 该仓这一条规格的可用库存），这里只复核入口那两格真有其人
        if (storeId == null) {
            throw new ServiceException("请先选择销售门店");
        }
        if (storeMapper.selectById(storeId) == null) {
            throw new ServiceException("所选销售门店不存在");
        }
        // 发货仓库可以不选（"出库时确定"），选了才查存在性，口径同 validate
        if (warehouseId != null && warehouseMapper.selectById(warehouseId) == null) {
            throw new ServiceException("所选发货仓库不存在");
        }
        return baseMapper.selectProductOptions(storeId, warehouseId, StringUtils.trimToNull(keyword),
                PRODUCT_OPTION_LIMIT);
    }

    @Override
    public ShopErpSale selectSaleById(Long id) {
        ShopErpSale sale = baseMapper.selectSaleById(id);
        if (sale == null) {
            throw new ServiceException("销售单不存在");
        }
        assertOwn(sale);
        sale.setItems(itemMapper.selectItemsBySaleId(id));
        return sale;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopErpSale sale) {
        // 新增时表单那栏是禁用的、不会传单号，和采购单号一样由系统生成
        if (StringUtils.isBlank(sale.getSaleNo())) {
            sale.setSaleNo(GeneratorNoUtils.getSaleNo());
        }
        validate(sale, null);
        // 新增的单还没出过货，这一列只能由出库动作往后累加，所以一律写 0（同样不认请求体）
        for (ShopErpSaleItem item : sale.getItems()) {
            item.setShippedQuantity(0);
        }
        recalculate(sale);
        applyStatusTimes(sale, null);
        // 负责的操作员：ERP 账号录的单只能记自己名下。其它账号没指定就留 NULL（= 还没指派），
        // 不能兜底成登录人 —— 这列的语义是"哪个 ERP 操作员负责"，不是创建人留痕（那个看 create_by）
        if (LoginUserUtils.isErp()) {
            sale.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(sale);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_sale_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("销售单号「" + sale.getSaleNo()
                    + "」已被占用，可能来自一张已删除的销售单");
        }
        saveItems(sale);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopErpSale sale) {
        if (sale.getId() == null) {
            throw new ServiceException("销售单ID不能为空");
        }
        ShopErpSale exists = getById(sale.getId());
        if (exists == null) {
            throw new ServiceException("销售单不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 只有「待审核」的单子还能改：审核过就等于对客户承诺过了（出库单是照着它建的），
        // 这时再改明细/数量，名下出库单登记的销售量就对不上。「审核」和「作废」这两个动作本身
        // 还是在待审核那张单的「修改」弹窗里选状态，选完这张单就冻结
        if (isFrozen(exists)) {
            throw new ServiceException("销售单「" + exists.getSaleNo() + "」已经不在待审核状态，不能再修改");
        }
        // 操作员只有非 ERP 账号能换；ERP 账号钉回原值，免得改个备注把单子挪到别人名下
        if (LoginUserUtils.isErp()) {
            sale.setUserId(exists.getUserId());
        }
        // 单号允许不传（表单里是只读的），不传时沿用原值参与查重
        if (StringUtils.isBlank(sale.getSaleNo())) {
            sale.setSaleNo(exists.getSaleNo());
        }
        validate(sale, sale.getId());
        carryShippedQuantity(sale);
        recalculate(sale);
        applyStatusTimes(sale, exists);
        try {
            updateById(sale);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("销售单号「" + sale.getSaleNo() + "」已被占用，可能来自一张已删除的销售单");
        }
        // 明细整单重存：先把这单现有的软删掉再插新的。
        // 没有 (sale_id, sku_id) 唯一键，所以重插不会撞逻辑删除留下的旧行
        removeItems(sale.getId());
        saveItems(sale);
        // 销售量、行数、规格一改，名下出库单登记得满不满就换了个说法：状态按新明细重算
        // （待审核这一档名下不会有出库单，所以这一步基本是空转，留着是为了别处改完单不用记这条）
        saleOutboundService.refreshSaleStatus(sale.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 一次按 id 捞出来：归属校验和状态校验都用这份库里的行，请求体里带什么都不算数
        List<ShopErpSale> sales = listByIds(ids);
        // ERP 账号删的必须是自己负责的：逐条校验，堵住直接拿别人单号打接口这条路
        if (LoginUserUtils.isErp()) {
            for (ShopErpSale sale : sales) {
                assertOwn(sale);
            }
        }
        // 待审核 / 已取消 之外都不给删：出库单只认 1已审核 / 2待出库 那两张（见 ShopErpSaleOutboundServiceImpl#insert），
        // 所以这两档名下必然没有出库单，删了不会留孤儿；出过货的单子是发货凭证，留着
        for (ShopErpSale sale : sales) {
            if (!canDelete(sale)) {
                throw new ServiceException("销售单「" + sale.getSaleNo()
                        + "」已经审核过，不能删除（要撤下来只能在待审核时作废）");
            }
        }
        removeByIds(ids);
        // 明细跟着一起软删：留着孤儿行，以后按 sale_id 统计销售量就会把已删除单的行算进去
        itemMapper.delete(new LambdaQueryWrapper<ShopErpSaleItem>()
                .in(ShopErpSaleItem::getSaleId, ids));
    }

    /** 是不是已经"过了待审核"（status 有值且不是 0）：过了就整单冻结，改拦下来（口径同采购单） */
    private boolean isFrozen(ShopErpSale sale) {
        Integer status = sale.getStatus();
        return status != null && status != STATUS_PENDING;
    }

    /** 能不能删：待审核 和 已取消 这两档放行（判据见 deleteByIds） */
    private boolean canDelete(ShopErpSale sale) {
        Integer status = sale.getStatus();
        return status == null || status == STATUS_PENDING || status == STATUS_CANCELLED;
    }

    /**
     * ERP 账号的归属校验，口径同 selectPage：非 ERP 直接放过（平台等看全部、也管全部）。
     * 这里补的是"绕过列表直接拿别人单号打接口"那条路。
     * user_id 为 NULL 的老单（平台录的、没指定操作员）ERP 账号本来就看不到，这里也一律改不动。
     */
    private void assertOwn(ShopErpSale sale) {
        if (!LoginUserUtils.isErp()) {
            return;
        }
        Long userId = sale.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张销售单不是你负责的，无权查看或修改");
        }
    }

    /**
     * 明细落库：saleId 一律以主表 id 为准（请求体里带什么都不认），金额已由 recalculate 算好，
     * 已出库数量已经由 insert（写 0）/ carryShippedQuantity（从库里搬）定好
     */
    private void saveItems(ShopErpSale sale) {
        for (ShopErpSaleItem item : sale.getItems()) {
            // 详情接口的字段会被原样塞回表单，明细带着原来的 id 回来；不清零 INSERT 就会显式带上主键
            item.setId(null);
            item.setSaleId(sale.getId());
            itemMapper.insert(item);
        }
    }

    private void removeItems(Long saleId) {
        itemMapper.delete(new LambdaQueryWrapper<ShopErpSaleItem>()
                .eq(ShopErpSaleItem::getSaleId, saleId));
    }

    /**
     * 改单时把库里已有的"已出库数量"搬回这次要重存的明细行：请求体里那一列一律不认。
     *
     * 非搬不可的原因：明细是整单重存（removeItems + saveItems 按请求体插），不搬就等于把
     * 已经出出去的货写成 0 —— 出库进度、单据状态全跟着错，同一批货还能再出一遍。
     *
     * 同一条规格可以出现在几行里（validate 没拦重复行），所以先按 sku_id 汇总库里那份，
     * 再按行序分摊到这次的新行上（分摊口径同入库那边的 carryOverReceived）
     */
    private void carryShippedQuantity(ShopErpSale sale) {
        Map<Long, Integer> shippedBySku = new HashMap<>();
        for (ShopErpSaleItem old : itemMapper.selectList(new LambdaQueryWrapper<ShopErpSaleItem>()
                .eq(ShopErpSaleItem::getSaleId, sale.getId())
                .orderByAsc(ShopErpSaleItem::getId))) {
            shippedBySku.merge(old.getSkuId(), old.getShippedQuantity() == null ? 0 : old.getShippedQuantity(),
                    Integer::sum);
        }
        Map<Long, Integer> plannedBySku = new HashMap<>();
        for (ShopErpSaleItem item : sale.getItems()) {
            plannedBySku.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        // 货已经出仓了，不能靠改单把它变没：这条规格的销售总量不能小于已出库量（删行、改小都算）
        for (Map.Entry<Long, Integer> entry : shippedBySku.entrySet()) {
            int already = entry.getValue();
            int plan = plannedBySku.getOrDefault(entry.getKey(), 0);
            if (plan < already) {
                throw new ServiceException("商品规格「" + skuLabel(entry.getKey()) + "」已出库 " + already
                        + " 件，销售数量不能改到比它小，也不能删掉这行；货出错了要去库存里冲账");
            }
        }
        for (ShopErpSaleItem item : sale.getItems()) {
            int left = shippedBySku.getOrDefault(item.getSkuId(), 0);
            int take = Math.min(left, item.getQuantity());
            item.setShippedQuantity(take);
            shippedBySku.put(item.getSkuId(), left - take);
        }
    }

    /**
     * 只给报错文案用：这一行报的是「商品名 + 规格文本」（默认规格的 spec_text 为 NULL，那就只留商品名），
     * 走的是 sku_id → shop_product_sku → shop_product 那两跳；规格不在（或它那一格所属商品是空的）、
     * 商品真被删了，都退回 #skuId。
     * 光给一个 id 操作员对不上是哪一行，所以能拼出名字就尽量拼。
     * 只在抛错那一条路径上调用（一张单最多抛一个错），所以这两次单查不会变成循环里的 N+1。
     */
    private String skuLabel(Long skuId) {
        ShopProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || sku.getProductId() == null) {
            return "#" + skuId;
        }
        ShopProduct product = productMapper.selectById(sku.getProductId());
        if (product == null) {
            return "#" + skuId;
        }
        return StringUtils.isBlank(sku.getSpecText())
                ? product.getProductName()
                : product.getProductName() + " / " + sku.getSpecText();
    }

    /**
     * 按明细重算三个"服务端说了算"的数：每行 amount = 数量 × 单价，主表 total 累加。
     * 单价/数量的合法性在 validate 里已经保证，所以这里不做 null 兜底；状态为空按"待审核"。
     */
    private void recalculate(ShopErpSale sale) {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (ShopErpSaleItem item : sale.getItems()) {
            item.setAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            quantity += item.getQuantity();
            amount = amount.add(item.getAmount());
        }
        sale.setTotalQuantity(quantity);
        sale.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP));
        if (sale.getStatus() == null) {
            sale.setStatus(STATUS_PENDING);
        }
    }

    /**
     * 状态第一次进入 1/2/3 时补对应时间，其余情况一律留 null。
     * 留 null 而不是回填旧值是有意的：audit_time / ship_time 用的是全局 not_empty（不是 IGNORED），
     * null 就整列不进 UPDATE 的 SET，等于"已记过的时间不会被改单改掉"。
     *
     * 2 待出库 是出库那条链推出来的（见 ShopErpSaleOutboundServiceImpl#refreshSaleStatus），
     * 改单时表单里那格原样提交回来，也要按"已经审核过"对待，别把 audit_time 留成空；
     * 4 已取消 只可能从待审核作废而来，那张单从没审核过，两个时间都留空
     */
    private void applyStatusTimes(ShopErpSale sale, ShopErpSale exists) {
        if (sale.getStatus() == null) {
            return;
        }
        int status = sale.getStatus();
        boolean needAudit = status == STATUS_AUDITED || status == STATUS_WAIT_OUT || status == STATUS_SHIPPED;
        if (needAudit && (exists == null || exists.getAuditTime() == null)) {
            sale.setAuditTime(LocalDateTime.now());
        }
        if (status == STATUS_SHIPPED && (exists == null || exists.getShipTime() == null)) {
            sale.setShipTime(LocalDateTime.now());
        }
    }

    /**
     * 主表 + 明细一起校验：门店必填、明细至少一行，每行的规格要真有其人、它所属的商品也要在，
     * 且那件商品属于这家门店（门店挂在 SPU 上，规格是靠 sku_id → shop_product_sku.product_id 这一跳拿到的）、
     * 数量要正、单价不能为负。规格和商品各一次批量捞回来，不在循环里逐行查（一张单几十行明细就是几十次往返）。
     * 「可销售」（shop_product_sku.sale_enable）只在候选下拉里挡，不在这里拦，理由见类注释。
     * 已出库数量不在这儿校验：请求体里的值根本不进库，由 carryShippedQuantity 从库里搬。
     */
    private void validate(ShopErpSale sale, Long excludeId) {
        if (StringUtils.isBlank(sale.getSaleNo())) {
            throw new ServiceException("销售单号不能为空");
        }
        sale.setSaleNo(sale.getSaleNo().trim());
        if (sale.getStoreId() == null) {
            throw new ServiceException("请选择销售门店");
        }
        ShopStore store = storeMapper.selectById(sale.getStoreId());
        if (store == null) {
            throw new ServiceException("所选销售门店不存在");
        }
        // 发货仓库是可以不填的（"出库时确定"），填了才查存在性
        if (sale.getWarehouseId() != null) {
            ShopErpWarehouse warehouse = warehouseMapper.selectById(sale.getWarehouseId());
            if (warehouse == null) {
                throw new ServiceException("所选发货仓库不存在");
            }
        }
        // 操作员填了就必须真有其人（口径同采购单）；账号类型只在 UI 收窄，服务端不校验类型
        if (sale.getUserId() != null && sysUserMapper.selectById(sale.getUserId()) == null) {
            throw new ServiceException("所选ERP操作员不存在");
        }
        List<ShopErpSaleItem> items = sale.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("销售明细不能为空，至少添加一行商品规格");
        }
        // 两跳各一次捞回来：sku_id → shop_product_sku（这一行有单价/规格文本/sale_enable），
        // 再由 shop_product_sku.product_id → shop_product（这一行有名称/编码/store_id）
        List<Long> skuIds = items.stream()
                .map(ShopErpSaleItem::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ShopProductSku> skuMap = skuIds.isEmpty() ? Map.of()
                : productSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ShopProductSku::getId, Function.identity()));
        List<Long> productIds = skuMap.values().stream()
                .map(ShopProductSku::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ShopProduct> productMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ShopProduct::getId, Function.identity()));
        for (int i = 0; i < items.size(); i++) {
            ShopErpSaleItem item = items.get(i);
            String row = "第 " + (i + 1) + " 行明细";
            if (item.getSkuId() == null) {
                throw new ServiceException(row + "：请选择商品规格");
            }
            // selectBatchIds 带 is_deleted = 0，所以真删过的规格查不出来，在 map 里就是缺键
            ShopProductSku sku = skuMap.get(item.getSkuId());
            if (sku == null) {
                throw new ServiceException(row + "：所选商品规格不存在");
            }
            ShopProduct product = sku.getProductId() == null ? null : productMap.get(sku.getProductId());
            if (product == null) {
                throw new ServiceException(row + "：这条规格所属的商品不存在");
            }
            // 门店是这张单的口径线：候选下拉按它筛过，这里再兜一道（走接口直连时也别想混进别家门店的货）
            if (!sale.getStoreId().equals(product.getStoreId())) {
                throw new ServiceException(row + "：商品「" + product.getProductName() + "」不属于门店「"
                        + store.getStoreName() + "」，这条规格不能开进这张销售单");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new ServiceException(row + "：销售数量必须大于 0");
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException(row + "：销售单价不能为空且不能为负");
            }
        }
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpSale>()
                .eq(ShopErpSale::getSaleNo, sale.getSaleNo())
                .ne(excludeId != null, ShopErpSale::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("销售单号「" + sale.getSaleNo() + "」已存在");
        }
    }
}
