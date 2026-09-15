package com.shop.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import cn.dev33.satoken.stp.StpUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysRefundMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.entity.SysRefund;
import com.shop.entity.SysUser;
import com.shop.service.SysRefundService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;

/**
 * 退款表 服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysRefundServiceImpl extends ServiceImpl<SysRefundMapper, SysRefund> implements SysRefundService {

    private final SysUserMapper sysUserMapper;

    /** 退款状态：待审核 */
    private static final int STATUS_PENDING_AUDIT = 0;
    /** 审核结果：审核通过 */
    private static final int AUDIT_PASS = 1;
    /** 审核结果：审核拒绝 */
    private static final int AUDIT_REJECT = 2;

    /**
     * 查询退款表分页列表
     */
    @Override
    public IPage<SysRefund> selectPage(SysRefund sysRefund) {
        return baseMapper.selectRefundPage(PageUtil.getPage(), sysRefund);
    }


    /**
     * 新增退款表
     */
    @Override
    public boolean insert(SysRefund sysRefund) {
        if (StringUtils.isBlank(sysRefund.getRefundNo())) {
            sysRefund.setRefundNo(GeneratorNoUtils.getRefundNo());
        }
        if (sysRefund.getApplyTime() == null) {
            sysRefund.setApplyTime(LocalDateTime.now());
        }
        fillUserInfo(sysRefund);
        return save(sysRefund);
    }

    /**
     * 修改退款表
     */
    @Override
    public boolean update(SysRefund sysRefund) {
        fillUserInfo(sysRefund);
        return updateById(sysRefund);
    }

    /**
     * 审核退款
     * 只允许待审核状态的单据被审核；审核通过时可同时确定实际退款金额
     */
    @Override
    public boolean audit(SysRefund sysRefund) {
        SysRefund exists = getById(sysRefund.getId());
        if (exists == null) {
            throw new ServiceException("退款单不存在");
        }

        Integer status = sysRefund.getStatus();
        if (status == null || (status != AUDIT_PASS && status != AUDIT_REJECT)) {
            throw new ServiceException("审核结果只能是审核通过或审核拒绝");
        }
        if (exists.getStatus() == null || exists.getStatus() != STATUS_PENDING_AUDIT) {
            throw new ServiceException("该退款单已处理，不能重复审核");
        }

        // 只更新审核相关字段，避免其余入参被写库
        SysRefund update = new SysRefund();
        update.setId(exists.getId());
        update.setStatus(status);
        update.setAuditTime(LocalDateTime.now());
        update.setAuditorId(StpUtil.getLoginIdAsLong());
        update.setAuditRemark(sysRefund.getAuditRemark());
        if (status == AUDIT_PASS) {
            // 未填写实际退款金额时，按申请金额全额退
            update.setActualRefundAmount(sysRefund.getActualRefundAmount() != null
                    ? sysRefund.getActualRefundAmount()
                    : exists.getRefundAmount());
        }
        return updateById(update);
    }

    /**
     * 批量删除退款表
     */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }

    /**
     * 冗余申请人昵称、手机号，避免列表查询关联 sys_user
     */
    private void fillUserInfo(SysRefund sysRefund) {
        if (sysRefund.getUserId() == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(sysRefund.getUserId());
        if (user == null) {
            return;
        }
        sysRefund.setUserName(user.getNickname());
        sysRefund.setMobile(user.getMobile());
    }
}
