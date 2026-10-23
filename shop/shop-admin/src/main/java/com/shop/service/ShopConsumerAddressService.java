package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.entity.ShopConsumerAddress;
import com.shop.vo.address.ShopAddressVo;

import java.util.List;

/**
 * 消费者收货地址 服务接口
 */
public interface ShopConsumerAddressService extends IService<ShopConsumerAddress> {

    /**
     * 分页查询收货地址（联表带出消费者昵称、用户名）
     *
     * @param query 查询条件（keyword/userId/tag/isDefault/时间范围）
     * @return 地址分页
     */
    IPage<ShopAddressVo> selectPage(ShopAddressQueryDto query);

    /**
     * 单条地址详情（供修改弹窗回显）
     *
     * @param id 地址id
     * @return 地址视图对象
     */
    ShopAddressVo selectAddressById(Long id);

    /**
     * 新增地址：该消费者的第一条地址强制为默认，设默认时把其余的置 0
     *
     * @param form 新增参数
     */
    void insert(ShopAddressSaveDto form);

    /**
     * 修改地址：换归属消费者时会按新消费者重算默认那条
     *
     * @param form 修改参数
     */
    void update(ShopAddressSaveDto form);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 地址id集合
     */
    void deleteByIds(List<Long> ids);
}
