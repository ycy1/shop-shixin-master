package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpInventoryCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存盘点单 Mapper接口（写法同 ShopErpInventoryTransferMapper：列表和详情都走自定义 SQL 联表带名称）
 */
@Mapper
public interface ShopErpInventoryCheckMapper extends BaseMapper<ShopErpInventoryCheck> {

    /**
     * 分页查询盘点单：关键字跨盘点单号/仓库名/操作员昵称/备注，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpInventoryCheck> selectCheckPage(IPage<ShopErpInventoryCheck> page,
            @Param("q") ShopErpInventoryCheck query);

    /**
     * 单条盘点单（带仓库名和它现在的状态、操作员昵称，不带明细——明细由
     * ShopErpInventoryCheckItemMapper 另查，那一趟要带这家仓现在的库存数）
     */
    ShopErpInventoryCheck selectCheckById(@Param("id") Long id);

    /**
     * 盘点明细的规格候选：一行是**这家仓里的一条库存行**（shop_erp_inventory）。
     * 和调拨那一档的区别是不筛可用库存 —— 盘点要的是"这家仓登记在册的规格有哪些"，
     * 一条规格账面剩 0 件恰恰是最该盘的一行（实物却有就是盘盈），所以 stock = 0 的行也在候选里。
     * 返回的是 ShopErpInventory 那一套列（skuId / 商品名编码 / 规格文本 / 成本价 / 库存数），
     * 前端选中后落进明细的是 skuId，book_quantity 带的就是这一行的 stock，cost_price 带这一行的成本价
     * 走本模块的 SQL 而不是商品库存那页的列表接口，是为了让只有盘点权限的 ERP 操作员也调得动
     */
    List<ShopErpInventory> selectSkuOptions(@Param("warehouseId") Long warehouseId,
            @Param("keyword") String keyword, @Param("limit") int limit);
}
