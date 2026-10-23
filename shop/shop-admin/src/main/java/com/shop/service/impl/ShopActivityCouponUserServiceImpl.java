package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.service.ShopActivityCouponUserService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

/**
 * 用户优惠券 服务实现（只有查询，写法照活动曝光/点击记录 ShopActivityClickServiceImpl）
 *
 * 数据范围：分账号类型两档 —— 平台/门店/ERP 账号**不收窄**（同活动明细、库存流水那两页的口径：
 * 这一页是平台侧看一张券发到了谁手上，一收窄就会出现"别人发出去的那几张在这份明细里凭空消失"）；
 * 消费者账号只看自己名下的那几张（个人信息页的优惠券 tab 走的就是这一条）。
 * 发放那一条写入路不在这里：它要占模板的发行额度，和占额度同一个事务才谈得上不超发，
 * 所以挂在 ShopActivityCouponServiceImpl#give 上。
 */
@Service
public class ShopActivityCouponUserServiceImpl extends ServiceImpl<ShopActivityCouponUserMapper, ShopActivityCouponUser>
        implements ShopActivityCouponUserService {

    @Override
    public IPage<ShopActivityCouponUser> selectRecordPage(ShopActivityCouponUser query) {
        if (query == null) {
            query = new ShopActivityCouponUser();
        }
        // 消费者账号只看自己名下的券：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 昵称和手机号联 sys_user、is_deleted = 0 都写在 XML（自定义 SQL 不走全局逻辑删除）
        return baseMapper.selectRecordPage(PageUtil.getPage(), query);
    }
}
