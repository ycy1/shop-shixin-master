package com.shop.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
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
     */
    @Override
    public boolean update(SysOrder sysOrder) {
        fillUserInfo(sysOrder);
        return updateById(sysOrder);
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
