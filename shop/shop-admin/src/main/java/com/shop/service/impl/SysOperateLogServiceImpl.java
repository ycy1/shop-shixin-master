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
        // 原先一条 orderBy 都没有：MySQL 就照主键顺序回，于是第一屏是最早那批（9 月的），要翻到末页才看到刚发生的。
        // 后面跟 id desc 是为了同一秒里连着记的多条（批量删菜单那种）在翻页时不会重复或漏掉。
        wrapper.orderByDesc(SysOperateLog::getCreateTime).orderByDesc(SysOperateLog::getId);
        return page(PageUtil.getPage(), wrapper);
    }
}
