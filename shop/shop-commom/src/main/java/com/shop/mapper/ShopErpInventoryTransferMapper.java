package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存调拨单 Mapper接口（写法同 ShopErpPurchaseInMapper：列表和详情都走自定义 SQL 联表带名称）
 */
@Mapper
public interface ShopErpInventoryTransferMapper extends BaseMapper<ShopErpInventoryTransfer> {

    /**
     * 分页查询调拨单：关键字跨调拨单号/原因/备注/两家仓名/操作员名，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpInventoryTransfer> selectTransferPage(IPage<ShopErpInventoryTransfer> page,
            @Param("q") ShopErpInventoryTransfer query);

    /**
     * 单条调拨单（带两家仓名和它们各自的状态、操作员昵称，不带明细——明细由
     * ShopErpInventoryTransferItemMapper 另查，那一趟要带调出仓的可用库存）
     */
    ShopErpInventoryTransfer selectTransferById(@Param("id") Long id);

    /**
     * 调拨明细的规格候选：一行是**调出仓里的一条库存行**（shop_erp_inventory），
     * 所以"这个仓有没有这批货"这一档在候选阶段就分得出去，不用等到点调拨才发现挪不走。
     * 返回的是 ShopErpInventory 那一套列（skuId / 商品名编码 / 规格文本 / 成本价 / 可用库存），
     * 前端选中后落进明细的是 skuId，成本价直接带这一行库存上的那个数
     * 走本模块的 SQL 而不是商品库存那页的列表接口，是为了让只有调拨权限的 ERP 操作员也调得动
     */
    List<ShopErpInventory> selectSkuOptions(@Param("warehouseId") Long warehouseId,
            @Param("keyword") String keyword, @Param("limit") int limit);
}
