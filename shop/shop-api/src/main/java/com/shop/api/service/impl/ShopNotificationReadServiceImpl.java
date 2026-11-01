package com.shop.api.service.impl;

import cn.hutool.http.HtmlUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.api.service.ShopNotificationReadService;
import com.shop.entity.SysNotifications;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysNotificationsMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.notifications.NotificationUnreadVo;
import com.shop.vo.notifications.NotificationsListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 我的消息 读实现（判据都在这份，三端只是各自决定什么时候来取）
 *
 * 五件事全部落在「当前登录用户」这一格上，参数里没有 userId：
 * /api/** 不过 Sa-Token 拦截器，所以由这里自己取登录态，取不到就报 ——
 * 消息是私密的（谁被回复、谁签收了一单），让它匿名可读等于把别人的动态摊在公网上。
 *
 * 分页那一趟带两条 EXISTS 子查询（已读、没被我删），加上未读汇总那条，读的量级是「我名下的消息条数」，
 * 不是全表；索引由 receiver 表上的 uk_message_user 和新加的 idx_receiver_list 兜（见 add_notification_columns.sql）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopNotificationReadServiceImpl implements ShopNotificationReadService {

    private final SysNotificationsMapper sysNotificationsMapper;

    /** 消息分类（字典 notification_category）：1 系统消息 —— 公告、活动开始、培训开始、签收这一类 */
    private static final int CATEGORY_SYSTEM = 1;

    /** 消息分类：2 互动消息 —— 评论回复、门店评价、点赞、获得徽章这一类 */
    private static final int CATEGORY_INTERACT = 2;

    @Override
    public IPage<NotificationsListVo> page(Integer category) {
        Long userId = LoginUserUtils.requireLoginUser("后再看消息").getId();
        checkCategory(category);
        Page<Object> page = PageUtil.getPage();
        SysNotifications query = new SysNotifications();
        query.setCategory(category);
        IPage<NotificationsListVo> result = sysNotificationsMapper.selectNotificationsPage(page, query, userId);
        // 公告正文是富文本编辑器那份 HTML，三端都按纯文本铺，所以在这里剥一次标签。
        // 只在读侧做、不在 NotificationsUtil 写入时做：那一格还要原样喂给邮件（EmailUtil 按 text/html 发、
        // 并从正文里抽 <img> 转成 CID 附件），写的时候剥等于把公告的图和排版从邮件里也删掉了。
        result.getRecords().forEach(vo -> vo.setMessage(plainText(vo.getMessage())));
        return result;
    }

    /** 富文本正文 → 纯文本；空值原样回（有的消息本来就没正文，只有标题） */
    private String plainText(String message) {
        return message == null || message.isEmpty() ? message : HtmlUtil.cleanHtmlTag(message);
    }

    @Override
    public NotificationUnreadVo unread() {
        Long userId = LoginUserUtils.requireLoginUser("后再看消息").getId();
        Map<String, Object> row = sysNotificationsMapper.countUnreadSummary(userId);
        NotificationUnreadVo vo = new NotificationUnreadVo();
        // count(1) 回来是 Long、SUM(CASE) 回来是 BigDecimal，所以两格都得按 Number 取，不能直接 (Long) cast
        vo.setTotal(num(row, "total"));
        vo.setSystemCount(num(row, "system_num"));
        vo.setInteractCount(num(row, "interact_num"));
        return vo;
    }

    @Override
    public void read(Long id) {
        Long userId = LoginUserUtils.requireLoginUser("后再看消息").getId();
        if (id == null) {
            throw new ServiceException("请指明要看哪一条消息");
        }
        // 那条 upsert 的 SELECT 只认 del_flag=0 的主消息：受影响 0 行就是这一条不存在（或已被后台删掉）
        if (sysNotificationsMapper.doRead(userId, id) <= 0) {
            throw new ServiceException("这条消息不存在或已被删除");
        }
    }

    @Override
    public void readAll(Integer category) {
        Long userId = LoginUserUtils.requireLoginUser("后再看消息").getId();
        checkCategory(category);
        // 一条 INSERT..SELECT 把这一档的未读全翻成已读：公网库上按人循环 UPDATE 要几十毫秒一条，未读多了就慢得明显
        sysNotificationsMapper.allRead(userId, category);
    }

    @Override
    public void delete(Long id) {
        Long userId = LoginUserUtils.requireLoginUser("后再看消息").getId();
        if (id == null) {
            throw new ServiceException("请指明要删哪一条消息");
        }
        // 谓词带了 pushVisible：不是我名下的（也不是全员广播的）删不到，免得凭一个 id 把别人的已读/删除行铺进去
        if (sysNotificationsMapper.deleteByUser(userId, id) <= 0) {
            throw new ServiceException("这条消息不存在或与你无关");
        }
    }

    @Override
    public void deleteAll(Integer category) {
        Long userId = LoginUserUtils.requireLoginUser("后再看消息").getId();
        checkCategory(category);
        // 一条 INSERT..SELECT 把名下可见的那一批全铺成已删（同 allRead 的理由：公网库上不能按 id 一条条来回）。
        // 和单条 delete 不同，这里不校验行数：本来一条不留也是清空成功，报「没有可清的消息」反而像是在怪人点错了。
        sysNotificationsMapper.allDelete(userId, category);
    }

    /** 分类只有两档，给个 3 上去等于查一条永远为假的谓词 —— 列表空、红点也空，看着像"没消息"其实是参数写错了 */
    private void checkCategory(Integer category) {
        if (category != null && category != CATEGORY_SYSTEM && category != CATEGORY_INTERACT) {
            throw new ServiceException("消息分类只有 1 系统消息 / 2 互动消息两档");
        }
    }

    private Long num(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
