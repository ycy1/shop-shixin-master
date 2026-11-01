package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryCheck;

import java.util.List;

/**
 * 库存盘点单 服务接口
 *
 * 这一张表是"单据"，形状同库存调拨单：有增删改，但**库存数量一列都不由表单决定** ——
 * 动库存的只有「调整」那一个动作（adjust），它按明细的差异数直接改盘点仓那几行库存，
 * 不经过入库单/出库单，流水记 7 盘盈 / 8 盘亏。
 *
 * 和调拨单相反的一点：调拨两家仓之间挪货、一次记完两侧；盘点只动**一家仓自己的账**，
 * 而且从建单起就把这家仓冻成 2 盘点中，整张单期间别人进不来（出入库、下单分仓都被拦）。
 */
public interface ShopErpInventoryCheckService extends IService<ShopErpInventoryCheck> {

    IPage<ShopErpInventoryCheck> selectPage(ShopErpInventoryCheck query);

    /**
     * 单条盘点单（连明细，明细那一趟带出这条规格在盘点仓现在的库存数和还能动几件）
     */
    ShopErpInventoryCheck selectCheckById(Long id);

    /**
     * 明细的规格候选：盘点仓里的库存行，一行是一条规格
     *
     * 和调拨那一份的区别是不筛可用库存 —— 账面 0 件的那一行恰恰最该盘（实物在就是盘盈）。
     *
     * @param warehouseId 盘点仓库ID，必填（前端那一格要先选了才调这个接口）
     * @param keyword     商品名称/编码/规格文本关键字
     */
    List<ShopErpInventory> selectSkuOptions(Long warehouseId, String keyword);

    void insert(ShopErpInventoryCheck check);

    /**
     * 修改：0 待审核（改盘点范围 + 录实盘）和 1 盘点中（主要录实盘、也能补漏掉的规格）两档都开放，
     * 2 已完成 / 3 已取消 不许改；盘点仓钉回原值（换仓只能取消重建）
     */
    void update(ShopErpInventoryCheck check);

    void deleteByIds(List<Long> ids);

    /**
     * 审核：0 待审核 → 1 盘点中，并补 start_time。只有审核过的单子才开始录实盘、才允许调整
     */
    void audit(Long id);

    /**
     * 执行调整（列表上那颗「调整」）：按这一行的差异数量直接改库存 ——
     * 差异为正走 7 盘盈（加库存）、为负走 8 盘亏（减库存）、为 0 的只是认过这一笔、不动账不记流水。
     * 一行整行一次调平（没有"本次调整数"），调完的行 status 置 2 已调整。
     * 一张单可以点多次（分几批调），全部行都到 2 才把主表推成 2 已完成、补 end_time、把仓库复位成启用。
     *
     * 请求体只认 (明细行 id, 本次是否调整, 差异原因)，账面/实盘/差异/规格全以库里的行为准
     */
    void adjust(ShopErpInventoryCheck form);

    /**
     * 取消（作废）：0 待审核 / 1 盘点中 → 3 已取消，并腾出盘点仓的盘点中状态。
     * 只要有一行已经调整过（status=2）就不许取消 —— 那几笔库存账已经动了，回退得反向再调一次，是另一张单的活
     */
    void cancel(Long id);
}
