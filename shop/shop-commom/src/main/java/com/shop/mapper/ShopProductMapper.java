package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.product.ShopProductQueryDto;
import com.shop.dto.product.ShopProductWebQueryDto;
import com.shop.entity.ShopProduct;
import com.shop.vo.product.ShopProductVo;
import com.shop.vo.product.ShopProductWebVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品主表 Mapper接口
 */
@Mapper
public interface ShopProductMapper extends BaseMapper<ShopProduct> {

    /**
     * 分页查询商品，按 SKU 汇总带出价格区间/销量/可用库存，再联类目、品牌、门店名称
     */
    IPage<ShopProductVo> selectProductPage(IPage<ShopProductVo> page,
                                           @Param("q") ShopProductQueryDto query);

    /**
     * 查询单个商品，同样按 SKU 汇总带出价格区间/销量/可用库存与关联名称
     */
    ShopProductVo selectProductById(@Param("id") Long id);

    /**
     * Web 端分页：只看已上架的（分类的下级已在服务层展开进 query.categoryIds），
     * 不带数据范围（商城前台对所有人是同一份货）
     */
    IPage<ShopProductWebVo> selectWebPage(IPage<ShopProductWebVo> page,
                                          @Param("q") ShopProductWebQueryDto query);

    /**
     * Web 端单个商品：下架的一律查不出来，和列表同一口径
     */
    ShopProductWebVo selectWebById(@Param("id") Long id);
}
