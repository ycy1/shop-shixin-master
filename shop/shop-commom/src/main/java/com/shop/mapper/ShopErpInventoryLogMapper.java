package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpInventoryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存流水 Mapper接口
 *
 * 只有查（列表）和 MP 自带的 insert（入库动作记流水）；这张表没有改和删的入口。
 * 列表走自定义 SQL 是因为要联商品名/编码、规格文本、仓库名、操作人昵称（同 ShopErpInventoryMapper）。
 */
@Mapper
public interface ShopErpInventoryLogMapper extends BaseMapper<ShopErpInventoryLog> {

    /**
     * 分页查流水：固定按 (规格, 仓库) 看一行库存的进出明细（库存行本来就是「一个 SKU × 一个仓」），
     * 条件写在 XML 里（代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）。
     *
     * 查询对象是实体本身，所以 productId 那一格只是查询条件（实体上标了 exist=false）：
     * 本表没有商品列，XML 里借 sk.product_id 筛，命中该商品名下所有规格的流水。
     */
    IPage<ShopErpInventoryLog> selectLogPage(IPage<ShopErpInventoryLog> page,
                                             @Param("q") ShopErpInventoryLog query);
}
