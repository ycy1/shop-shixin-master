package com.shop.service;

import com.shop.entity.SysRefund;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/**
 * 退款表 服务接口
 */
public interface SysRefundService extends IService<SysRefund> {
    /**
     * 查询退款表分页列表
     */
    IPage<SysRefund> selectPage(SysRefund sysRefund);


    /**
     * 新增退款表
     */
    boolean insert(SysRefund sysRefund);

    /**
     * 修改退款表
     */
    boolean update(SysRefund sysRefund);

    /**
     * 审核退款：入参 status 就是审核完要落的那一档 —— 3 退款中（通过）/ 2 审核拒绝（拒绝）
     */
    boolean audit(SysRefund sysRefund);

    /**
     * 批量删除退款表
     */
    boolean deleteByIds(List<Long> ids);
}
