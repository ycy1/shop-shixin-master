package com.shop.service.impl;

import org.springframework.stereotype.Service;
import com.shop.mapper.SysOperateLogMapper;
import com.shop.entity.SysOperateLog;
import com.shop.service.SysOperateLogService;
import com.shop.utils.PageUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;

/**
 *  服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysOperateLogServiceImpl extends ServiceImpl<SysOperateLogMapper, SysOperateLog> implements SysOperateLogService {

    /**
     * 查询分页列表
     */
    @Override
    public IPage<SysOperateLog> listSysOperateLog(SysOperateLog sysOperateLog) {
        LambdaQueryWrapper<SysOperateLog> wrapper = new LambdaQueryWrapper<>();
        return page(PageUtil.getPage(), wrapper);
    }
}
