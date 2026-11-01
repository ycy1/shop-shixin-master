package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.product.ShopProductSkuQueryDto;
import com.shop.dto.product.ShopProductSkuSaveDto;
import com.shop.entity.ShopProductSku;
import com.shop.vo.product.ShopProductSkuVo;

import java.util.List;

/**
 * 商品SKU 服务类
 *
 * 一条 SKU = 一件商品的一个具体规格（无规格的那条叫默认 SKU，spec_ids 是空串）。
 * 单价、重量、库存预警、积分、能不能单卖都在这一层；整件商品的事（含上下架）在 ShopProductService。
 */
public interface ShopProductSkuService extends IService<ShopProductSku> {

    /**
     * 分页查询 SKU（跨商品，给「按规格挑一行」的入口用）
     */
    IPage<ShopProductSkuVo> selectPage(ShopProductSkuQueryDto query);

    /**
     * 某个商品名下的全部 SKU，商品页那颗「SKU管理」按钮用的，不分页
     */
    List<ShopProductSkuVo> selectByProductId(Long productId);

    /**
     * 新增一条 SKU：规格文本由服务层按 spec_ids 从规格字典现拼，编码不传就系统生成
     */
    void insert(ShopProductSkuSaveDto form);

    /**
     * 修改一条 SKU：所属商品改不动，规格改了会重拼规格文本
     */
    void update(ShopProductSkuSaveDto form);

    /**
     * 批量删除 SKU（逻辑删除）；一件商品名下最后一条不给删
     */
    void deleteByIds(List<Long> ids);
}
