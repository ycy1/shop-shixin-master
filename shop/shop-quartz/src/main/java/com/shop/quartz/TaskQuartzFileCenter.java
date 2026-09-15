package com.shop.quartz;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.entity.SysFileCenter;
import com.shop.mapper.SysFileCenterMapper;
import com.shop.wx.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component("fileCenter")
@RequiredArgsConstructor
public class TaskQuartzFileCenter {

    private final SysFileCenterMapper sysFileCenterMapper;

    /**
     * 清理过期文件：上传时间超过 expire_time 的文件移入回收站，并标记为「已过期」。
     * 仅处理数据库状态，存储文件保留，可在回收站手动「彻底删除」。
     */
    public void cleanExpired() {
        List<SysFileCenter> expiredList = sysFileCenterMapper.selectList(new LambdaQueryWrapper<SysFileCenter>()
                .isNotNull(SysFileCenter::getExpireTime)
                .lt(SysFileCenter::getExpireTime, LocalDateTime.now())
                .ne(SysFileCenter::getFileStatus, 4)
                .ne(SysFileCenter::getFileSource, 4));
        for (SysFileCenter fc : expiredList) {
            // 记录删除前的来源，便于还原
            if (fc.getOriginalSource() == null) {
                fc.setOriginalSource(fc.getFileSource());
            }
            fc.setFileSource(4);
            fc.setFileStatus(4);
            sysFileCenterMapper.updateById(fc);
        }
        log.info("文件中心定时清理：{} 个过期文件已移入回收站", expiredList.size());
    }
}
