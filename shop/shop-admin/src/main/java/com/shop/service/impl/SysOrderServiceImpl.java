package com.shop.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shop.api.service.ShopOrderDeliveryService;
import com.shop.mapper.SysOrderMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.entity.SysOrder;
import com.shop.entity.SysUser;
import com.shop.service.SysOrderService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;

/**
 * 订单主表 服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysOrderServiceImpl extends ServiceImpl<SysOrderMapper, SysOrder> implements SysOrderService {

    private final SysUserMapper sysUserMapper;

    /** 下单那一刻就派生出来的那条配送单，订单没了它也得跟着没（见 update） */
    private final ShopOrderDeliveryService shopOrderDeliveryService;

    /** 订单状态（字典 order_status）：3 已取消 5 已关闭 —— 这两档都不再有货要发 */
    private static final int STATUS_CANCELLED = 3;

    private static final int STATUS_CLOSED = 5;

    /**
     * 查询订单主表分页列表
     */
    @Override
    public IPage<SysOrder> selectPage(SysOrder sysOrder) {
        return baseMapper.selectOrderPage(PageUtil.getPage() ,sysOrder);
    }


    /**
     * 新增订单主表
     */
    @Override
    public boolean insert(SysOrder sysOrder) {
        if (StringUtils.isBlank(sysOrder.getOrderNo())) {
            sysOrder.setOrderNo(GeneratorNoUtils.getOrderNo());
        }
        fillUserInfo(sysOrder);
        return save(sysOrder);
    }

    /**
     * 修改订单主表
     *
     * 后台把状态改成 3 已取消 / 5 已关闭 时，这一单名下那条下单时就建好的配送单跟着置 5 已取消：
     * 钱没收到、货也不会发了，却还在抢单池里挂着让送货员抢，抢到才发现取不到货。
     * 只收 0 待派单 和 1 待接单 那两档（真已经在路上的得去「配送管理」人工处理，那边看得见送货员）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SysOrder sysOrder) {
        fillUserInfo(sysOrder);
        boolean updated = updateById(sysOrder);
        Integer status = sysOrder.getStatus();
        if (updated && status != null && (status == STATUS_CANCELLED || status == STATUS_CLOSED)) {
            shopOrderDeliveryService.cancelByOrder(sysOrder.getId(),
                    status == STATUS_CLOSED ? "来源订单已关闭" : "来源订单已取消");
        }
        return updated;
    }

    /**
     * 批量删除订单主表
     */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }

    /**
     * 冗余用户昵称、手机号，避免列表查询关联 sys_user
     */
    private void fillUserInfo(SysOrder sysOrder) {
        if (sysOrder.getUserId() == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(sysOrder.getUserId());
        if (user == null) {
            return;
        }
        sysOrder.setUserName(user.getNickname());
        sysOrder.setMobile(user.getMobile());
    }
}
