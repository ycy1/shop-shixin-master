package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryTransfer;

import java.util.List;

/**
 * 库存调拨单 服务接口
 *
 * 这一张表是"单据"，所以和商品库存那张账相反：有增删改，但**库存数量一列都不由表单决定**。
 * 动库存的只有「调拨」那一个动作（transfer），它直接落到两家仓的库存行上，
 * 不经过入库单/出库单（这是定稿口径：调拨单自己一次记完两侧，见实现类的 transfer）。
 */
public interface ShopErpInventoryTransferService extends IService<ShopErpInventoryTransfer> {

    IPage<ShopErpInventoryTransfer> selectPage(ShopErpInventoryTransfer query);

    /**
     * 单条调拨单（连明细，明细那一趟带出调出仓这条规格的可用库存）
     */
    ShopErpInventoryTransfer selectTransferById(Long id);

    /**
     * 明细的规格候选：调出仓里有可用库存的那些库存行（一行是一条规格）
     *
     * @param warehouseId 调出仓库ID，必填（前端那一格要先选了才调这个接口）
     * @param keyword     商品名称/编码/规格文本关键字
     */
    List<ShopErpInventory> selectSkuOptions(Long warehouseId, String keyword);

    void insert(ShopErpInventoryTransfer transfer);

    void update(ShopErpInventoryTransfer transfer);

    void deleteByIds(List<Long> ids);

    /**
     * 审核：0 待审核 → 1 已审核，并补 audit_time。只有审核过的单子才允许开始调拨
     */
    void audit(Long id);

    /**
     * 执行调拨（列表上那颗「调拨」）：按每行的本次调拨数，调出仓减一次、调入仓加一次，
     * 两侧同一批数量、同一个事务，各记一行库存流水（6 调拨出库 / 5 调拨入库）。
     * 一张单可以点多次，全部调满就置 3 已完成并把两家仓复位成 1 启用。
     *
     * 请求体只认 (明细行 id, 本次调拨数)，规格/计划数/仓库全以库里的行为准
     */
    void transfer(ShopErpInventoryTransfer form);

    /**
     * 取消（作废）：0 待审核 / 1 已审核 → 4 已取消。这两档一笔库存都没动过，所以只翻状态、
     * 再把两家仓的调拨中状态腾出来；已经调出过货（2/3）的单子不许取消
     */
    void cancel(Long id);
}
