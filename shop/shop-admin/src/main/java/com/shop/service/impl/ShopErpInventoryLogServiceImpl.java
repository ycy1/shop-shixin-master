package com.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpInventoryLog;
import com.shop.mapper.ShopErpInventoryLogMapper;
import com.shop.service.ShopErpInventoryLogService;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

/**
 * 库存流水 服务实现
 *
 * 只有查询。数据范围：**故意不按 user_id 收窄**（和 shop_erp_inventory 同一口径）——
 * 流水是那行共享账本的进出明细，按人筛会让别人入的货在这行账上凭空消失，
 * 谁登进来看到的都是同一本流水。能传的筛选条件只有 productId / warehouseId / changeType。
 */
@Service
public class ShopErpInventoryLogServiceImpl extends ServiceImpl<ShopErpInventoryLogMapper, ShopErpInventoryLog>
        implements ShopErpInventoryLogService {

    @Override
    public IPage<ShopErpInventoryLog> selectPage(ShopErpInventoryLog query) {
        if (query == null) {
            query = new ShopErpInventoryLog();
        }
        // 名称联表、is_deleted = 0 都写在 XML（自定义 SQL 不走全局逻辑删除）
        return baseMapper.selectLogPage(PageUtil.getPage(), query);
    }
}
