package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryCheck;
import com.shop.entity.ShopErpInventoryCheckItem;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopProductSku;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpInventoryCheckItemMapper;
import com.shop.mapper.ShopErpInventoryCheckMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopErpInventoryCheckService;
import com.shop.service.ShopErpInventoryService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 库存盘点单 服务实现（单据的骨架照 ShopErpInventoryTransferServiceImpl，这里只说盘点自己的口径）
 *
 * 1. 这张单**直接动库存**，不经过入库单/出库单：一次「调整」把有差异的那几行按 diff_quantity 落到
 *    盘点仓的库存行上（ShopErpInventoryService#checkIn / #checkOut），流水相应记 7 盘盈 / 8 盘亏。
 *    账实相符的行不动账、也不记流水，但它同样要在调整弹窗里点一次确认才进 2 已调整 ——
 *    无差异也是"有人认过这一笔"的一个事实（这是他定的口径，别改成自动完成）。
 * 2. 仓库状态由这张单管：新建就把盘点仓置成 2 盘点中（出入库、下单分仓都会被 WarehouseUtils 那道闸拦掉，
 *    账面才停得住），单子走到 2 已完成 / 3 已取消 才复位成 1 启用，而且只认它现在确实是 2、
 *    且没有其它还在跑（0/1）的盘点单引用它才复位（见 releaseWarehouse）。建单要求这家仓当时是 1 启用，
 *    就是为了这条复位链走得通 —— 不然先完成的那张会把别人那张的冻结解掉，
 *    或者把别人手动设的 调拨中/禁用 擅自改成启用。
 * 3. status 不由表单选：新增恒 0 待审核；1 盘点中 由「审核」推；2 已完成 由「调整」按明细进度推；
 *    3 已取消 由「取消」置。start_time / end_time 各在第一次进对应那档时补，之后不覆盖。
 *    注意单据的 status 和仓库的 status 是两码事：仓在建单那一刻就冻成 2 盘点中了，
 *    单据自己还得先过「审核」（这是需求 ③ 和字典 check_status 定下来的走法）。
 * 4. 「修改」在 0/1 两档都开放（0 改范围、1 主要是录实盘，也能补漏掉的规格），但**已调整的那几行不许动**；
 *    只有 0 待审核 能删 —— 一进了 1 就可能已经调过账了，删单不会把库存挪回去，删完就是一笔对不上的死账。
 *    「取消」放宽到 0/1 两档，但同样要求一行都没调过账（判据查明细里 status=2 的行数）。
 * 5. book_quantity / cost_price 不是表单列，也不刷新：建单时从盘点仓那条库存行快照下来，
 *    之后改单、调整都沿用建单那一版（账面必须停在盘点开始那一刻才对得上账，
 *    中途被动过的库存由下一张盘点单去发现）。diff_quantity / diff_amount 和主表那六个数一律服务端算。
 * 6. 明细不能"整单软删重插"：表上有唯一键 uk_check_sku (check_id, sku_id)，软删的行仍然占着这个键，
 *    重插同一条规格必撞 1062；MP 的 update 又都被逻辑删除谓词挡在 is_deleted=0 那一侧。
 *    所以改单是按 (check_id, sku_id) 走 itemMapper#resetItem 覆盖原行（顺带把删掉的行复活），
 *    只有库里没有的那条规格才 INSERT。
 *
 * 数据范围：ERP 操作员账号（account_type=5）只看/只改 user_id = 自己那几张，三件套同调拨单：
 * 列表覆盖 userId、读写前 assertOwn、新增钉成登录 id / 修改钉回原值。
 */
@Service
@RequiredArgsConstructor
public class ShopErpInventoryCheckServiceImpl
        extends ServiceImpl<ShopErpInventoryCheckMapper, ShopErpInventoryCheck>
        implements ShopErpInventoryCheckService {

    /** 明细的规格候选一次最多返回多少条（远程搜索的下拉，同调拨单/销售单） */
    private static final int SKU_OPTION_LIMIT = 20;

    /** 差异原因（字典 diff_reason）就这四档；明细那一列存的是字典值的字符串 */
    private static final Set<String> DIFF_REASONS = new HashSet<>(Arrays.asList("1", "2", "3", "4"));

    private final ShopErpInventoryCheckItemMapper itemMapper;

    /** 建单时校验仓库存在且启用，并在这三处（建单/完成/取消删除）翻它的盘点中/启用状态 */
    private final ShopErpWarehouseMapper warehouseMapper;

    /** 只校验操作人存在性 */
    private final SysUserMapper sysUserMapper;

    /** 明细行的规格存在性 + 商品回填（sku_id → product_id 那一跳） */
    private final ShopProductSkuMapper productSkuMapper;

    /** 库存落库的唯一入口：盘盈加一下、盘亏减一下、流水怎么记，全在它那边 */
    private final ShopErpInventoryService inventoryService;

    @Override
    public IPage<ShopErpInventoryCheck> selectPage(ShopErpInventoryCheck query) {
        if (query == null) {
            query = new ShopErpInventoryCheck();
        }
        // 一律覆盖 userId：ERP 账号换成登录 id，其它账号置空。列表上没有"按操作员筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人盘的盘子
        query.setUserId(LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null);
        // 关键字跨单号/仓库名/备注/操作员名搜，条件写在 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectCheckPage(PageUtil.getPage(), query);
        // 列表不带明细：要看明细点开修改弹窗或「调整」弹窗（同调拨单/入库单）
    }

    @Override
    public ShopErpInventoryCheck selectCheckById(Long id) {
        ShopErpInventoryCheck check = baseMapper.selectCheckById(id);
        if (check == null) {
            throw new ServiceException("盘点单不存在");
        }
        assertOwn(check);
        // 明细那一趟顺带按盘点仓联出"这条规格现在库存几件、还能动几件"，
        // 「调整」弹窗要拿它和账面数比一眼：正常应该相等（仓是冻着的），不等就是期间有人动过账
        check.setItems(itemMapper.selectItemsByCheckId(id, check.getWarehouseId()));
        return check;
    }

    @Override
    public List<ShopErpInventory> selectSkuOptions(Long warehouseId, String keyword) {
        // 候选一行是盘点仓里的一条库存行：判据（这一仓的 + 规格和商品都还在）全在 XML 里，
        // 这里刻意不筛可用库存（账面 0 件的那一行最该盘）
        if (warehouseId == null) {
            throw new ServiceException("请先选择盘点仓库");
        }
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new ServiceException("所选盘点仓库不存在或已被删除");
        }
        return baseMapper.selectSkuOptions(warehouseId, StringUtils.trimToNull(keyword), SKU_OPTION_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopErpInventoryCheck check) {
        // 新增时表单那栏是禁用的、不会传单号，和调拨单号一样由系统生成
        if (StringUtils.isBlank(check.getCheckNo())) {
            check.setCheckNo(GeneratorNoUtils.getCheckNo());
        }
        // 新建才走"仓库必须是 1 启用"那道闸（validate 按 excludeId 分岔）
        ShopErpWarehouse warehouse = validate(check, null);
        // 账面数和成本价从盘点仓那条库存行快照下来，差异和明细状态按录了的实盘算
        for (ShopErpInventoryCheckItem item : check.getItems()) {
            snapshotStock(item, warehouse);
            applyDiff(item);
        }
        recalculate(check);
        // 新建的单子一律从 0 待审核 起（「审核」那颗按钮才推得到 1 盘点中）；请求体里的 status 不认
        check.setStatus(ShopErpInventoryCheck.STATUS_PENDING);
        // 两个时间列都从 null 起：钉这一道是防"编辑完一张单再点新增"时，表单里残留的时间被原样插进新单
        check.setStartTime(null);
        check.setEndTime(null);
        // 操作员：ERP 账号录的单只能记自己名下（表单那一格对他禁用了，服务端再钉一道）。
        // 其它账号没指定就留 NULL（= 没记是谁盘的）
        if (LoginUserUtils.isErp()) {
            check.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(check);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_check_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("盘点单号「" + check.getCheckNo()
                    + "」已被占用，可能来自一张已删除的盘点单");
        }
        saveItems(check);
        // 建完单就把这家仓冻起来：需求③ 要的就是这一步 —— 账面停在盘点开始那一刻才盘得准
        freezeWarehouse(check.getWarehouseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopErpInventoryCheck check) {
        if (check.getId() == null) {
            throw new ServiceException("盘点单ID不能为空");
        }
        ShopErpInventoryCheck exists = getById(check.getId());
        if (exists == null) {
            throw new ServiceException("盘点单不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 0 待审核 改盘点范围、1 盘点中 录实盘，这两档之外（2 已完成 / 3 已取消）单子已经走完了
        int current = exists.getStatus() == null
                ? ShopErpInventoryCheck.STATUS_PENDING : exists.getStatus();
        if (current != ShopErpInventoryCheck.STATUS_PENDING && current != ShopErpInventoryCheck.STATUS_STOCKTAKING) {
            throw new ServiceException("这张盘点单已经走完或已取消，不能修改；要盘别的规格请重新建一张单");
        }
        // 操作员只有非 ERP 账号能换；ERP 账号钉回原值，免得改个实盘数把单子挪到别人名下
        if (LoginUserUtils.isErp()) {
            check.setUserId(exists.getUserId());
        }
        // 单号允许不传（表单里是只读的），不传时沿用原值参与查重
        if (StringUtils.isBlank(check.getCheckNo())) {
            check.setCheckNo(exists.getCheckNo());
        }
        // 盘点仓钉回库里的值：冻结是按这一列盖上去的，换仓等于把上一家的冻结留在别人头上、
        // 又凭空把第三家冻进去。要换仓只能取消这张重开（前端那一格在修改弹窗里也是禁用的）
        if (check.getWarehouseId() != null && !check.getWarehouseId().equals(exists.getWarehouseId())) {
            throw new ServiceException("盘点单不支持换仓库，请取消这张单后按新的仓库重新建一张");
        }
        check.setWarehouseId(exists.getWarehouseId());
        // 状态和两个时间列同样钉回库里的值：审核走「审核」那颗按钮，时间由服务端在进档时补
        check.setStatus(exists.getStatus());
        check.setStartTime(exists.getStartTime());
        check.setEndTime(exists.getEndTime());
        // excludeId 非空：跳过"仓库必须 1 启用"那道闸（它现在正被这张单冻成 2 盘点中）
        ShopErpWarehouse warehouse = validate(check, check.getId());

        // 已存在的行按 sku 认（uk_check_sku 就是这一对），提交里没有的算"这次不再盘它"
        Map<Long, ShopErpInventoryCheckItem> oldRows = new HashMap<>();
        for (ShopErpInventoryCheckItem row : itemMapper.selectList(
                new LambdaQueryWrapper<ShopErpInventoryCheckItem>()
                        .eq(ShopErpInventoryCheckItem::getCheckId, check.getId()))) {
            oldRows.put(row.getSkuId(), row);
        }
        Long operatorId = StpUtil.getLoginIdAsLong();
        for (ShopErpInventoryCheckItem item : check.getItems()) {
            item.setCheckId(check.getId());
            ShopErpInventoryCheckItem row = oldRows.remove(item.getSkuId());
            if (row != null && row.getStatus() != null && row.getStatus() == ShopErpInventoryCheckItem.STATUS_ADJUSTED) {
                // 已经调过账的那一行：它的差异已经进了库存账，改个实盘数就等于把单据改成和账对不上的样子
                if (!StringUtils.equals(StringUtils.trimToNull(row.getDiffReason()),
                        StringUtils.trimToNull(item.getDiffReason()))
                        || !Objects.equals(row.getActualQuantity(), item.getActualQuantity())) {
                    throw new ServiceException("规格 #" + item.getSkuId()
                            + " 已经调整过了，不能再改它的实盘数或差异原因；账面又变了就另建一张盘点单");
                }
                // 原样提交就跳过这一行（同一次修改点两次保存不至于把已调整的行退回 1 已盘点）
                continue;
            }
            if (row == null) {
                // 盘点期间发现漏了某条规格：补进来的这行按现在的库存行取账面数
                snapshotStock(item, warehouse);
            } else {
                // 老行沿用建单那一刻的账面数和成本价，改单不刷新（口径见类注释第 5 条）
                item.setBookQuantity(row.getBookQuantity());
                item.setCostPrice(row.getCostPrice());
            }
            applyDiff(item);
            // resetItem 是自定义 SQL，MyMetaObjectHandler 不会过来，留痕那一列手写
            item.setUpdateBy(operatorId);
            if (itemMapper.resetItem(item) == 0) {
                item.setId(null);
                itemMapper.insert(item);
            }
        }
        // 提交里没有的行：软删掉。已调整的那几行在上面被 remove 走了，所以这里数不到它们
        if (!oldRows.isEmpty()) {
            List<Long> dropIds = new ArrayList<>(oldRows.size());
            for (ShopErpInventoryCheckItem dropped : oldRows.values()) {
                dropIds.add(dropped.getId());
            }
            itemMapper.delete(new LambdaQueryWrapper<ShopErpInventoryCheckItem>()
                    .in(ShopErpInventoryCheckItem::getId, dropIds));
        }
        // 主表那六个数按库里最新的明细重算（上面写的都是算过的，但删掉的行得从总数里出去）。
        // 这里重新查一遍而不是沿用提交的那份：跳过的已调整行不在提交里算得全
        check.setItems(itemMapper.selectItemsByCheckId(check.getId(), check.getWarehouseId()));
        recalculate(check);
        try {
            updateById(check);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("盘点单号「" + check.getCheckNo()
                    + "」已被占用，可能来自一张已删除的盘点单");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先按 id 捞出要删的几张：一是给归属校验和状态判据用，二是删完得知道该把哪几家仓腾出来
        List<ShopErpInventoryCheck> checks = listByIds(ids);
        for (ShopErpInventoryCheck check : checks) {
            // ERP 账号删的必须是自己盘的：堵住直接拿别人单号打接口这条路
            assertOwn(check);
            // 只有待审核能删，口径同「修改」：盘点中这一档随时可能已经调过账了，
            // 删单不会把库存挪回去，删完就是一笔对不上的死账（取消掉的单也一样删不掉，那是留着的凭证）
            if (check.getStatus() == null || check.getStatus() != ShopErpInventoryCheck.STATUS_PENDING) {
                throw new ServiceException("盘点单「" + check.getCheckNo()
                        + "」不是待审核状态，不能删除；不需要这张单请在操作列点「取消」");
            }
        }
        removeByIds(ids);
        // 明细跟着一起软删：留着孤儿行，以后按 check_id 汇总就会把已删除单的行算进去
        itemMapper.delete(new LambdaQueryWrapper<ShopErpInventoryCheckItem>()
                .in(ShopErpInventoryCheckItem::getCheckId, ids));
        // 这几张单占着的仓库腾出来了（上面已经把单子置成已删除，所以 releaseWarehouse 数不到自己）
        for (ShopErpInventoryCheck check : checks) {
            releaseWarehouse(check.getWarehouseId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        ShopErpInventoryCheck check = getById(id);
        if (check == null) {
            throw new ServiceException("盘点单不存在");
        }
        assertOwn(check);
        if (check.getStatus() == null || check.getStatus() != ShopErpInventoryCheck.STATUS_PENDING) {
            throw new ServiceException("这张盘点单不是待审核状态，不能重复审核");
        }
        // 审核是把「录实盘 + 调整」放开的闸门，所以这里得确认那家仓还冻在这张单上：
        // 仓被删了（库存行还在）或被人改回启用了，这张单的账面口径就废了，该重新建一张
        requireStocktakingWarehouse(check.getWarehouseId());
        // 只写这两列，走 UpdateWrapper 而不是稀疏实体：这张表上有两个 IGNORED 列（user_id / remark），
        // 稀疏实体 updateById 照样把这两列写进 SET，值却是 null —— 审核一次就把操作员和备注抹了
        update(new LambdaUpdateWrapper<ShopErpInventoryCheck>()
                .set(ShopErpInventoryCheck::getStatus, ShopErpInventoryCheck.STATUS_STOCKTAKING)
                .set(ShopErpInventoryCheck::getStartTime, LocalDateTime.now())
                .eq(ShopErpInventoryCheck::getId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(ShopErpInventoryCheck form) {
        if (form == null || form.getId() == null) {
            throw new ServiceException("盘点单ID不能为空");
        }
        // 直接拿库里的整单来改（不用请求体）：user_id / remark 是 IGNORED，
        // 用稀疏实体更新会把没带的列写成 NULL
        ShopErpInventoryCheck check = getById(form.getId());
        if (check == null) {
            throw new ServiceException("盘点单不存在");
        }
        assertOwn(check);
        // 只有 1 盘点中 能动账：0 还没批、2 已盘完、3 作废
        int current = check.getStatus() == null
                ? ShopErpInventoryCheck.STATUS_PENDING : check.getStatus();
        if (current != ShopErpInventoryCheck.STATUS_STOCKTAKING) {
            throw new ServiceException("这张盘点单当前不能调整：待审核的单子请先点「审核」，"
                    + "已完成/已取消的单子请重新建一张");
        }
        Long warehouseId = check.getWarehouseId();
        requireStocktakingWarehouse(warehouseId);
        List<ShopErpInventoryCheckItem> rows = itemMapper.selectItemsByCheckId(check.getId(), warehouseId);
        if (rows.isEmpty()) {
            throw new ServiceException("这张盘点单没有明细，先在修改里补上要盘的规格再调整");
        }
        // 请求体只认 (明细行 id, 本次是否调整, 差异原因)，账面/实盘/差异/规格全以库里的行为准
        Map<Long, ShopErpInventoryCheckItem> patches = new HashMap<>();
        if (form.getItems() != null) {
            for (ShopErpInventoryCheckItem line : form.getItems()) {
                if (line.getId() == null) {
                    throw new ServiceException("盘点明细缺少行ID，请重新打开调整页再提交");
                }
                if (patches.put(line.getId(), line) != null) {
                    throw new ServiceException("同一条盘点明细提交了两次，请刷新调整页");
                }
            }
        }
        Long operatorId = StpUtil.getLoginIdAsLong();
        boolean adjusted = false;
        for (ShopErpInventoryCheckItem row : rows) {
            ShopErpInventoryCheckItem patch = patches.remove(row.getId());
            if (patch == null || !Boolean.TRUE.equals(patch.getThisAdjust())) {
                // 没勾上的行 = 这次不调，原样留着（分几批调就靠这一点）
                continue;
            }
            if (row.getStatus() != null && row.getStatus() == ShopErpInventoryCheckItem.STATUS_ADJUSTED) {
                throw new ServiceException("规格 #" + row.getSkuId()
                        + " 这一行已经调整过了，一行只能调平一次；账面又变了就另建一张盘点单");
            }
            // 调账的前提是这一行盘过了：没录实盘就没有差异，连该加还是该减都不知道
            if (row.getStatus() == null || row.getStatus() != ShopErpInventoryCheckItem.STATUS_COUNTED) {
                throw new ServiceException("规格 #" + row.getSkuId()
                        + " 还没录实盘数，先在「修改」里填上这一行的实盘库存再来调整");
            }
            int diff = row.getDiffQuantity() == null ? 0 : row.getDiffQuantity();
            String reason = StringUtils.trimToNull(patch.getDiffReason());
            if (reason != null && !DIFF_REASONS.contains(reason)) {
                throw new ServiceException("差异原因只能是 1正常损耗 / 2记录错误 / 3丢失 / 4其他");
            }
            if (reason == null) {
                // 弹窗里没重填就沿用它已有的那个（录实盘时在修改里填过）
                reason = StringUtils.trimToNull(row.getDiffReason());
            }
            if (diff != 0 && reason == null) {
                throw new ServiceException("规格 #" + row.getSkuId()
                        + " 有 " + diff + " 件差异，调整时必须选一个差异原因");
            }

            // 差异为正 = 实物比账面多 = 盘盈：给这家仓加上这一笔。
            // 单价用的是账面停住那一刻的成本价（建单时快照下来的那版），和 diff_amount 同一个口径，
            // 加权平均是进货算的，这里只是把这一版价格搬进去（见 ShopErpInventoryServiceImpl#moveIn）
            BigDecimal unitPrice = row.getCostPrice();
            if (diff > 0) {
                if (unitPrice == null) {
                    // 建单时这条规格账面 0 件、从没算过成本价，那就用库存行现在那一版
                    unitPrice = currentCostPrice(row.getSkuId(), warehouseId);
                }
                if (unitPrice == null) {
                    throw new ServiceException("规格 #" + row.getSkuId() + " 还没有成本价，"
                            + "请先在商品库存里补上这条规格的成本价再调整盘盈");
                }
                inventoryService.checkIn(row.getSkuId(), warehouseId, diff, unitPrice,
                        operatorId, check.getId(), check.getCheckNo());
            } else if (diff < 0) {
                // 差异为负 = 实物比账面少 = 盘亏：减的是总库存，被下单锁着的那一份还不回来，
                // 所以能动的那部分不够就会在这里被拦下（口径同调拨出库）。
                // 返回值是这次扣账那一版的成本价，只在行上没快照到成本价时兜一下金额口径
                BigDecimal cost = inventoryService.checkOut(row.getSkuId(), warehouseId, -diff,
                        operatorId, check.getId(), check.getCheckNo());
                if (unitPrice == null) {
                    unitPrice = cost;
                }
            }

            // 建单时带不出成本价的那一行（账面 0 件的新规格），现在有了价，
            // 把这一版的成本价补到行上，差异金额和主表那两个金额才不是空的
            boolean fillPrice = diff != 0 && unitPrice != null && row.getCostPrice() == null;
            BigDecimal amount = fillPrice
                    ? unitPrice.multiply(BigDecimal.valueOf(diff)).setScale(2, RoundingMode.HALF_UP)
                    : null;
            if (fillPrice) {
                row.setCostPrice(unitPrice);
                row.setDiffAmount(amount);
            }
            // 只写这四列，走 UpdateWrapper 而不是稀疏实体：actual_quantity 是 IGNORED 列，
            // 稀疏实体 updateById 照样把它写进 SET、值却是 null —— 调一次账就把录好的实盘数抹没了
            itemMapper.update(null, new LambdaUpdateWrapper<ShopErpInventoryCheckItem>()
                    .set(ShopErpInventoryCheckItem::getStatus, ShopErpInventoryCheckItem.STATUS_ADJUSTED)
                    // 没有差异的行传 null 就是把这一格清干净
                    .set(ShopErpInventoryCheckItem::getDiffReason, reason)
                    .set(fillPrice, ShopErpInventoryCheckItem::getCostPrice, unitPrice)
                    .set(fillPrice, ShopErpInventoryCheckItem::getDiffAmount, amount)
                    .eq(ShopErpInventoryCheckItem::getId, row.getId()));
            row.setStatus(ShopErpInventoryCheckItem.STATUS_ADJUSTED);
            row.setDiffReason(reason);
            adjusted = true;
        }
        if (!patches.isEmpty()) {
            throw new ServiceException("提交的明细里有不属于这张盘点单的行，请刷新调整页后重试");
        }
        if (!adjusted) {
            throw new ServiceException("这次没有勾选任何要调整的规格");
        }
        // 主表：六个数按库里最新的明细重算（调整不动差异，但补录/清行的那几次会跟着刷），
        // 状态按明细进度往前推 —— 全部行都到 2 已调整 才进 2 已完成
        check.setItems(rows);
        recalculate(check);
        int target = progressStatus(rows);
        check.setStatus(target);
        if (target == ShopErpInventoryCheck.STATUS_DONE && check.getEndTime() == null) {
            check.setEndTime(LocalDateTime.now());
        }
        updateById(check);
        // 盘完了才把这家仓腾出来；没调完它还得继续被这张单冻着（账面才停得住）
        if (target == ShopErpInventoryCheck.STATUS_DONE) {
            releaseWarehouse(warehouseId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ShopErpInventoryCheck check = getById(id);
        if (check == null) {
            throw new ServiceException("盘点单不存在");
        }
        assertOwn(check);
        // 0 待审核 / 1 盘点中 都可能还没动账：先看状态，再看明细
        int current = check.getStatus() == null
                ? ShopErpInventoryCheck.STATUS_PENDING : check.getStatus();
        if (current == ShopErpInventoryCheck.STATUS_CANCELLED) {
            throw new ServiceException("这张盘点单已经是已取消状态");
        }
        if (current != ShopErpInventoryCheck.STATUS_PENDING && current != ShopErpInventoryCheck.STATUS_STOCKTAKING) {
            throw new ServiceException("这张盘点单已经完成了，不能取消");
        }
        // 只要调过一行账就退不回去了：那几笔库存已经按差异挪过，反向再挪一次是另一张单的活
        Long moved = itemMapper.selectCount(new LambdaQueryWrapper<ShopErpInventoryCheckItem>()
                .eq(ShopErpInventoryCheckItem::getCheckId, id)
                .eq(ShopErpInventoryCheckItem::getStatus, ShopErpInventoryCheckItem.STATUS_ADJUSTED));
        if (moved != null && moved > 0) {
            throw new ServiceException("这张盘点单已经有 " + moved + " 行调整过库存了，不能取消；"
                    + "剩下的规格继续点「调整」调完即可");
        }
        // 同 audit：只写 status 一列，别让 user_id / remark 这两个 IGNORED 列被稀疏实体刷成 NULL
        update(new LambdaUpdateWrapper<ShopErpInventoryCheck>()
                .set(ShopErpInventoryCheck::getStatus, ShopErpInventoryCheck.STATUS_CANCELLED)
                .eq(ShopErpInventoryCheck::getId, id));
        // 先把状态翻成 3 再腾仓：releaseWarehouse 数的是"还有谁在跑"，这张单得先退出那支队伍
        releaseWarehouse(check.getWarehouseId());
    }

    /**
     * ERP 账号的归属校验，口径同调拨单：非 ERP 直接放过（平台等看全部、也管全部）。
     * 这里补的是"绕过列表直接拿别人盘点单 id 打接口"那条路。
     * user_id 为 NULL 的单（平台录的、没记操作员）ERP 账号本来就看不到，这里也一律改不动。
     */
    private void assertOwn(ShopErpInventoryCheck check) {
        if (!LoginUserUtils.isErp()) {
            return;
        }
        Long userId = check.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张盘点单不是你负责的，无权查看或修改");
        }
    }

    /**
     * 主表 + 明细一起校验：单号必填且不重复、仓库必填、操作员填了要真有其人、盘点类型在字典里、
     * 明细至少一行且每行规格都还在、同一张单里一条规格只能出现一次（表上有 uk_check_sku，
     * 但 MySQL 的唯一键对 NULL 不判重，这一道还是得由服务层保证）、实盘数不能为负、差异原因填了就在字典里。
     *
     * @param excludeId 正在改的那一张的 id；为 null 就是新建。只有新建才要求这家仓当前是 1 启用 ——
     *                  改单时它正被这张自己冻成 2 盘点中，那道闸会把自己挡死
     * @return 校验过的仓库行，给 snapshotStock 带账面数用（免得每行再查一次）
     */
    private ShopErpWarehouse validate(ShopErpInventoryCheck check, Long excludeId) {
        if (StringUtils.isBlank(check.getCheckNo())) {
            throw new ServiceException("盘点单号不能为空");
        }
        check.setCheckNo(check.getCheckNo().trim());
        if (check.getWarehouseId() == null) {
            throw new ServiceException("请选择盘点仓库");
        }
        ShopErpWarehouse warehouse = warehouseMapper.selectById(check.getWarehouseId());
        if (warehouse == null) {
            throw new ServiceException("所选盘点仓库不存在或已被删除");
        }
        if (excludeId == null) {
            // 建单要求这家仓是启用状态：这一步紧接着把它冻成 2 盘点中，而复位只认"现在是不是 2"
            // （见 releaseWarehouse）。仓库页的 options 下拉本来就只列启用的那几家，这一道是服务端同一口径
            requireEnabled(warehouse);
        }
        if (check.getUserId() != null && sysUserMapper.selectById(check.getUserId()) == null) {
            throw new ServiceException("所选ERP操作员不存在");
        }
        if (check.getCheckType() == null) {
            check.setCheckType(ShopErpInventoryCheck.TYPE_FULL);
        } else {
            int type = check.getCheckType();
            if (type != ShopErpInventoryCheck.TYPE_FULL && type != ShopErpInventoryCheck.TYPE_SAMPLE
                    && type != ShopErpInventoryCheck.TYPE_CYCLE) {
                throw new ServiceException("盘点类型只能是 1全盘 / 2抽盘 / 3循环盘点");
            }
        }
        List<ShopErpInventoryCheckItem> items = check.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("盘点明细不能为空，请按盘点仓库选至少要盘的一条规格");
        }
        Set<Long> skuIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            ShopErpInventoryCheckItem item = items.get(i);
            String row = "第 " + (i + 1) + " 行明细";
            if (item.getSkuId() == null) {
                throw new ServiceException(row + "：缺少商品规格，请按盘点仓库选一条要盘的规格");
            }
            if (!skuIds.add(item.getSkuId())) {
                throw new ServiceException(row + "：这条规格在本单里出现了两次，一张盘点单里一条规格只能有一行");
            }
            if (item.getActualQuantity() != null && item.getActualQuantity() < 0) {
                throw new ServiceException(row + "：实盘库存不能为负");
            }
            if (item.getDiffReason() != null) {
                String reason = StringUtils.trimToNull(item.getDiffReason());
                if (reason != null && !DIFF_REASONS.contains(reason)) {
                    throw new ServiceException(row + "：差异原因只能是 1正常损耗 / 2记录错误 / 3丢失 / 4其他");
                }
                // 空格串一律当没填：这一列是可空的
                item.setDiffReason(reason);
            }
            ShopProductSku sku = productSkuMapper.selectById(item.getSkuId());
            if (sku == null) {
                throw new ServiceException(row + "：商品规格不存在或已被删除");
            }
            // product_id 由服务端按 sku 回填：明细行上这两列得是同一批货，请求体里带什么都不认
            item.setProductId(sku.getProductId());
        }
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpInventoryCheck>()
                .eq(ShopErpInventoryCheck::getCheckNo, check.getCheckNo())
                .ne(excludeId != null, ShopErpInventoryCheck::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("盘点单号「" + check.getCheckNo() + "」已存在");
        }
        return warehouse;
    }

    /**
     * 账面数和成本价从盘点仓那条库存行快照下来：库存行不在就是这条规格从没在这家仓入过库，
     * 连"账面上该有几件"都没有，盘无可盘，直接拦。
     *
     * cost_price 允许带不出（账面 0 件的规格从没进过货、自然没算过加权价）—— 这一列在 DDL 里就可空，
     * 拦在建单外会让人连"实物比账面多几件"这一笔都登记不了；差额算不出金额就先留 NULL，
     * 到「调整」真要动账时再按当时的库存行补一次（见 adjust 里那一档）
     */
    private void snapshotStock(ShopErpInventoryCheckItem item, ShopErpWarehouse warehouse) {
        ShopErpInventory stock = getStockRow(item.getSkuId(), warehouse.getId());
        if (stock == null) {
            throw new ServiceException("规格 #" + item.getSkuId() + " 在「" + warehouse.getWarehouseName()
                    + "」还没有库存记录（从没入过库），没有账面数可盘");
        }
        item.setBookQuantity(stock.getStock() == null ? 0 : stock.getStock());
        item.setCostPrice(stock.getCostPrice());
    }

    /** 这一行现在的库存行（sku_id + warehouse_id 是它的身份，唯一键保证最多一行） */
    private ShopErpInventory getStockRow(Long skuId, Long warehouseId) {
        return inventoryService.getOne(new LambdaQueryWrapper<ShopErpInventory>()
                .eq(ShopErpInventory::getSkuId, skuId)
                .eq(ShopErpInventory::getWarehouseId, warehouseId));
    }

    /** 库存行现在那一版的成本价，行不在或没算过价就返回 null */
    private BigDecimal currentCostPrice(Long skuId, Long warehouseId) {
        ShopErpInventory stock = getStockRow(skuId, warehouseId);
        return stock == null ? null : stock.getCostPrice();
    }

    /**
     * 由实盘数算这一行的差异、差异金额和明细状态：
     * 实盘为 NULL = 这一行还没盘到，差异记 0（别当差异看，按 status 认）、状态 0 待盘点；
     * 录了实盘就是 1 已盘点，diff_quantity = 实盘 − 账面（正为盘盈、负为盘亏），
     * diff_amount 带符号（负差异就是负金额，主表那两个金额列才分得开盈/亏）
     */
    private void applyDiff(ShopErpInventoryCheckItem item) {
        int book = item.getBookQuantity() == null ? 0 : item.getBookQuantity();
        Integer actual = item.getActualQuantity();
        if (actual == null) {
            item.setDiffQuantity(0);
            item.setDiffAmount(null);
            item.setStatus(ShopErpInventoryCheckItem.STATUS_PENDING);
            return;
        }
        int diff = actual - book;
        item.setDiffQuantity(diff);
        item.setDiffAmount(item.getCostPrice() == null ? null
                : item.getCostPrice().multiply(BigDecimal.valueOf(diff)).setScale(2, RoundingMode.HALF_UP));
        // 差异为 0 也算"这一行盘过了"：它得和盘盈盘亏的行一样，在「调整」里点一次确认才进 2
        item.setStatus(ShopErpInventoryCheckItem.STATUS_COUNTED);
    }

    /**
     * 明细落库（新增那一路）：checkId 一律以主表 id 为准（请求体里带什么都不认），
     * 账面数/成本价在 snapshotStock 里带好了、差异和状态在 applyDiff 里算好了，这里只插
     */
    private void saveItems(ShopErpInventoryCheck check) {
        for (ShopErpInventoryCheckItem item : check.getItems()) {
            // 详情接口的字段会被原样塞回表单，明细带着原来的 id 回来；不清零 INSERT 就会显式带上主键
            item.setId(null);
            item.setCheckId(check.getId());
            itemMapper.insert(item);
        }
    }

    /**
     * 按明细重算"服务端说了算"的六个数：
     * total_sku_count = 明细行数、diff_sku_count = 差异不为 0 的行数、
     * profit_* 只累加差异为正的那几行、loss_* 只累加差异为负的那几行并取绝对值（所以四个数都是正的）。
     * 没录实盘的行差异记 0，加不进任何一档；成本价带不出的行没有金额，也只进数量不进金额
     */
    private void recalculate(ShopErpInventoryCheck check) {
        int totalSku = 0;
        int diffSku = 0;
        int profitQuantity = 0;
        int lossQuantity = 0;
        BigDecimal profitAmount = BigDecimal.ZERO;
        BigDecimal lossAmount = BigDecimal.ZERO;
        for (ShopErpInventoryCheckItem item : check.getItems()) {
            int diff = item.getDiffQuantity() == null ? 0 : item.getDiffQuantity();
            BigDecimal amount = item.getDiffAmount();
            totalSku++;
            if (diff > 0) {
                diffSku++;
                profitQuantity += diff;
            } else if (diff < 0) {
                diffSku++;
                lossQuantity -= diff;
            }
            if (amount != null) {
                int sign = amount.signum();
                if (sign > 0) {
                    profitAmount = profitAmount.add(amount);
                } else if (sign < 0) {
                    lossAmount = lossAmount.subtract(amount);
                }
            }
        }
        check.setTotalSkuCount(totalSku);
        check.setDiffSkuCount(diffSku);
        check.setProfitQuantity(profitQuantity);
        check.setLossQuantity(lossQuantity);
        check.setProfitAmount(profitAmount.setScale(2, RoundingMode.HALF_UP));
        check.setLossAmount(lossAmount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 按明细进度算主表状态：还有一行没在「调整」里确认就停在 1 盘点中，全部行都到 2 已调整 才进 2 已完成。
     *
     * 账实相符的那一行也要点一次确认（这是定稿口径）：无差异同样是"有人认过这一笔"的事实，
     * 自动跳过就等于这张单从没被人看过
     */
    private int progressStatus(List<ShopErpInventoryCheckItem> items) {
        for (ShopErpInventoryCheckItem item : items) {
            if (item.getStatus() == null || item.getStatus() != ShopErpInventoryCheckItem.STATUS_ADJUSTED) {
                return ShopErpInventoryCheck.STATUS_STOCKTAKING;
            }
        }
        return ShopErpInventoryCheck.STATUS_DONE;
    }

    /**
     * 审核 / 调整前的仓库闸门：这家仓还得在，而且现在确实是 2 盘点中 —— 也就是它仍然是被这张单冻着的。
     * 被人从仓库管理里改回启用（或改成调拨中）说明这轮盘点的账面口径已经废了，
     * 重开一张比在这里猜该怎么圆更清楚（同调拨单的 requireTransferWarehouse）
     */
    private void requireStocktakingWarehouse(Long warehouseId) {
        ShopErpWarehouse warehouse = warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new ServiceException("盘点仓库不存在或已被删除，这张盘点单没法继续，请取消后重建");
        }
        if (!Integer.valueOf(ShopErpWarehouse.STATUS_STOCKTAKING).equals(warehouse.getStatus())) {
            throw new ServiceException("仓库「" + warehouse.getWarehouseName() + "」现在不是盘点中状态，"
                    + "这张单的冻结已经丢了，请取消后重建");
        }
    }

    /** 建单闸门：仓库必须是 1 启用（禁用/盘点中/调拨中的仓都不能新起一张盘点单） */
    private void requireEnabled(ShopErpWarehouse warehouse) {
        if (!Integer.valueOf(ShopErpWarehouse.STATUS_ENABLED).equals(warehouse.getStatus())) {
            throw new ServiceException("盘点仓库「" + warehouse.getWarehouseName()
                    + "」不是启用状态，不能作为盘点仓库；请先在仓库管理里把它改回启用");
        }
    }

    /**
     * 新建盘点单：把这家仓冻成 2 盘点中。
     * 用 LambdaUpdateWrapper 只写 status 一列：capacity_used 是出入库那条链维护的冗余列，
     * 拿整行实体回写会把自己刚读到的那份盖回去，纯属找事
     */
    private void freezeWarehouse(Long warehouseId) {
        updateWarehouseStatus(warehouseId, ShopErpWarehouse.STATUS_STOCKTAKING);
    }

    /**
     * 单子走完了（2 已完成 / 3 已取消）或删掉了：把这家仓复位成 1 启用。
     *
     * 两道保留：
     * 1. 只有现在还是 2 盘点中 才复位 —— 有人在仓库管理里改成禁用/调拨中，那是他的决定，不该由这张单改回去；
     *    调拨单冻成的 3 也不归这张单解（两张单不可能同时占着同一家仓，这一道是防手工把状态改乱了）。
     * 2. 还有别的盘点单引用这家仓且它仍在跑（0/1，判据见 ShopErpInventoryCheck#inFlight）就不复位，
     *    否则先完成的那张会把别人那张的冻结解掉。
     *
     * 调用方要先把这张单自己的状态翻掉（或把它删掉）再来腾仓，不然这里会把自己也算成"还在跑的那一张"
     */
    private void releaseWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return;
        }
        ShopErpWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null || !Integer.valueOf(ShopErpWarehouse.STATUS_STOCKTAKING).equals(warehouse.getStatus())) {
            return;
        }
        Long holding = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpInventoryCheck>()
                .eq(ShopErpInventoryCheck::getWarehouseId, warehouseId)
                .in(ShopErpInventoryCheck::getStatus, ShopErpInventoryCheck.STATUS_PENDING,
                        ShopErpInventoryCheck.STATUS_STOCKTAKING));
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
