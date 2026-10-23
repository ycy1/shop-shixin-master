package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.entity.ShopConsumerAddress;
import com.shop.vo.address.ShopAddressVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消费者收货地址 Mapper接口
 */
@Mapper
public interface ShopConsumerAddressMapper extends BaseMapper<ShopConsumerAddress> {

    /**
     * 分页查询收货地址，联表带出消费者昵称/用户名
     */
    IPage<ShopAddressVo> selectAddressPage(IPage<ShopAddressVo> page, @Param("q") ShopAddressQueryDto query);

    /**
     * 查询单条收货地址（同上，供修改弹窗回显）
     */
    ShopAddressVo selectAddressById(@Param("id") Long id);
}
