package com.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivityClick;
import com.shop.mapper.ShopActivityClickMapper;
import com.shop.service.ShopActivityClickService;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

/**
 * 活动曝光/点击记录 服务实现（只有查询，写法照库存流水 ShopErpInventoryLogServiceImpl）
 *
 * 数据范围：**故意不收窄**（同库存流水、活动列表那两页的口径）—— 这一页是平台侧看投放效果，
 * 按账号一收窄就会出现"别人碰过的记录在这份明细里凭空消失"。
 * 能传的筛选条件只有 activityId / eventType / userId 三格。
 */
@Service
public class ShopActivityClickServiceImpl extends ServiceImpl<ShopActivityClickMapper, ShopActivityClick>
        implements ShopActivityClickService {

    @Override
    public IPage<ShopActivityClick> selectRecordPage(ShopActivityClick query) {
        if (query == null) {
            query = new ShopActivityClick();
        }
        // 昵称联 sys_user、is_deleted = 0 都写在 XML（自定义 SQL 不走全局逻辑删除）
        return baseMapper.selectRecordPage(PageUtil.getPage(), query);
    }
}
