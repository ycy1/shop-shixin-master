package com.shop.service.impl;

import java.util.List;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.Constants;
import com.shop.enums.NoticePosttionEnum;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
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

    /**
     * 公告落库成功后丢一条事件出去：新增、修改、上下线三处都走这一句，落主表和展开接收人都在 NotificationsUtil 那段异步里做。
     *
     * 为什么不再自己 submit 到线程池：原先是一个 service 实例 new 一个 FixedThreadPool（容器关掉还漏线程），
     * 而且那一段跑在事务提交之前 —— 公告回滚了，消息却已经发出去了。
     */
    private void publishNotice(SysNotice notice, Long fromUserId) {
        if (notice == null || notice.getId() == null) {
            return;
        }
        // 收件范围还是公告表单里那串 notice_push（user/dept/role 三档），三档全空 = 全员广播
        List<List<Long>> push = NotificationsUtil.parseNoticePush(notice.getNoticePush());
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.NOTICE)
                .businessId(notice.getId())
                .title("公告通知【" + notice.getTitle() + "】")
                .message(notice.getContent())
                .fromUserId(fromUserId)
                .userIds(push.get(0))
                .deptIds(push.get(1))
                .roleIds(push.get(2))
                .build());
    }

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
            publishNotice(sysNotice, StpUtil.getLoginIdAsLong());
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
            publishNotice(sysNotice, StpUtil.getLoginIdAsLong());
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
            // 上下线这一趟用的是库里的正文（弹窗只递了 isShow 那一格）
            publishNotice(one, StpUtil.getLoginIdAsLong());
        }
        // 上面已经发过一遍了：转手给 update 只做「是否展示」那格的保存，
        // 别让它带着表单里的 send=true 再发第二遍（老代码这么双发过一次）
        sysNotice.setSend(false);
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
