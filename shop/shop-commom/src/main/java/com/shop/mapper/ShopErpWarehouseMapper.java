package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpWarehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 仓库 Mapper接口
 *
 * 列表和详情都走这里的自定义 SQL：关联门店名称、操作员昵称要联表带出来，
 * 而 BaseMapper 的 selectById 挂不上联表，两条路各写一条才不会一边有一边没有（同采购单）。
 */
@Mapper
public interface ShopErpWarehouseMapper extends BaseMapper<ShopErpWarehouse> {

    /**
     * 分页查询仓库：关键字跨名称/编码/联系人/电话，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpWarehouse> selectWarehousePage(IPage<ShopErpWarehouse> page,
                                                @Param("q") ShopErpWarehouse query);

    /**
     * 单条仓库（带关联门店名称、操作员昵称）
     */
    ShopErpWarehouse selectWarehouseById(@Param("id") Long id);

    /**
     * 按 shop_erp_inventory 现算并回写「已用容量」（= 该仓库所有商品库存 stock 之和）。
     * 唯一调用方是库存入库 ShopErpInventoryServiceImpl#inbound —— 增量维护会漏（同一事务里多次入库、
     * 以后接出库），汇总重算一条 SQL 就是幂等的，也不用先读后写。
     */
    int refreshCapacityUsed(@Param("warehouseId") Long warehouseId);
}
