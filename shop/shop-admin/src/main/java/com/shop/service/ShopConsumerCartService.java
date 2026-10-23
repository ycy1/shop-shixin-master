package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.cart.ShopCartQueryDto;
import com.shop.dto.cart.ShopCartSaveDto;
import com.shop.entity.ShopConsumerCart;
import com.shop.vo.cart.ShopCartVo;

import java.util.List;

/**
 * 购物车 服务接口
 */
public interface ShopConsumerCartService extends IService<ShopConsumerCart> {

    /**
     * 分页查询购物车（联表带出商品、分类、品牌、门店、消费者名称）
     *
     * @param query 查询条件（keyword/userId/categoryId/storeId/selected/时间范围）
     * @return 购物车分页
     */
    IPage<ShopCartVo> selectPage(ShopCartQueryDto query);

    /**
     * 单条购物车详情（供修改弹窗回显）
     *
     * @param id 购物车记录id
     * @return 购物车视图对象
     */
    ShopCartVo selectCartById(Long id);

    /**
     * 新增购物车记录：分类/品牌/门店按商品带出，(消费者, 商品) 已存在时报错
     *
     * @param form 新增参数
     */
    void insert(ShopCartSaveDto form);

    /**
     * 修改购物车记录：换商品时会重新按新商品带出分类/品牌/门店
     *
     * @param form 修改参数
     */
    void update(ShopCartSaveDto form);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 记录id集合
     */
    void deleteByIds(List<Long> ids);
}
