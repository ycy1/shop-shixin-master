package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopConsumerWallet;
import com.shop.mapper.ShopConsumerWalletMapper;
import com.shop.service.ShopConsumerWalletService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

/**
 * 钱包账户 服务实现（后台这一页只有一条读，写法照积分账户 ShopActivityPointsServiceImpl）
 *
 * 数据范围：分账号类型两档 ——
 * 平台/门店/ERP 账号**不收窄**（钱包和积分是同一本全平台账，按登录账号收窄会让门店/ERP 账号
 * 看不见大批用户的钱包）；消费者账号只看自己那一行（「个人信息」那页的钱包 tab 直接调这一条，
 * 不认前端传谁的 id）。
 * 这里的 user_id 是"这些钱是谁的"，不是 create_by 那个"谁建的账"（同积分账户、购物车、用户券）。
 *
 * 后台这一页没有写入口：这一本是账，那几个数由统一下单/退款和充值/提现那两路（都在 shop-api）
 * 的条件增量改，后台不改账也不调账（积分那一份有「修改」那颗按钮，这一轮没照搬 —— 动钱的事等他点名）。
 */
@Service
public class ShopConsumerWalletServiceImpl extends ServiceImpl<ShopConsumerWalletMapper, ShopConsumerWallet>
        implements ShopConsumerWalletService {

    @Override
    public IPage<ShopConsumerWallet> selectPage(ShopConsumerWallet query) {
        if (query == null) {
            query = new ShopConsumerWallet();
        }
        // 消费者账号只看自己的那一行：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 昵称和手机号联 sys_user（sys_* 表没有 is_deleted 那一列，写法见 XML）
        return baseMapper.selectWalletPage(PageUtil.getPage(), query);
    }
}
