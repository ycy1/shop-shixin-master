package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.service.ShopActivityPointsLogService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

/**
 * 用户积分流水 服务实现（只有查询，写法照库存流水 ShopErpInventoryLogServiceImpl）
 *
 * 这一页是按"某一个人的账"翻他的进出明细，所以筛选条件就是那一行的 user_id，页面上不给改；
 * 收窄口径同账户表那一侧（ShopActivityPointsServiceImpl）：平台/门店/ERP 账号看全部，
 * 消费者账号只看自己那一本 —— 个人信息页的积分 tab 走的就是这一条。
 */
@Service
public class ShopActivityPointsLogServiceImpl extends ServiceImpl<ShopActivityPointsLogMapper, ShopActivityPointsLog>
        implements ShopActivityPointsLogService {

    @Override
    public IPage<ShopActivityPointsLog> selectRecordPage(ShopActivityPointsLog query) {
        if (query == null) {
            query = new ShopActivityPointsLog();
        }
        // 消费者账号只看自己的流水：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        return baseMapper.selectRecordPage(PageUtil.getPage(), query);
    }
}
