package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopConsumerWalletLog;
import com.shop.mapper.ShopConsumerWalletLogMapper;
import com.shop.service.ShopConsumerWalletLogService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

/**
 * 钱包流水 服务实现（只有查询，写法照积分流水 ShopActivityPointsLogServiceImpl）
 *
 * 这一页是按"某一个人的账"翻他的进出明细，所以筛选条件就是那一行的 user_id，页面上不给改；
 * 收窄口径同账户表那一侧（ShopConsumerWalletServiceImpl）：平台/门店/ERP 账号看全部，
 * 消费者账号只看自己那一本 —— 个人信息页的钱包 tab 走的就是这一条。
 */
@Service
public class ShopConsumerWalletLogServiceImpl extends ServiceImpl<ShopConsumerWalletLogMapper, ShopConsumerWalletLog>
        implements ShopConsumerWalletLogService {

    @Override
    public IPage<ShopConsumerWalletLog> selectRecordPage(ShopConsumerWalletLog query) {
        if (query == null) {
            query = new ShopConsumerWalletLog();
        }
        // 消费者账号只看自己的流水：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        return baseMapper.selectRecordPage(PageUtil.getPage(), query);
    }
}
