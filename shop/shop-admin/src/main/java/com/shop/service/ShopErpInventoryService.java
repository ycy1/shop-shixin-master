package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpInventory;

import java.math.BigDecimal;

/**
 * 商品库存 服务接口
 *
 * 这一张表对外只有"查"和"改预警参数"，没有新增和删除：库存行由入库动作自动建，
 * 手工删行等于把已入库的数量凭空抹掉（而且软删的行还占着 (sku_id, warehouse_id) 唯一键）。
 */
public interface ShopErpInventoryService extends IService<ShopErpInventory> {

    IPage<ShopErpInventory> selectPage(ShopErpInventory query);

    ShopErpInventory selectInventoryById(Long id);

    /**
     * 修改库存行：只放开成本价和 safety_stock / max_stock / stock_warn_enable 三个预警参数，
     * 库存数量三列（stock / locked_stock / in_transit_stock）和身份两列一律不接受请求体里的值
     */
    void update(ShopErpInventory inventory);

    /**
     * 入一批货：这条规格在这个仓库有库存行就加库存、按加权平均刷成本价，没有就建一行
     * （安全库存等三个参数从 shop_product_sku 快照过来），并且记一行库存流水。
     *
     * 由入库动作（ShopErpPurchaseInService#receive）调用，不单独开接口 —— 库存数量只能由单据改，
     * 库存流水也就只有 inbound / outbound（入库单、出库单）、transferIn / transferOut（库存调拨）
     * 和 checkIn / checkOut（库存盘点）这四组写入路径。
     *
     * @param skuId      入库的那条商品规格（shop_product_sku.id），库存行的身份之一
     * @param operatorId 记进 user_id 的操作人（"最后一次动这行的人"，不参与数据范围）
     * @param changeType 流水的变动类型，取值见 ShopErpInventoryLog 的常量（字典 inventory_change_type）
     * @param bizId      来源单据ID（采购入库那一条是入库单ID），只记进流水，可为 NULL
     * @param bizNo      来源单据的单号（采购入库那一条是入库单号），只记进流水，可为 NULL
     */
    void inbound(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                 int changeType, Long bizId, String bizNo);

    /**
     * 出一批货：这一行库存的总库存减去 quantity，同时把下单时锁住的那一份还掉 releaseLocked
     * （两个数分开传：一张出库单可以分几次出，每次还的锁定不一定等于本次出的数），
     * 记一行库存流水（change_quantity 为负），末尾重算仓库已用容量。
     *
     * 由出库动作（ShopErpSaleOutboundService 的出库）调用，同样不单独开接口。
     * 和 inbound 相反，这里**不会建库存行**：没有行就是这一仓从没进过这批货，出库该报错而不是记一笔负账。
     *
     * @param skuId         出库的那条商品规格（shop_product_sku.id），和 warehouseId 一起定位库存行
     * @param releaseLocked 本次还回的锁定数量，上限是"这一行当前锁着的那份"和"本次出库数"里小的那个，
     *                      超了就按上限还（口径见实现类 capRelease）
     * @return 这次记账用的那一版库存行上的成本价
     *         （加权平均是进货时算的，卖货不改它；这一仓从没算过成本价时返回 null）。
     *         留着给以后"按进货成本结转"的出库来源用 —— 目前唯一的调用方是线上订单那一路，
     *         它结的是订单实付价（支付回调时已经写在明细行上），所以这个返回值暂时没人接
     * @throws ServiceException 库存行不存在、总库存不够出、或出完后会让可用库存变负
     */
    BigDecimal outbound(Long skuId, Long warehouseId, int quantity, int releaseLocked, Long operatorId,
                        int changeType, Long bizId, String bizNo);

    /**
     * 调出仓那一侧的落库：这一行库存的总库存减去 quantity，记一行 6 调拨出库 的流水
     * （change_quantity 为负），末尾重算调出仓的已用容量。锁定库存不参与 —— 调拨的货是仓里还能动的那份，
     * 下单锁着的部分由出库那条路还。
     *
     * 为什么不直接用 {@link #outbound}：入库单/出库单那两条路上的仓库状态闸（盘点中/调拨中不许动账）
     * 会把调拨自己挡死 —— 新建调拨单就把两家仓冻成 3 调拨中了，这一路的账本来就该由调拨动作记，
     * 所以它走同一个落库口径、只是不套那道闸（判据由 ShopErpInventoryTransferService 那边把）。
     * 除了不拦冻结仓，其余和 outbound 完全一致：不建库存行、总库存/可用库存两道量在前、CAS 在后。
     *
     * @param skuId      调出的那条规格（shop_product_sku.id），和 warehouseId 一起定位库存行
     * @param quantity   本次调出件数，必须大于 0
     * @param operatorId 记进库存行 user_id 和流水 user_id 的操作人
     * @param bizId      来源调拨单ID（shop_erp_inventory_transfer.id），只记进流水
     * @param bizNo      来源调拨单号，只记进流水
     * @return 这次记账用的那一版库存行上的成本价（调拨明细那一格的 cost_price 就取它）
     * @throws ServiceException 库存行不存在、总库存不够调出、或调出后会让可用库存变负
     */
    BigDecimal transferOut(Long skuId, Long warehouseId, int quantity, Long operatorId,
                           Long bizId, String bizNo);

    /**
     * 调入仓那一侧的落库：这个规格在调入仓有库存行就加库存、按加权平均刷成本价，没有就建一行
     * （三个预警参数从 shop_product_sku 快照），记一行 5 调拨入库 的流水，末尾重算调入仓的已用容量。
     *
     * 和 {@link #transferOut} 同一对：调拨一次动作两侧各记一行流水、数量相同，账才连得起来。
     * 单价传的是**调出仓**那一行的成本价，所以调入仓加权进来的数是这批货的实际成本，不是售价。
     *
     * @param skuId      调入的那条规格（shop_product_sku.id）
     * @param unitPrice  这批货的成本价（调出仓带出来的那个数），不能为空且不能为负
     * @param operatorId 记进库存行 user_id 和流水 user_id 的操作人
     * @param bizId      来源调拨单ID，只记进流水
     * @param bizNo      来源调拨单号，只记进流水
     */
    void transferIn(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                    Long bizId, String bizNo);

    /**
     * 盘盈那一侧的落库：这一行库存加上多出来的那几件、成本价按加权平均刷（加的是"账外找到的货"，
     * 单价传的是这一行现有的成本价，所以加权之后仍然是一个仓内成本口径），记一行 7 盘盈 的流水，
     * 末尾重算仓库已用容量。
     *
     * 和 {@link #transferIn} 同一档理由，也不套仓库状态那道冻结闸：新建盘点单就把这家仓冻成
     * 2 盘点中了，盘点认下来的账本来就只能由盘点动作记（哪家仓能进这一路、哪几行能调，
     * 判据在 ShopErpInventoryCheckServiceImpl 那边）。
     *
     * @param skuId      盘盈的那条规格（shop_product_sku.id），和 warehouseId 一起定位库存行
     * @param quantity   多出来的件数（实盘 − 账面，正数），必须大于 0
     * @param unitPrice  这批货的成本价（盘点明细上带出的那一格），不能为空且不能为负
     * @param operatorId 记进库存行 user_id 和流水 user_id 的操作人
     * @param bizId      来源盘点单ID（shop_erp_inventory_check.id），只记进流水
     * @param bizNo      来源盘点单号，只记进流水
     */
    void checkIn(Long skuId, Long warehouseId, int quantity, BigDecimal unitPrice, Long operatorId,
                 Long bizId, String bizNo);

    /**
     * 盘亏那一侧的落库：这一行库存减去对不上的那几件，记一行 8 盘亏 的流水（change_quantity 为负），
     * 末尾重算仓库已用容量。锁定库存不参与归还 —— 盘点核的是仓里剩下的货，
     * 下单锁着的那一份仍然欠着订单，动它等于把已经卖出去的货又还回货架。
     *
     * 同样不套冻结闸，也同样**不建库存行**：账面为 0、实物却盘出来了，那是盘盈不是盘亏；
     * 连库存行都没有就说明这条规格从没进过这家仓，减一笔负账出来没意义。
     *
     * @param skuId      盘亏的那条规格（shop_product_sku.id），和 warehouseId 一起定位库存行
     * @param quantity   对不上的件数（账面 − 实盘，正数），必须大于 0
     * @param operatorId 记进库存行 user_id 和流水 user_id 的操作人
     * @param bizId      来源盘点单ID，只记进流水
     * @param bizNo      来源盘点单号，只记进流水
     * @return 这次记账用的那一版库存行上的成本价（盘点明细的差异金额就按建单时带出的那个数算，
     *         这里回传只为核对，两者不一致说明盘点期间有人刷过成本价）
     * @throws ServiceException 库存行不存在、总库存不够减、或减完会让可用库存变负
     */
    BigDecimal checkOut(Long skuId, Long warehouseId, int quantity, Long operatorId,
                        Long bizId, String bizNo);
}
