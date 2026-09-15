package com.shop.service.impl;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.Constants;
import com.shop.entity.SysNotifications;
import com.shop.enums.NoticePosttionEnum;
import com.shop.exception.ServiceException;
import com.shop.utils.EmailUtil;
import com.shop.utils.NotificationsUtil;
import org.springframework.stereotype.Service;
import com.shop.mapper.SysNoticeMapper;
import com.shop.entity.SysNotice;
import com.shop.service.SysNoticeService;
import com.shop.utils.PageUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;

/**
 * 公告 服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    private final NotificationsUtil notificationsUtil;

    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(10);
    /**
     * 查询公告分页列表
     */
    @Override
    public IPage<SysNotice> selectPage(SysNotice sysNotice) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        // 构建查询条件
        wrapper.like(sysNotice.getContent() != null, SysNotice::getContent, sysNotice.getContent());
        wrapper.eq(sysNotice.getIsShow() != null, SysNotice::getIsShow, sysNotice.getIsShow());
        wrapper.eq(sysNotice.getPosition() != null, SysNotice::getPosition, sysNotice.getPosition());
        return page(PageUtil.getPage(), wrapper);
    }

    /**
     * 查询公告列表
     */
    @Override
    public List<SysNotice> selectList(SysNotice sysNotice) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        // 构建查询条件
        wrapper.eq(sysNotice.getId() != null, SysNotice::getId, sysNotice.getId());
        wrapper.eq(sysNotice.getContent() != null, SysNotice::getContent, sysNotice.getContent());
        wrapper.eq(sysNotice.getIsShow() != null, SysNotice::getIsShow, sysNotice.getIsShow());
        wrapper.eq(sysNotice.getPosition() != null, SysNotice::getPosition, sysNotice.getPosition());
        wrapper.eq(sysNotice.getCreateTime() != null, SysNotice::getCreateTime, sysNotice.getCreateTime());
        return list(wrapper);
    }

    /**
     * 新增公告
     */
    @Override
    public boolean insert(SysNotice sysNotice) {
        if (sysNotice.getIsShow() == Constants.YES && sysNotice.getPosition().equals(NoticePosttionEnum.TOP.getCode())) {
            SysNotice one = baseMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
                    .eq(SysNotice::getPosition, sysNotice.getPosition())
                    .eq(SysNotice::getIsShow,sysNotice.getIsShow()));
            if(one != null) {
                throw new ServiceException("显示的顶部公告只能有一个!");
            }
        }

        boolean result = save(sysNotice);
        // 是否发送：新增弹窗点击“发送”（send=true），或添加即展示（isShow=1，原行为）→ 立即推送
        boolean shouldPush = Boolean.TRUE.equals(sysNotice.getSend())
                || sysNotice.getIsShow() == Constants.YES;
        if (shouldPush) {
            long loginIdAsLong = StpUtil.getLoginIdAsLong();
            taskExecutor.submit(() -> {
                try {
                    SysNotifications notifications = SysNotifications.builder()
                            .title("公告通知【"+ sysNotice.getTitle()+"】")
                            .message(sysNotice.getContent())
                            .businessId(sysNotice.getId())
                            .businessType("notice")
                            // 推送对象由公告的 notice_push 指定（null 或全空 = 全员可见）
                            .noticePush(sysNotice.getNoticePush())
                            .fromUserId(loginIdAsLong)
                            .build();
                    notificationsUtil.publish(notifications);
                } catch (Exception e) {
                    log.error("发送通知失败", e);
                }
            });
        }
        return result;
    }

    /**
     * 修改公告
     */
    @Override
    public boolean update(SysNotice sysNotice) {

        if (sysNotice.getIsShow() == Constants.YES && sysNotice.getPosition().equals(NoticePosttionEnum.TOP.getCode())) {
            SysNotice one = baseMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
                    .eq(SysNotice::getPosition, sysNotice.getPosition())
                    .eq(SysNotice::getIsShow,sysNotice.getIsShow()));
            if(one != null && !one.getId().equals(sysNotice.getId())) {
                throw new ServiceException("显示的顶部公告只能有一个!");
            }
        }
        boolean result = updateById(sysNotice);
        // 修改弹窗点击“发送”（send=true）：保存并立即推送
        if (Boolean.TRUE.equals(sysNotice.getSend())) {
            long loginIdAsLong = StpUtil.getLoginIdAsLong();
            taskExecutor.submit(() -> {
                try {
                    SysNotifications notifications = SysNotifications.builder()
                            .title("公告通知【" + sysNotice.getTitle() + "】")
                            .message(sysNotice.getContent())
                            .businessId(sysNotice.getId())
                            .businessType("notice")
                            // 推送对象由公告的 notice_push 指定（null 或全空 = 全员可见）
                            .noticePush(sysNotice.getNoticePush())
                            .fromUserId(loginIdAsLong)
                            .build();
                    notificationsUtil.publish(notifications);
                } catch (Exception e) {
                    log.error("发送通知失败", e);
                }
            });
        }
        return result;
    }

    @Override
    public boolean show(SysNotice sysNotice) {
        SysNotice one = baseMapper.selectById(sysNotice.getId());
        if(one == null) {
            throw new ServiceException("公告不存在!");
        }
        if (sysNotice.getIsShow() == Constants.YES) {
            long loginIdAsLong = StpUtil.getLoginIdAsLong();
            taskExecutor.submit(() -> {
                try {
                    SysNotifications notifications = SysNotifications.builder()
                            .title("公告通知【"+ one.getTitle()+"】")
                            .message(one.getContent())
                            .businessId(one.getId())
                            .businessType("notice")
                            // 推送对象由公告的 notice_push 指定（null 或全空 = 全员可见）
                            .noticePush(one.getNoticePush())
                            .fromUserId(loginIdAsLong)
                            .build();
                    notificationsUtil.publish(notifications);
                } catch (Exception e) {
                    log.error("发送通知失败", e);
                }
            });
        }
        return update(sysNotice);
    }

    /**
     * 批量删除公告
     */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
