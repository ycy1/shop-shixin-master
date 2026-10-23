package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpInventoryLog;

/**
 * 库存流水 服务接口
 *
 * 对外只有"查"：一行流水必须由一次真实的库存变动带出来，所以没有新增/修改/删除接口。
 * 写入方在 ShopErpInventoryServiceImpl#inbound 里面（它直接注 mapper 插一行，
 * 因为变动前后的库存数只有那边知道），不从这里开入口。
 */
public interface ShopErpInventoryLogService extends IService<ShopErpInventoryLog> {

    /**
     * 分页查流水：按 (商品, 仓库) 看一行库存的进出明细，也可只按商品看它所有仓的流水
     */
    IPage<ShopErpInventoryLog> selectPage(ShopErpInventoryLog query);
}
