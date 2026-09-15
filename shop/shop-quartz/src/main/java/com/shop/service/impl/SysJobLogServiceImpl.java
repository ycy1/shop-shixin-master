package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.JobLogQuery;
import com.shop.entity.SysJobLog;
import com.shop.mapper.SysJobLogMapper;
import com.shop.service.SysJobLogService;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysJobLogServiceImpl extends ServiceImpl<SysJobLogMapper, SysJobLog> implements SysJobLogService {

    @Override
    public Page<SysJobLog> selectJobLogPage(JobLogQuery query) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .like(StringUtils.isNotBlank(query.getJobName()), SysJobLog::getJobName, query.getJobName())
                .eq(StringUtils.isNotBlank(query.getJobGroup()), SysJobLog::getJobGroup, query.getJobGroup())
                .eq(StringUtils.isNotBlank(query.getStatus()), SysJobLog::getStatus, query.getStatus())
                .orderByDesc(SysJobLog::getCreateTime);

        return page(PageUtil.getPage(), wrapper);
    }

    @Override
    public void addJobLog(SysJobLog jobLog) {
        baseMapper.insert(jobLog);
    }

    @Override
    public void cleanJobLog() {
        baseMapper.cleanJobLog();
    }
}
