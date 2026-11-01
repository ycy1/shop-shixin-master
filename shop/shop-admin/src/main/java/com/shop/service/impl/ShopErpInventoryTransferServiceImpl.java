package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryTransfer;
import com.shop.entity.ShopErpInventoryTransferItem;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopProductSku;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpInventoryTransferItemMapper;
import com.shop.mapper.ShopErpInventoryTransferMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.service.ShopErpInventoryService;
import com.shop.service.ShopErpInventoryTransferService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
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

/**
 * 库存调拨单 服务实现（主子表整单存的写法照 ShopErpPurchaseInServiceImpl，这里只说调拨自己的口径）
 *
 * 1. 这张单**直接动库存**，不经过入库单/出库单：一次「调拨」把调出仓减掉的那一下和调入仓加上的
 *    那一下记在同一笔事务里（ShopErpInventoryService#transferOut / #transferIn），
 *    库存流水也就一次出两行 —— 6 调拨出库 + 5 调拨入库，两行的 biz_id/biz_no 都是这张调拨单。
 *    正因为两侧一次记完，明细的 out_quantity 和 receive_quantity 永远相等，
 *    剩多少没调就是 quantity − out_quantity。
 * 2. 仓库状态由这张单管：新建把两家仓置成 3 调拨中（出入库、下单分仓都会被 WarehouseUtils 那道闸拦掉），
 *    单子走到 3 已完成 / 4 已取消 才复位成 1 启用，而且只有没有其它还在跑（0/1/2）的调拨单引用它才复位
 *    （见 releaseWarehouses）。建单要求两家仓当时都是 1 启用，就是为了这条复位链走得通 ——
 *    不然先完成的那张会把别人那张的冻结解掉，或者把别人手动设的 盘点中/禁用 擅自改成启用。
 * 3. status 不由表单选：新增恒 0 待审核；1 由「审核」推；2 调出中 / 3 已完成 由「调拨」按明细进度推；
 *    4 已取消 由「取消」置。audit_time / out_time / receive_time 各在第一次进对应那档时补，之后不覆盖。
 * 4. 只有 0 待审核 能改能删，判据同采购单/入库单：一进了 1 就可能有货已经挪过仓了，改明细等于把单据
 *    改成和账对不上的样子。取消放宽到 0/1 两档 —— 这两档都还是一笔库存没动。
 * 5. cost_price 不是表单列：建单/改单时从**调出仓**那条库存行带出（带不出就拦，见 validate 那一段），
 *    调拨时它既是这一行的成本口径，也是给调入仓做加权平均的那个单价；每次调拨再按当时那一版的成本价覆盖
 *    （加权是进货算的，调拨只取现有值，见 ShopErpInventoryServiceImpl#moveOut 的返回值）。
 *
 * 数据范围：ERP 操作员账号（account_type=5）只看/只改 user_id = 自己那几张，三件套同入库单：
 * 列表覆盖 userId、读写前 assertOwn、新增钉成登录 id / 修改钉回原值。
 */
@Service
@RequiredArgsConstructor
public class ShopErpInventoryTransferServiceImpl
        extends ServiceImpl<ShopErpInventoryTransferMapper, ShopErpInventoryTransfer>
        implements ShopErpInventoryTransferService {

    /** 明细的规格候选一次最多返回多少条（远程搜索的下拉，同销售单/采购单） */
    private static final int SKU_OPTION_LIMIT = 20;

    private final ShopErpInventoryTransferItemMapper itemMapper;

    /** 建单时校验两家仓存在（且当时是启用），并在这四张单里翻它们的调拨中/启用状态 */
    private final ShopErpWarehouseMapper warehouseMapper;

    /** 只校验操作人存在性 */
    private final SysUserMapper sysUserMapper;

    /** 明细行的规格存在性 + 商品回填（sku_id → product_id 那一跳） */
    private final ShopProductSkuMapper productSkuMapper;

    /** 库存落库的唯一入口：调出/调入各记一下、成本价怎么加权、流水怎么记，全在它那边 */
    private final ShopErpInventoryService inventoryService;

    @Override
    public IPage<ShopErpInventoryTransfer> selectPage(ShopErpInventoryTransfer query) {
        if (query == null) {
            query = new ShopErpInventoryTransfer();
        }
        // 一律覆盖 userId：ERP 账号换成登录 id，其它账号置空。列表上没有"按操作员筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人负责的单子
        query.setUserId(LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null);
        // 关键字跨单号/原因/备注/两家仓名/操作员名搜，条件写在 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectTransferPage(PageUtil.getPage(), query);
        // 列表不带明细：要看明细点开修改弹窗或「调拨」弹窗（同采购单/入库单）
    }

    @Override
    public ShopErpInventoryTransfer selectTransferById(Long id) {
        ShopErpInventoryTransfer transfer = baseMapper.selectTransferById(id);
        if (transfer == null) {
            throw new ServiceException("调拨单不存在");
        }
        assertOwn(transfer);
        // 明细那一趟顺带按调出仓联出"这条规格还剩几件可用"，「调拨」弹窗里那一格要用它
        transfer.setItems(itemMapper.selectItemsByTransferId(id, transfer.getFromWarehouseId()));
        return transfer;
    }

    @Override
    public List<ShopErpInventory> selectSkuOptions(Long warehouseId, String keyword) {
        // 候选一行是调出仓里的一条库存行：判据（这一仓的 + 还有可用库存 + 规格和商品都还在）全在 XML 里。
        // 这里只复核入口那一格：没选调出仓就没有"哪个仓的货"这个口径
        if (warehouseId == null) {
            throw new ServiceException("请先选择调出仓库");
        }
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new ServiceException("所选调出仓库不存在或已被删除");
        }
        return baseMapper.selectSkuOptions(warehouseId, StringUtils.trimToNull(keyword), SKU_OPTION_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopErpInventoryTransfer transfer) {
        // 新增时表单那栏是禁用的、不会传单号，和入库单号一样由系统生成
        if (StringUtils.isBlank(transfer.getTransferNo())) {
            transfer.setTransferNo(GeneratorNoUtils.getTransferNo());
        }
        // 新建才走"两家仓必须是 1 启用"那道闸（validate 按 excludeId 分岔）
        validate(transfer, null);
        // 新建的单子还没有任何进度，已调出/已调入一律 0（顺带把请求体里可能带的值冲掉）
        resetItemProgress(transfer.getItems());
        recalculate(transfer);
        // 状态一律从 0 待审核 起（「审核」那颗按钮才推得到 1）；请求体里的 status 不认
        transfer.setStatus(ShopErpInventoryTransfer.STATUS_PENDING);
        // 三个时间列都从 null 起：钉这一道是防"编辑完一张单再点新增"时，表单里残留的时间被原样插进新单
        transfer.setAuditTime(null);
        transfer.setOutTime(null);
        transfer.setReceiveTime(null);
        // 操作员：ERP 账号录的单只能记自己名下（表单那一格对他禁用了，服务端再钉一道）。
        // 其它账号没指定就留 NULL（= 没记是谁操作的）
        if (LoginUserUtils.isErp()) {
            transfer.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(transfer);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_transfer_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("调拨单号「" + transfer.getTransferNo()
                    + "」已被占用，可能来自一张已删除的调拨单");
        }
        saveItems(transfer);
        // 建完单就把两家仓冻起来：这一张单占着它们，出入库和下单分仓在这一单走完之前都进不来
        freezeWarehouses(transfer.getFromWarehouseId(), transfer.getToWarehouseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopErpInventoryTransfer transfer) {
        if (transfer.getId() == null) {
            throw new ServiceException("调拨单ID不能为空");
        }
        ShopErpInventoryTransfer exists = getById(transfer.getId());
        if (exists == null) {
            throw new ServiceException("调拨单不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 只有待审核能改：一过了这一档就可能已经调出过货（「审核」之后随时能点「调拨」），
        // 改明细等于把单据改成和库存账对不上的样子（口径同采购单/入库单）
        if (exists.getStatus() == null || exists.getStatus() != ShopErpInventoryTransfer.STATUS_PENDING) {
            throw new ServiceException("这张调拨单已经审核过了，不能修改；要挪别的货请重新建一张单");
        }
        // 操作员只有非 ERP 账号能换；ERP 账号钉回原值，免得改个备注把单子挪到别人名下
        if (LoginUserUtils.isErp()) {
            transfer.setUserId(exists.getUserId());
        }
        // 单号允许不传（表单里是只读的），不传时沿用原值参与查重
        if (StringUtils.isBlank(transfer.getTransferNo())) {
            transfer.setTransferNo(exists.getTransferNo());
        }
        // 两家仓钉回库里的值：调拨中的冻结是按这两列盖上去的，换仓等于把上一家的冻结留在别人头上、
        // 又凭空把第三家冻进去。要换仓只能取消这张重开（前端这两格在修改弹窗里也是禁用的）
        boolean changedFrom = transfer.getFromWarehouseId() != null
                && !transfer.getFromWarehouseId().equals(exists.getFromWarehouseId());
        boolean changedTo = transfer.getToWarehouseId() != null
                && !transfer.getToWarehouseId().equals(exists.getToWarehouseId());
        if (changedFrom || changedTo) {
            throw new ServiceException("调拨单不支持换仓库，请取消这张单后按新的两家仓重新建一张");
        }
        transfer.setFromWarehouseId(exists.getFromWarehouseId());
        transfer.setToWarehouseId(exists.getToWarehouseId());
        // 三个时间列同样钉回库里的值（待审核这一档它们必然是 null，钉一道免得请求体伪造）
        transfer.setAuditTime(exists.getAuditTime());
        transfer.setOutTime(exists.getOutTime());
        transfer.setReceiveTime(exists.getReceiveTime());
        // 改单不认请求体里的 status：这一档只能是 0，审核走「审核」那颗按钮
        transfer.setStatus(ShopErpInventoryTransfer.STATUS_PENDING);
        // excludeId 非空：跳过"两家仓必须 1 启用"那道闸（它们现在正被这张单冻成 3 调拨中）
        validate(transfer, transfer.getId());
        // 整单重存会把行的 id 换掉，但这一档的进度必然还是 0（能进到这里说明一笔都没调过），所以直接清零
        resetItemProgress(transfer.getItems());
        recalculate(transfer);
        try {
            updateById(transfer);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("调拨单号「" + transfer.getTransferNo()
                    + "」已被占用，可能来自一张已删除的调拨单");
        }
        // 明细整单重存：先把这单现有的软删掉再插新的。
        // 没有 (transfer_id, sku_id) 唯一键，所以重插不会撞逻辑删除留下的旧行
        removeItems(transfer.getId());
        saveItems(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先按 id 捞出要删的几张：一是给归属校验和状态判据用，二是删完得知道该把哪几家仓腾出来
        List<ShopErpInventoryTransfer> transfers = listByIds(ids);
        for (ShopErpInventoryTransfer transfer : transfers) {
            // ERP 账号删的必须是自己操作的：堵住直接拿别人单号打接口这条路
            assertOwn(transfer);
            // 只有待审核能删，口径同「修改」：审核过的单子随时可能已经调出过货，
            // 删单不会把库存挪回去，删完就是一笔对不上的死账（取消掉的单也一样删不掉，那是留着的凭证）
            if (transfer.getStatus() == null || transfer.getStatus() != ShopErpInventoryTransfer.STATUS_PENDING) {
                throw new ServiceException("调拨单「" + transfer.getTransferNo()
                        + "」不是待审核状态，不能删除；不需要这张单请在操作列点「取消」");
            }
        }
        removeByIds(ids);
        // 明细跟着一起软删：留着孤儿行，以后按 transfer_id 汇总就会把已删除单的行算进去
        itemMapper.delete(new LambdaQueryWrapper<ShopErpInventoryTransferItem>()
                .in(ShopErpInventoryTransferItem::getTransferId, ids));
        // 这几张单占着的两家仓腾出来了（上面已经把它们置成已删除，所以 releaseWarehouses 数不到自己）
        for (ShopErpInventoryTransfer transfer : transfers) {
            releaseWarehouses(transfer.getFromWarehouseId(), transfer.getToWarehouseId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        ShopErpInventoryTransfer transfer = getById(id);
        if (transfer == null) {
            throw new ServiceException("调拨单不存在");
        }
        assertOwn(transfer);
        if (transfer.getStatus() == null || transfer.getStatus() != ShopErpInventoryTransfer.STATUS_PENDING) {
            throw new ServiceException("这张调拨单不是待审核状态，不能重复审核");
        }
        // 审核是把「调拨」放开的闸门，所以这里得确认两家仓还挂在单子上：
        // 仓被删了（库存行还在）或被人改回启用了，这张单就该重新建一张
        requireTransferWarehouse(transfer.getFromWarehouseId(), "调出");
        requireTransferWarehouse(transfer.getToWarehouseId(), "调入");
        // 只写这两列，走 UpdateWrapper 而不是稀疏实体：这张表上有好几个 IGNORED 列（user_id / reason / remark），
        // 稀疏实体 updateById 照样把它们写进 SET，值却是 null —— 审核一次就把操作员、原因、备注全抹了
        update(new LambdaUpdateWrapper<ShopErpInventoryTransfer>()
                .set(ShopErpInventoryTransfer::getStatus, ShopErpInventoryTransfer.STATUS_AUDITED)
                .set(ShopErpInventoryTransfer::getAuditTime, LocalDateTime.now())
                .eq(ShopErpInventoryTransfer::getId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(ShopErpInventoryTransfer form) {
        if (form == null || form.getId() == null) {
            throw new ServiceException("调拨单ID不能为空");
        }
        // 直接拿库里的整单来改（不用请求体）：user_id / reason / remark 是 IGNORED，
        // 用稀疏实体更新会把没带的列写成 NULL
        ShopErpInventoryTransfer transfer = getById(form.getId());
        if (transfer == null) {
            throw new ServiceException("调拨单不存在");
        }
        assertOwn(transfer);
        // 只有 1 已审核 / 2 调出中 能动账：0 还没批、3 已调满、4 作废
        int current = transfer.getStatus() == null ? ShopErpInventoryTransfer.STATUS_PENDING : transfer.getStatus();
        if (current != ShopErpInventoryTransfer.STATUS_AUDITED && current != ShopErpInventoryTransfer.STATUS_OUTING) {
            throw new ServiceException("这张调拨单当前不能调拨：待审核的单子请先点「审核」，"
                    + "已完成/已取消的单子请重新建一张");
        }
        Long fromWarehouseId = transfer.getFromWarehouseId();
        Long toWarehouseId = transfer.getToWarehouseId();
        // 仓行不在就不该再把货记到它头上（库存行是按 warehouse_id 记的，仓删了那行还在，
        // 少了这一道就会把货挪进一个已经不存在的仓）。冻结状态（2 盘点中 / 3 调拨中）不在这儿拦：
        // 这一路的账本来就该由调拨动作记，判据见 ShopErpInventoryService#transferOut
        requireWarehouseAlive(fromWarehouseId, "调出");
        requireWarehouseAlive(toWarehouseId, "调入");
        List<ShopErpInventoryTransferItem> rows = itemMapper.selectItemsByTransferId(transfer.getId(), fromWarehouseId);
        if (rows.isEmpty()) {
            throw new ServiceException("这张调拨单没有明细，先在修改里补上要调拨的商品规格再调拨");
        }
        // 请求体只认 (明细行 id, 本次调拨数)，规格/计划数/仓库全以库里的行为准
        Map<Long, ShopErpInventoryTransferItem> patches = new HashMap<>();
        if (form.getItems() != null) {
            for (ShopErpInventoryTransferItem line : form.getItems()) {
                if (line.getId() == null) {
                    throw new ServiceException("调拨明细缺少行ID，请重新打开调拨页再提交");
                }
                if (patches.put(line.getId(), line) != null) {
                    throw new ServiceException("同一条调拨明细提交了两次，请刷新调拨页");
                }
            }
        }
        Long operatorId = StpUtil.getLoginIdAsLong();
        boolean moved = false;
        for (ShopErpInventoryTransferItem row : rows) {
            ShopErpInventoryTransferItem patch = patches.remove(row.getId());
            if (patch == null) {
                // 没提交的行 = 这次不调，原样留着（分几次调就靠这一点）
                continue;
            }
            int thisTime = patch.getThisQuantity() == null ? 0 : patch.getThisQuantity();
            if (thisTime < 0) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 的本次调拨数不能为负");
            }
            if (thisTime == 0) {
                continue;
            }
            int plan = row.getQuantity() == null ? 0 : row.getQuantity();
            int out = row.getOutQuantity() == null ? 0 : row.getOutQuantity();
            if (thisTime > plan - out) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 这单计划调拨 " + plan
                        + " 件、已调出 " + out + " 件，本次最多还能调 " + Math.max(plan - out, 0) + " 件");
            }
            // 先扣调出仓：返回的是这次记账那一版的成本价，也就是这批货的实际成本，
            // 拿它给调入仓加权（口径同入库：加权平均是进货算的，动账只取现有值）
            BigDecimal unitPrice = inventoryService.transferOut(row.getSkuId(), fromWarehouseId, thisTime,
                    operatorId, transfer.getId(), transfer.getTransferNo());
            if (unitPrice == null) {
                // 建单时成本价是按这个仓带的，能走到这儿说明那一版有数、现在这一版被人在库存页清空了
                unitPrice = row.getCostPrice();
            }
            if (unitPrice == null) {
                throw new ServiceException("规格 #" + row.getSkuId() + " 在调出仓还没有成本价，"
                        + "请先在商品库存里补上这条规格的成本价再调拨");
            }
            // 再记调入仓：同一批数量、同一个事务，加权进来的单价就是刚扣出来的那个成本价
            inventoryService.transferIn(row.getSkuId(), toWarehouseId, thisTime, unitPrice,
                    operatorId, transfer.getId(), transfer.getTransferNo());
            // 这行的进度累加（两侧同增）；成本价按这次实际调出的那一版覆盖，小计跟着重算（主表总额循环完一起刷）
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(plan)).setScale(2, RoundingMode.HALF_UP);
            ShopErpInventoryTransferItem itemPatch = new ShopErpInventoryTransferItem();
            itemPatch.setId(row.getId());
            itemPatch.setOutQuantity(out + thisTime);
            itemPatch.setReceiveQuantity((row.getReceiveQuantity() == null ? 0 : row.getReceiveQuantity()) + thisTime);
            itemPatch.setCostPrice(unitPrice);
            itemPatch.setAmount(amount);
            itemMapper.updateById(itemPatch);

            row.setOutQuantity(out + thisTime);
            row.setReceiveQuantity(itemPatch.getReceiveQuantity());
            row.setCostPrice(unitPrice);
            row.setAmount(amount);
            moved = true;
        }
        if (!patches.isEmpty()) {
            throw new ServiceException("提交的明细里有不属于这张调拨单的行，请刷新调拨页后重试");
        }
        if (!moved) {
            throw new ServiceException("本次调拨数全部为 0，没有要调拨的规格");
        }
        // 主表：总额按各行最新成本价重算，状态按进度往前推（一次就调满的直接进 3 已完成）
        transfer.setItems(rows);
        recalculate(transfer);
        int target = progressStatus(rows);
        transfer.setStatus(target);
        applyMovedTime(transfer);
        updateById(transfer);
        // 调满了才把两家仓腾出来；没调满它们还得继续被这张单冻着
        if (target == ShopErpInventoryTransfer.STATUS_DONE) {
            releaseWarehouses(fromWarehouseId, toWarehouseId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ShopErpInventoryTransfer transfer = getById(id);
        if (transfer == null) {
            throw new ServiceException("调拨单不存在");
        }
        assertOwn(transfer);
        // 0 待审核 / 1 已审核 都能作废：这两档一笔库存都没动，取消只是翻状态 + 腾仓。
        // 2 调出中 / 3 已完成 不许取消 —— 货已经在两家仓之间挪过了，回退得反向再调一次，那是另一张单的活
        int current = transfer.getStatus() == null ? ShopErpInventoryTransfer.STATUS_PENDING : transfer.getStatus();
        if (current == ShopErpInventoryTransfer.STATUS_CANCELLED) {
            throw new ServiceException("这张调拨单已经是已取消状态");
        }
        if (current != ShopErpInventoryTransfer.STATUS_PENDING && current != ShopErpInventoryTransfer.STATUS_AUDITED) {
            throw new ServiceException("这张调拨单已经调出过货了，不能取消；剩下的规格继续点「调拨」调完即可");
        }
        // 同 audit：只写 status 一列，别让那三个 IGNORED 列被稀疏实体刷成 NULL
        update(new LambdaUpdateWrapper<ShopErpInventoryTransfer>()
                .set(ShopErpInventoryTransfer::getStatus, ShopErpInventoryTransfer.STATUS_CANCELLED)
                .eq(ShopErpInventoryTransfer::getId, id));
        // 先把状态翻成 4 再腾仓：releaseWarehouses 数的是"还有谁在跑"，这张单得先退出那支队伍
        releaseWarehouses(transfer.getFromWarehouseId(), transfer.getToWarehouseId());
    }

    /**
     * ERP 账号的归属校验，口径同入库单：非 ERP 直接放过（平台等看全部、也管全部）。
     * 这里补的是"绕过列表直接拿别人调拨单 id 打接口"那条路。
     * user_id 为 NULL 的单（平台录的、没记操作员）ERP 账号本来就看不到，这里也一律改不动。
     */
    private void assertOwn(ShopErpInventoryTransfer transfer) {
        if (!LoginUserUtils.isErp()) {
            return;
        }
        Long userId = transfer.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张调拨单不是你负责的，无权查看或修改");
        }
    }

    /**
     * 主表 + 明细一起校验：单号必填且不重复、两家仓必填且不是同一家、操作员填了要真有其人、
     * 明细至少一行且每行规格都还在、每行的成本价都能从**调出仓**那条库存行带出来（带不出就没成本口径）。
     *
     * @param excludeId 正在改的那一张的 id；为 null 就是新建。只有新建才要求两家仓当前是 1 启用 ——
     *                  改单时这两家仓正被这张自己冻成 3 调拨中，那道闸会把自己挡死
     */
    private void validate(ShopErpInventoryTransfer transfer, Long excludeId) {
        if (StringUtils.isBlank(transfer.getTransferNo())) {
            throw new ServiceException("调拨单号不能为空");
        }
        transfer.setTransferNo(transfer.getTransferNo().trim());
        if (transfer.getFromWarehouseId() == null) {
            throw new ServiceException("请选择调出仓库");
        }
        if (transfer.getToWarehouseId() == null) {
            throw new ServiceException("请选择调入仓库");
        }
        if (transfer.getFromWarehouseId().equals(transfer.getToWarehouseId())) {
            throw new ServiceException("调出仓库和调入仓库不能是同一家");
        }
        ShopErpWarehouse from = warehouseMapper.selectById(transfer.getFromWarehouseId());
        if (from == null) {
            throw new ServiceException("所选调出仓库不存在或已被删除");
        }
        ShopErpWarehouse to = warehouseMapper.selectById(transfer.getToWarehouseId());
        if (to == null) {
            throw new ServiceException("所选调入仓库不存在或已被删除");
        }
        if (excludeId == null) {
            // 建单要求两家仓都是启用：这一步紧接着把它们冻成 3 调拨中，而复位只认"现在是不是 3"
            // （见 releaseWarehouses）。仓库页的 options 下拉本来就只列启用的那几家，这一道是服务端同一口径
            requireEnabled(from, "调出");
            requireEnabled(to, "调入");
        }
        if (transfer.getUserId() != null && sysUserMapper.selectById(transfer.getUserId()) == null) {
            throw new ServiceException("所选ERP操作员不存在");
        }
        if (transfer.getTransferType() == null) {
            transfer.setTransferType(ShopErpInventoryTransfer.TYPE_NORMAL);
        } else {
            int type = transfer.getTransferType();
            if (type != ShopErpInventoryTransfer.TYPE_NORMAL && type != ShopErpInventoryTransfer.TYPE_URGENT
                    && type != ShopErpInventoryTransfer.TYPE_BETWEEN_STORE) {
                throw new ServiceException("调拨类型只能是 1普通调拨 / 2紧急调拨 / 3门店间调拨");
            }
        }
        List<ShopErpInventoryTransferItem> items = transfer.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("调拨明细不能为空，请至少保留一行商品规格");
        }
        for (int i = 0; i < items.size(); i++) {
            ShopErpInventoryTransferItem item = items.get(i);
            String row = "第 " + (i + 1) + " 行明细";
            if (item.getSkuId() == null) {
                throw new ServiceException(row + "：缺少商品规格，请按调出仓库选一条要调拨的规格");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new ServiceException(row + "：调拨数量必须大于 0");
            }
            ShopProductSku sku = productSkuMapper.selectById(item.getSkuId());
            if (sku == null) {
                throw new ServiceException(row + "：商品规格不存在或已被删除");
            }
            // product_id 由服务端按 sku 回填：明细行上这两列得是同一批货，请求体里带什么都不认
            item.setProductId(sku.getProductId());
            // 成本价从调出仓那一行库存带出：调拨是把这一仓的货挪到另一仓，
            // 记的成本只能是这一仓现在那个数（加权后的），所以带不出就该拦，而不是让页面填一个
            ShopErpInventory stock = inventoryService.getOne(new LambdaQueryWrapper<ShopErpInventory>()
                    .eq(ShopErpInventory::getSkuId, item.getSkuId())
                    .eq(ShopErpInventory::getWarehouseId, transfer.getFromWarehouseId()));
            if (stock == null) {
                throw new ServiceException(row + "：这条规格在「" + from.getWarehouseName()
                        + "」还没有库存记录（从没入过库），无从带出成本价，不能调拨");
            }
            if (stock.getCostPrice() == null) {
                throw new ServiceException(row + "：这条规格在「" + from.getWarehouseName()
                        + "」还没有成本价，请先在商品库存里补上再建这张单");
            }
            item.setCostPrice(stock.getCostPrice());
        }
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpInventoryTransfer>()
                .eq(ShopErpInventoryTransfer::getTransferNo, transfer.getTransferNo())
                .ne(excludeId != null, ShopErpInventoryTransfer::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("调拨单号「" + transfer.getTransferNo() + "」已存在");
        }
    }

    /**
     * 明细落库：transferId 一律以主表 id 为准（请求体里带什么都不认），
     * 成本价在 validate 里带好了、进度和金额在 resetItemProgress / recalculate 里算好了，这里只插
     */
    private void saveItems(ShopErpInventoryTransfer transfer) {
        for (ShopErpInventoryTransferItem item : transfer.getItems()) {
            // 详情接口的字段会被原样塞回表单，明细带着原来的 id 回来；不清零 INSERT 就会显式带上主键
            item.setId(null);
            item.setTransferId(transfer.getId());
            itemMapper.insert(item);
        }
    }

    private void removeItems(Long transferId) {
        itemMapper.delete(new LambdaQueryWrapper<ShopErpInventoryTransferItem>()
                .eq(ShopErpInventoryTransferItem::getTransferId, transferId));
    }

    /**
     * 新建/整单重存前把进度清成 0：能走到这里的只有待审核那一张（改单那道闸拦掉了别的状态），
     * 所以不存在"把已调出的数搬回新行"这一说，直接清零就行
     * （顺带把请求体里可能带的 out/receive_quantity 和只用于本次调拨的 this_quantity 冲掉）
     */
    private void resetItemProgress(List<ShopErpInventoryTransferItem> items) {
        for (ShopErpInventoryTransferItem item : items) {
            item.setOutQuantity(0);
            item.setReceiveQuantity(0);
            item.setThisQuantity(null);
        }
    }

    /**
     * 按明细重算"服务端说了算"的数：每行 amount = 调拨数量 × 成本价，主表 total_quantity / total_amount 累加。
     * 成本价的合法性在 validate 里已经保证，所以这里不做 null 兜底
     */
    private void recalculate(ShopErpInventoryTransfer transfer) {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (ShopErpInventoryTransferItem item : transfer.getItems()) {
            item.setAmount(item.getCostPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            quantity += item.getQuantity();
            amount = amount.add(item.getAmount());
        }
        transfer.setTotalQuantity(quantity);
        transfer.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 按明细进度算主表状态：一行都没调 1 已审核（不会由这里给出，见 transfer 的 moved 闸门，留着兜底）；
     * 全部调满 3 已完成；其余 2 调出中。
     *
     * 按"已调出"数算，不看"已调入"：两侧在同一个事务里一次记完，本来就该相等，
     * 但账如果因为别的原因歪了（比如手工冲过库存），以调出那一头为准更保守 —— 宁可停在 2 调出中让人来查
     */
    private int progressStatus(List<ShopErpInventoryTransferItem> items) {
        int plan = 0;
        int moved = 0;
        for (ShopErpInventoryTransferItem item : items) {
            int rowPlan = item.getQuantity() == null ? 0 : item.getQuantity();
            plan += rowPlan;
            moved += Math.min(item.getOutQuantity() == null ? 0 : item.getOutQuantity(), rowPlan);
        }
        if (moved <= 0) {
            return ShopErpInventoryTransfer.STATUS_AUDITED;
        }
        return moved >= plan ? ShopErpInventoryTransfer.STATUS_DONE : ShopErpInventoryTransfer.STATUS_OUTING;
    }

    /**
     * 状态第一次进入 2（调出中）时补 out_time、第一次进入 3（已完成）时补 receive_time。
     *
     * 一次就把所有规格调满的那一张会从 1 直接跳到 3，所以进 3 时 out_time 还空着也一起补 ——
     * 调出那一下确实发生了，留个空时间比补上更说不清。
     * 两列都故意不加 IGNORED（全局 not_empty 让 null 整列不进 UPDATE 的 SET），
     * 所以"已记过的时间不会被下一次改单改掉"（口径同入库单的 receive_time）
     */
    private void applyMovedTime(ShopErpInventoryTransfer transfer) {
        LocalDateTime now = LocalDateTime.now();
        int status = transfer.getStatus();
        if (status == ShopErpInventoryTransfer.STATUS_OUTING && transfer.getOutTime() == null) {
            transfer.setOutTime(now);
        }
        if (status == ShopErpInventoryTransfer.STATUS_DONE) {
            if (transfer.getOutTime() == null) {
                transfer.setOutTime(now);
            }
            if (transfer.getReceiveTime() == null) {
                transfer.setReceiveTime(now);
            }
        }
    }

    /** 调拨前的仓库闸门：仓行还得在（软删过的仓不该再把货记到它头上） */
    private void requireWarehouseAlive(Long warehouseId, String side) {
        if (warehouseId == null) {
            throw new ServiceException("这张调拨单缺少" + side + "仓库，无法调拨");
        }
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new ServiceException(side + "仓库不存在或已被删除，无法调拨");
        }
    }

    /**
     * 审核前的仓库闸门：在"仓还在"之外还要它当时是 3 调拨中 —— 也就是这两家仓仍然是被这张单冻着的。
     * 被人从仓库管理里改回启用（或改成盘点中）说明这个调拨口径已经废了，重开一张比在这里猜该怎么圆更清楚
     */
    private void requireTransferWarehouse(Long warehouseId, String side) {
        ShopErpWarehouse warehouse = warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new ServiceException(side + "仓库不存在或已被删除，这张调拨单没法审核，请取消后重建");
        }
        if (!Integer.valueOf(ShopErpWarehouse.STATUS_TRANSFER).equals(warehouse.getStatus())) {
            throw new ServiceException("仓库「" + warehouse.getWarehouseName() + "」现在不是调拨中状态，"
                    + "这张调拨单的冻结已经丢了，请取消后重建");
        }
    }

    /** 建单闸门：仓库必须是 1 启用（禁用/盘点中/调拨中的仓都不能新起一张调拨单） */
    private void requireEnabled(ShopErpWarehouse warehouse, String side) {
        if (!Integer.valueOf(ShopErpWarehouse.STATUS_ENABLED).equals(warehouse.getStatus())) {
            throw new ServiceException(side + "仓库「" + warehouse.getWarehouseName()
                    + "」不是启用状态，不能作为调拨的仓库；请先在仓库管理里把它改回启用");
        }
    }

    /**
     * 新建调拨单：把两家仓冻成 3 调拨中。
     * 用 LambdaUpdateWrapper 只写 status 一列：capacity_used 是出入库那条链维护的冗余列，
     * 拿整行实体回写会把自己刚读到的那份盖回去，纯属找事
     */
    private void freezeWarehouses(Long fromWarehouseId, Long toWarehouseId) {
        updateWarehouseStatus(fromWarehouseId, ShopErpWarehouse.STATUS_TRANSFER);
        updateWarehouseStatus(toWarehouseId, ShopErpWarehouse.STATUS_TRANSFER);
    }

    /**
     * 单子走完了（3 已完成 / 4 已取消）或删掉了：把两家仓复位成 1 启用。
     *
     * 两道保留：
     * 1. 只有现在还是 3 调拨中 才复位 —— 有人在仓库管理里改成盘点中/禁用，那是他的决定，不该由这张单改回去；
     * 2. 还有别的调拨单引用这家仓且它仍在跑（0/1/2，判据见 ShopErpInventoryTransfer#inFlight）就不复位，
     *    否则先完成的那张会把别人那张的冻结解掉。
     *
     * 调用方要先把这张单自己的状态翻掉（或把它删掉）再来腾仓，不然这里会把自己也算成"还在跑的那一张"
     */
    private void releaseWarehouses(Long fromWarehouseId, Long toWarehouseId) {
        releaseOneWarehouse(fromWarehouseId);
        releaseOneWarehouse(toWarehouseId);
    }

    private void releaseOneWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return;
        }
        ShopErpWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null || !Integer.valueOf(ShopErpWarehouse.STATUS_TRANSFER).equals(warehouse.getStatus())) {
            return;
        }
        Long holding = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpInventoryTransfer>()
                .and(wrapper -> wrapper.eq(ShopErpInventoryTransfer::getFromWarehouseId, warehouseId)
                        .or().eq(ShopErpInventoryTransfer::getToWarehouseId, warehouseId))
                .in(ShopErpInventoryTransfer::getStatus, ShopErpInventoryTransfer.STATUS_PENDING,
                        ShopErpInventoryTransfer.STATUS_AUDITED, ShopErpInventoryTransfer.STATUS_OUTING));
        if (holding != null && holding > 0) {
            return;
        }
        updateWarehouseStatus(warehouseId, ShopErpWarehouse.STATUS_ENABLED);
    }

    private void updateWarehouseStatus(Long warehouseId, int status) {
        if (warehouseId == null) {
            return;
        }
        warehouseMapper.update(null, new LambdaUpdateWrapper<ShopErpWarehouse>()
                .set(ShopErpWarehouse::getStatus, status)
                .eq(ShopErpWarehouse::getId, warehouseId));
    }
}
