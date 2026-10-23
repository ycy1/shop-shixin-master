package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpPurchase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采购单 Mapper接口
 *
 * 列表和详情都走这里的自定义 SQL：供应商名称、操作员昵称要联表带出来，
 * 而 BaseMapper 的 selectById 挂不上联表，两条路各写一条才不会一边有一边没有。
 */
@Mapper
public interface ShopErpPurchaseMapper extends BaseMapper<ShopErpPurchase> {

    /**
     * 分页查询采购单：关键字跨单号/备注/供应商名称，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpPurchase> selectPurchasePage(IPage<ShopErpPurchase> page, @Param("q") ShopErpPurchase query);

    /**
     * 单条采购单（带供应商名称、操作员昵称，不带明细——明细由 ShopErpPurchaseItemMapper 另查）
     */
    ShopErpPurchase selectPurchaseById(@Param("id") Long id);
}
