package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryLog;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopProductSku;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpInventoryMapper;
import com.shop.mapper.ShopErpInventoryLogMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.service.ShopErpInventoryService;
import com.shop.utils.PageUtil;
import com.shop.utils.WarehouseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商品库存 服务实现
 *
 * 这张表是"账"不是"单据"，所以本服务只有六条路：查（列表/详情）、改预警参数（白名单四列）、
 * 入库落库（inbound，只给 ShopErpPurchaseInService#receive 调）、出库落库
 * （outbound，只给 ShopErpSaleOutboundService 的出库动作调）、调拨落库
 * （transferIn / transferOut，只给 ShopErpInventoryTransferService 的调拨动作调）、盘点落库
 * （checkIn / checkOut，只给 ShopErpInventoryCheckService 的调整动作调）。库存数量三列没有任何手工入口。
 *
 * 后四条是同一对私有助手 {@code moveIn} / {@code moveOut} 的四个门面，差别只有一处：
 * 入库/出库先过仓库状态那道冻结闸（盘点中/调拨中的仓不许动账），调拨和盘点那四条不过 ——
 * 新建调拨单本身就把两家仓冻成 3 调拨中、新建盘点单又把仓冻成 2 盘点中，
 * 这两路的账本来就只能由自己的单据动作记。
 *
 * locked_stock 一头一尾两个写入方：涨在统一下单，那边是 shop-api、只依赖 commom，摸不到本服务，
 * 所以直接按 {@link ShopErpInventoryMapper#lockStock} 那条条件增量锁；
 * 回在本服务的 outbound —— 货真出仓了才把下单锁的那一份同时还掉，不另开释放入口
 * （取消/超时那一路要归还锁定还得另做，这一版没有）。in_transit_stock 这一版还没有写入方、恒为 0。
 *
 * 数据范围：**故意不按 user_id 收窄**（和采购单/仓库/入库单那三件套相反）。库存行按
 * (规格, 仓库) 唯一、是大家共享的一行账，按人收窄会让后入的那个人看不见这行、于是各建一行、
 * 同一条规格的库存被算成两份。user_id 在这里只是"最后一次动这行的人"。
 *
 * 并发：写路径各有各的挡法，都靠 version。
 * 1. 改预警参数走 {@code updateById(patch)}，实体上标了 {@code @Version} 且项目注册了
 *    OptimisticLockerInnerInterceptor，所以 WHERE 自动带 version、SET 里自增，返回 false 就是撞车。
 * 2. 入库走 {@link ShopErpInventoryMapper#increaseStock}、出库走 {@link ShopErpInventoryMapper#decreaseStock}，
 *    都是手写 CAS —— 它们必须在 SQL 里做 {@code stock = stock ± delta} 的增量
 *    （插件不接管自定义 SQL，也不能把增量改成读-改-写）。
 *
 * 流水：inbound / outbound / transferIn / transferOut / checkIn / checkOut 每次记完成功后
 * 往 shop_erp_inventory_log 插一行（变动前后的库存就用那次 CAS 的快照，出库、调出和盘亏那三条
 * change_quantity 带负号），这是流水仅有的四组写入路径
 * （调拨一次记两行：调出 6 + 调入 5，带同一张调拨单的 id/单号；
 *  盘点一次调整按行记 7 盘盈 或 8 盘亏，带同一张盘点单的 id/单号，账实相符的行不动账也就不记）。
 * 查流水在 ShopErpInventoryLogService，那边也只有查询。
 * 每一路末尾都按库存重算仓库的 capacity_used（已用容量），所以仓库页那格「已用/容量」进出库后都会动。
 */
@Service
@RequiredArgsConstructor
public class ShopErpInventoryServiceImpl extends ServiceImpl<ShopErpInventoryMapper, ShopErpInventory>
        implements ShopErpInventoryService {

    /** 只用来校验商品还在（软删过的商品不该再往里入货）；规格那一头由 productSkuMapper 查 */
    private final ShopProductMapper productMapper;

    /** 建库存行时从这里快照三个预警参数（预警档位定在「一条规格」上，不是整件商品），也是入库前的存在性闸门 */
    private final ShopProductSkuMapper productSkuMapper;

    /** 入库成功后记一行库存流水。直接注 mapper：流水没有业务规则，套一层 service 只是把 save 转个手 */
    private final ShopErpInventoryLogMapper inventoryLogMapper;

    /** 入库后顺手刷仓库的「已用容量」。同样直接注 mapper：刷一个冗余列不值得走一遍仓库 service 的归属校验 */
    private final ShopErpWarehouseMapper warehouseMapper;

    @Override
    public IPage<ShopErpInventory> selectPage(ShopErpInventory query) {
        if (query == null) {
            query = new ShopErpInventory();
        }
        // 不按账号类型收窄，所以也不看请求体里的 userId（XML 里压根没有这一条条件）；
        // 能传进来的只有 keyword / productId / warehouseId / lowStockOnly 那几格筛选
        // 关键字跨商品名称、编码和仓库名搜，条件写在 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectInventoryPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopErpInventory selectInventoryById(Long id) {
        ShopErpInventory inventory = baseMapper.selectInventoryById(id);
        if (inventory == null) {
            throw new ServiceException("库存记录不存在");
        }
        return inventory;
    }

    @Override
    public void update(ShopErpInventory inventory) {
        if (inventory == null || inventory.getId() == null) {
            throw new ServiceException("库存ID不能为空");
        }
        ShopErpInventory exists = getById(inventory.getId());
        if (exists == null) {
            throw new ServiceException("库存记录不存在");
        }
        BigDecimal costPrice = inventory.getCostPrice();
        if (costPrice != null && costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("成本价不能为负");
        }
        Integer safetyStock = inventory.getSafetyStock();
        if (safetyStock == null || safetyStock < 0) {
            throw new ServiceException("安全库存不能为空且不能为负");
        }
        Integer maxStock = inventory.getMaxStock();
        if (maxStock != null && maxStock <= 0) {
            // 不设上限请留空（NULL），填 0 会让"高于最大库存"永远预警
            throw new ServiceException("最大库存要么不填（不设上限），要么大于 0");
        }
        Integer warnEnable = inventory.getStockWarnEnable();
        if (warnEnable == null || (warnEnable != 0 && warnEnable != 1)) {
            throw new ServiceException("是否预警只能是 1是 / 0否");
        }
        // 只认这四列：新建一个干净实体来 UPDATE，商品/仓库/三个数量列/操作人都不进 SET。
        // 比起"把入参清空再 updateById"，这样以后表加列不会被客户端多带的字段顺手写进来
        ShopErpInventory patch = new ShopErpInventory();
        patch.setId(inventory.getId());
        patch.setCostPrice(costPrice);
        patch.setSafetyStock(safetyStock);
        patch.setMaxStock(maxStock);
        patch.setStockWarnEnable(warnEnable);
        // 乐观锁：@Version + OptimisticLockerInnerInterceptor 会把这个值拼进 WHERE version = ?，
        // 并在 SET 里自增。前端带回它看到的那一版，就能挡住"弹窗开着时别人入了货、这边把刚算出来的
        // 加权成本价覆盖回去"；没带（或版本对不上）就退回刚读到的那一版，至少不丢自己这次的改动
        patch.setVersion(inventory.getVersion() != null ? inventory.getVersion() : exists.getVersion());
        if (!updateById(patch)) {
            throw new ServiceException("这条库存刚被其他操作员改过（入库或改参数），请刷新后重新提交");
        }
    }

    @Override
    public void inbound(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                        int changeType, Long bizId, String bizNo) {
        // 仓库这一道是记账层的总闸：盘点中/调拨中的仓不许动账。单据那几层（入库单、出库单）建单和
        // 确认时各自还会先拦一次，报错更具体；留这一道是为了不走单据校验的自动账 —— 退款回调建的退货入库单
        ShopErpWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (WarehouseUtils.frozen(warehouse)) {
            throw new ServiceException(WarehouseUtils.frozenMessage(warehouse, "入库"));
        }
        moveIn(skuId, warehouseId, quantity, unitPrice, operatorId, changeType, bizId, bizNo, "入库");
    }

    @Override
    public void transferIn(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                           Long bizId, String bizNo) {
        // 刻意不套上面那道冻结闸：新建调拨单就把调入仓冻成 3 调拨中了，这一路的账本来就该由调拨动作记
        // （哪家仓能进这一路，判据在 ShopErpInventoryTransferServiceImpl 那边）
        moveIn(skuId, warehouseId, quantity, unitPrice, operatorId,
                ShopErpInventoryLog.CHANGE_TYPE_TRANSFER_IN, bizId, bizNo, "调入");
    }

    @Override
    public void checkIn(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                        Long bizId, String bizNo) {
        // 同样不套冻结闸（理由见 transferIn）：新建盘点单就把这家仓冻成 2 盘点中了，
        // 盘点认下来的账本来就该由盘点动作记（哪几行能调，判据在 ShopErpInventoryCheckServiceImpl 那边）
        moveIn(skuId, warehouseId, quantity, unitPrice, operatorId,
                ShopErpInventoryLog.CHANGE_TYPE_CHECK_PROFIT, bizId, bizNo, "盘盈");
    }

    /**
     * 加库存的公共那一段：这一行账有就加、没有就建，成本价按加权平均刷，末尾记流水 + 刷仓库已用容量。
     * inbound 和 transferIn 共用，差别只在要不要先过仓库状态那道闸、流水的变动类型和报错里那个动作名。
     *
     * @param action 报错文案里的动作名（入库 / 调入）：同一句闸门，操作员在调拨页看到"请重新提交入库"会找不着北
     */
    private void moveIn(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                        int changeType, Long bizId, String bizNo, String action) {
        if (skuId == null || warehouseId == null) {
            throw new ServiceException("明细缺少商品规格或仓库，无法记账");
        }
        if (quantity <= 0) {
            throw new ServiceException("本次" + action + "数必须大于 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(action + "单价不能为空且不能为负");
        }
        // selectById 带 is_deleted = 0：规格或它所属的商品被删过的行查不出来，货不该进一个不存在的东西
        ShopProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new ServiceException("商品规格不存在或已被删除，无法" + action);
        }
        if (productMapper.selectById(sku.getProductId()) == null) {
            throw new ServiceException("这条规格所属的商品不存在或已被删除，无法" + action);
        }
        // uk_sku_warehouse 保证这个组合最多一行未删除的库存（逻辑删除的行由全局条件自动排掉）
        ShopErpInventory row = getOne(new LambdaQueryWrapper<ShopErpInventory>()
                .eq(ShopErpInventory::getSkuId, skuId)
                .eq(ShopErpInventory::getWarehouseId, warehouseId));
        int beforeStock;
        if (row == null) {
            try {
                save(newRow(sku, warehouseId, quantity, unitPrice, operatorId));
                // 这行账是刚建起来的，之前没有货，所以变动前是 0
                beforeStock = 0;
            } catch (DuplicateKeyException e) {
                // 两个操作员同时给同一条规格入第一次货：一个插成功、这个撞键。
                // 撞了就落到下面按已存在的那行做 CAS，别把对方的数量覆盖掉。
                // 注意：如果撞上的是一条软删行（这行被删过），下面这一步还是查不到它，
                // 那时会再抛一次 DuplicateKeyException —— 库存页不放删除按钮就是为了不让它发生
                row = getOne(new LambdaQueryWrapper<ShopErpInventory>()
                        .eq(ShopErpInventory::getSkuId, skuId)
                        .eq(ShopErpInventory::getWarehouseId, warehouseId));
                if (row == null) {
                    throw new ServiceException("这条规格在该仓库有一条已删除的库存记录占着唯一键，请先在库里恢复它");
                }
                beforeStock = increase(row, quantity, unitPrice, operatorId, action);
            }
        } else {
            beforeStock = increase(row, quantity, unitPrice, operatorId, action);
        }
        // 库存记完了才写流水：before/after 用的是那次 CAS 的同一份快照，账和流水必然对得上
        writeLog(skuId, warehouseId, changeType, quantity, beforeStock, operatorId, bizId, bizNo);
        // 仓库那格「已用容量 / 容量」跟着刷一次：加减库存数量只有 moveIn / moveOut 这两个入口
        // （入库单、出库单、调拨三条路都从这儿走），刷在这里就不会漏。
        // 按仓库现汇总重算（不是 +quantity），所以一张单几条明细落在同一个仓库也只会被算成同一个值
        warehouseMapper.refreshCapacityUsed(warehouseId);
    }

    @Override
    public BigDecimal outbound(Long skuId, Long warehouseId, int quantity, int releaseLocked, Long operatorId,
                               int changeType, Long bizId, String bizNo) {
        // 同 inbound：盘点中/调拨中的仓不许动账。这一路除了后台的出库动作，还有支付回调按订单建的出库单
        ShopErpWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (WarehouseUtils.frozen(warehouse)) {
            throw new ServiceException(WarehouseUtils.frozenMessage(warehouse, "出库"));
        }
        return moveOut(skuId, warehouseId, quantity, releaseLocked, operatorId, changeType, bizId, bizNo, "出库");
    }

    @Override
    public BigDecimal transferOut(Long skuId, Long warehouseId, int quantity, Long operatorId,
                                  Long bizId, String bizNo) {
        // 同样不套冻结闸（理由见 transferIn）；锁定那份不参与归还：调拨只能挪仓里还能动的那份货，
        // 下单锁着的坑由出库那条路还，所以 releaseLocked 传 0
        return moveOut(skuId, warehouseId, quantity, 0, operatorId,
                ShopErpInventoryLog.CHANGE_TYPE_TRANSFER_OUT, bizId, bizNo, "调出");
    }

    @Override
    public BigDecimal checkOut(Long skuId, Long warehouseId, int quantity, Long operatorId,
                               Long bizId, String bizNo) {
        // 和 transferOut 一个形状：不套冻结闸（盘点单自己就把这家仓冻成 2 盘点中了），
        // 锁定那份也不归还 —— 盘亏核的是仓里剩下的货，下单锁着的那一份还欠着订单，
        // 把它一起减掉等于把已经卖出去的货又还回货架（releaseLocked 同样传 0）
        return moveOut(skuId, warehouseId, quantity, 0, operatorId,
                ShopErpInventoryLog.CHANGE_TYPE_CHECK_LOSS, bizId, bizNo, "盘亏");
    }

    /**
     * 减库存的公共那一段：不建行、两道闸门先量后 CAS、记负数流水、末尾刷仓库已用容量。
     * outbound 和 transferOut 共用，差别同 moveIn。
     *
     * @param releaseLocked 本次还回的锁定数量（调拨那一路恒 0）
     * @param action        报错文案里的动作名（出库 / 调出）
     * @return 这次记账用的那一版库存行上的成本价
     */
    private BigDecimal moveOut(Long skuId, Long warehouseId, int quantity, int releaseLocked, Long operatorId,
                               int changeType, Long bizId, String bizNo, String action) {
        if (skuId == null || warehouseId == null) {
            throw new ServiceException("明细缺少商品规格或仓库，无法记账");
        }
        if (quantity <= 0) {
            throw new ServiceException("本次" + action + "数必须大于 0");
        }
        // 出库不建行：没有库存行就是这个仓从没进过这批货，凭空出一次会把账做成负数还没人发现
        ShopErpInventory row = getOne(new LambdaQueryWrapper<ShopErpInventory>()
                .eq(ShopErpInventory::getSkuId, skuId)
                .eq(ShopErpInventory::getWarehouseId, warehouseId));
        if (row == null) {
            throw new ServiceException("这条规格在该仓库还没有库存记录（从没入过库），无法" + action);
        }
        int stock = row.getStock() == null ? 0 : row.getStock();
        int locked = row.getLockedStock() == null ? 0 : row.getLockedStock();
        int release = capRelease(releaseLocked, row, quantity);
        // CAS 之前先把闸门量一遍：decreaseStock 里那三条不满足都只返回 0 行，报错只能说"刷新重试"；
        // 在这儿量准了，操作员看到的是"这一仓总共剩几件、可用剩几件"
        if (stock < quantity) {
            throw new ServiceException("该仓库总库存只剩 " + stock + " 件，本次要" + action + " " + quantity
                    + " 件，不够");
        }
        if (stock - locked - quantity + release < 0) {
            throw new ServiceException("该仓库可用库存只有 " + (stock - locked) + " 件，本次要" + action + " " + quantity
                    + " 件（这一行还能还回 " + release + " 件锁定），请核对下单时锁定的库存是否已经释放");
        }
        ShopErpInventory used = deduct(row, quantity, release, operatorId, action);
        // 流水的变动数量是负数（出库）：before - quantity = after，和账本用的是同一次 CAS 的快照
        writeLog(skuId, warehouseId, changeType, -quantity,
                used.getStock() == null ? 0 : used.getStock(), operatorId, bizId, bizNo);
        // 已用容量同样在出库后刷一次：这条路径真的把 stock 减下去了，重算口径（SUM(stock)）不用改
        warehouseMapper.refreshCapacityUsed(warehouseId);
        // 把这一版快照上的成本价带回给调用方结转出库金额：加权平均是进货时算的，卖货只按现有值取，
        // 调用方（线上订单那一路、还有调拨那一路的 cost_price）不用自己再查一次库存，
        // 也就不会拿到和这次记账不同版的数
        return used.getCostPrice();
    }

    /**
     * 新建库存行：只这一行货就是全部库存，所以成本价直接用本次单价，不做加权
     * （原库存 0 件时加权公式的分母是 0，本来也就等于本次单价）
     *
     * 三个预警参数从这条规格（shop_product_sku）上抄：库存行是「一条规格 × 一个仓」，
     * 补货档位本来就该按规格定，同一件商品的几个规格可以各设各的。
     */
    private ShopErpInventory newRow(ShopProductSku sku, Long warehouseId, int quantity,
                                    BigDecimal unitPrice, Long operatorId) {
        ShopErpInventory row = new ShopErpInventory();
        row.setSkuId(sku.getId());
        row.setWarehouseId(warehouseId);
        row.setUserId(operatorId);
        row.setStock(quantity);
        // 建行这一刻还没有货被锁、也没有在途，显式写 0（NOT NULL，靠库默认值也行，写清楚省得以后误判成 null）
        row.setLockedStock(0);
        row.setInTransitStock(0);
        row.setCostPrice(unitPrice);
        row.setVersion(0);
        // 规格上没设的走库默认值（安全库存 0、不设上限、预警开）
        row.setSafetyStock(sku.getSafetyStock() == null ? 0 : sku.getSafetyStock());
        row.setMaxStock(sku.getMaxStock());
        row.setStockWarnEnable(sku.getStockWarnEnable() == null ? 1 : sku.getStockWarnEnable());
        return row;
    }

    /**
     * 按 version 做 CAS 加库存；撞上就重读一次再试一遍，第二次还撞就报错让人重试 ——
     * 宁可让操作员再点一次入库/调拨，也不能把别人刚入的那批数量算丢。
     *
     * 返回这次更新用的"变动前总库存"：CAS 成功就说明这次读到的那一版是最新的（version 一样），
     * 所以记流水直接拿它，不必再读一遍库，也不会出现流水和账本各拿一份快照的情况。
     *
     * @param action 报错里的动作名，同 {@link #moveIn}
     */
    private int increase(ShopErpInventory row, int quantity, BigDecimal unitPrice, Long operatorId, String action) {
        BigDecimal costPrice = weightedCost(row, quantity, unitPrice);
        if (baseMapper.increaseStock(row.getId(), quantity, costPrice, operatorId, operatorId, row.getVersion()) > 0) {
            return row.getStock() == null ? 0 : row.getStock();
        }
        ShopErpInventory latest = getById(row.getId());
        if (latest == null) {
            throw new ServiceException("这条库存记录已被删除，请刷新后重新" + action);
        }
        if (baseMapper.increaseStock(latest.getId(), quantity,
                weightedCost(latest, quantity, unitPrice), operatorId, operatorId, latest.getVersion()) == 0) {
            throw new ServiceException("这条库存刚被其他操作员改过，请刷新后重新提交" + action);
        }
        return latest.getStock() == null ? 0 : latest.getStock();
    }

    /**
     * 记一行库存流水：变动类型、变动数量、变动前后的库存全部来自上面那次记账，
     * 前端和请求体都插不进来（before + change = after 必须自洽，否则流水和账本对不上）。
     *
     * @param changeQuantity 带符号：入库传正数、出库传负数
     *
     * 和入库/出库在同一个事务里（调用方 ShopErpPurchaseInService#receive、
     * ShopErpSaleOutboundService#outbound 都带着 @Transactional），
     * 所以要么库存和流水一起改成、要么一起回滚，不会出现"账动了流水没动"。
     */
    private void writeLog(Long skuId, Long warehouseId, int changeType, int changeQuantity, int beforeStock,
                          Long operatorId, Long bizId, String bizNo) {
        ShopErpInventoryLog log = new ShopErpInventoryLog();
        log.setSkuId(skuId);
        log.setWarehouseId(warehouseId);
        log.setUserId(operatorId);
        log.setChangeType(changeType);
        log.setChangeQuantity(changeQuantity);
        log.setBeforeStock(beforeStock);
        log.setAfterStock(beforeStock + changeQuantity);
        log.setBizId(bizId);
        log.setBizNo(bizNo);
        inventoryLogMapper.insert(log);
    }

    /**
     * 按 version 做 CAS 减库存 + 还锁定；撞上就重读一次再试一遍，第二次还撞就报错让人重试 ——
     * 宁可让操作员再点一次出库/调拨，也不能把别人刚入进来或刚锁掉的数算丢。
     *
     * 返回这次更新用的那一版快照：流水的"变动前总库存"和给调用方结转的成本价都取自它，
     * 口径同 {@link #increase} —— CAS 成功就说明这一版是最新的，不必再读一遍库。
     *
     * @param action 报错里的动作名，同 {@link #moveOut}
     */
    private ShopErpInventory deduct(ShopErpInventory row, int quantity, int release, Long operatorId, String action) {
        if (baseMapper.decreaseStock(row.getId(), quantity, release, operatorId, operatorId, row.getVersion()) > 0) {
            return row;
        }
        ShopErpInventory latest = getById(row.getId());
        if (latest == null) {
            throw new ServiceException("这条库存记录已被删除，请刷新后重新" + action);
        }
        // 重试的这一次按最新那一版重新量还锁定的上限：别人可能在两次尝试之间又锁了货或入了货，
        // 上一版算出来的 release 放到新版上可能已经超过当前锁定了（那条闸门会直接返回 0 行）
        if (baseMapper.decreaseStock(latest.getId(), quantity,
                capRelease(release, latest, quantity), operatorId, operatorId, latest.getVersion()) == 0) {
            throw new ServiceException("这一仓的库存刚被其他操作员动过、或者已经不够" + action + "，请刷新后重新提交");
        }
        return latest;
    }

    /**
     * 本次还回去的锁定数量上限：既不能超过这一仓当前锁着的那份，也不能超过本次出库数
     * （出了 3 件就该消掉 3 件的坑，坑里剩下的部分是别的订单锁的，不该由这张单来填）。
     * 传进来的 releaseLocked 由调用方按"这一行下单时锁了多少、已经还了多少"算，这里只兜边界。
     */
    private int capRelease(int releaseLocked, ShopErpInventory row, int quantity) {
        int locked = row.getLockedStock() == null ? 0 : row.getLockedStock();
        return Math.min(Math.max(releaseLocked, 0), Math.min(locked, quantity));
    }

    /**
     * 加权平均成本价：(原库存数量 × 原成本价 + 本次入库数 × 本次单价) ÷ (原库存数量 + 本次入库数)，
     * 保留两位四舍五入。原库存为 0 或还没算出过成本价（行是别处建的）时，加权就是本次单价本身。
     */
    private BigDecimal weightedCost(ShopErpInventory row, int quantity, BigDecimal unitPrice) {
        int oldStock = row.getStock() == null ? 0 : row.getStock();
        BigDecimal oldCost = row.getCostPrice();
        if (oldCost == null || oldStock <= 0) {
            return unitPrice;
        }
        BigDecimal oldAmount = oldCost.multiply(BigDecimal.valueOf(oldStock));
        BigDecimal inAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return oldAmount.add(inAmount)
                .divide(BigDecimal.valueOf(oldStock + (long) quantity), 2, RoundingMode.HALF_UP);
    }
}
