package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopErpInventoryTransferItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调拨明细 Mapper接口（同 ShopErpPurchaseInItemMapper：名称联表带，一次把该带的都带上）
 */
@Mapper
public interface ShopErpInventoryTransferItemMapper extends BaseMapper<ShopErpInventoryTransferItem> {

    /**
     * 一张调拨单的明细。
     *
     * @param transferId  调拨单id
     * @param warehouseId 这张单的调出仓库id：拿它再联一跳 shop_erp_inventory，把"这一条规格在调出仓
     *                    还剩几件可用"一起带出来（「调拨」弹窗里那一格要用，也是它和库存行唯一的那条路）。
     *                    传 null 就没有这一格（比如主表那两家仓已经被删了）
     */
    List<ShopErpInventoryTransferItem> selectItemsByTransferId(@Param("transferId") Long transferId,
            @Param("warehouseId") Long warehouseId);
}
