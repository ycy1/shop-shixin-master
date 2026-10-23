package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopDeliveryFeeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 配送费规则表 Mapper接口
 */
@Mapper
public interface ShopDeliveryFeeRuleMapper extends BaseMapper<ShopDeliveryFeeRule> {

    /**
     * 分页查询配送费规则：关键字模糊匹配规则名称
     */
    IPage<ShopDeliveryFeeRule> selectRulePage(IPage<ShopDeliveryFeeRule> page,
                                                   @Param("q") ShopDeliveryFeeRule query);

    /**
     * 取这一种配送类型当前该用哪条规则：启用的里优先级最高，同优先级取 id 最小那条。
     * 一条都没有返回 null（那就不算钱，配送费留 0）。
     */
    ShopDeliveryFeeRule selectTopRule(@Param("deliveryType") Integer deliveryType);
}
