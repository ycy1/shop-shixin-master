package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpPurchaseIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 入库单 Mapper接口（写法同 ShopErpPurchaseMapper：列表和详情都走自定义 SQL 联表带名称）
 */
@Mapper
public interface ShopErpPurchaseInMapper extends BaseMapper<ShopErpPurchaseIn> {

    /**
     * 分页查询入库单：关键字跨入库单号/备注/采购单号/供应商名/仓库名，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpPurchaseIn> selectPurchaseInPage(IPage<ShopErpPurchaseIn> page,
            @Param("q") ShopErpPurchaseIn query);

    /**
     * 单条入库单（带采购单号、供应商名、仓库名、操作人昵称，不带明细——明细由 ShopErpPurchaseInItemMapper 另查）
     */
    ShopErpPurchaseIn selectPurchaseInById(@Param("id") Long id);

    /**
     * 一张采购单名下的全部入库单（不分页，字段同列表，不带明细——明细由调用方一次批量捞完挂上去）。
     * 用在采购单页那个「入库单」弹窗：那里要一次摊开这几张单的商品行，
     * 走分页接口拿不到明细，就得每张单再打一次详情（N+1）
     *
     * @param purchaseId 采购单id
     * @param userId     数据范围：ERP 账号传登录 id 只看自己操作的几张，其它账号传 null 看全部
     */
    List<ShopErpPurchaseIn> selectReceiptsByPurchaseId(@Param("purchaseId") Long purchaseId,
            @Param("userId") Long userId);
}
