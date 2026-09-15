package com.shop.service;

import com.shop.entity.SysOperateLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 *  服务接口
 */
public interface SysOperateLogService extends IService<SysOperateLog> {
    /**
     * 查询分页列表
     */
    IPage<SysOperateLog> listSysOperateLog(SysOperateLog sysOperateLog);
}