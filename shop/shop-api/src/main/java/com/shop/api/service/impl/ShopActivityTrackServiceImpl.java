package com.shop.api.service.impl;

import com.shop.api.service.ShopActivityTrackService;
import com.shop.dto.activity.ShopActivityTrackDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopActivity;
import com.shop.entity.ShopActivityClick;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityMapper;
import com.shop.mapper.ShopActivityClickMapper;
import com.shop.utils.IpUtil;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.UserAgentUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * 活动埋点 服务实现（一次曝光 / 一次点击 = shop_activity_click 里的一行）
 *
 * 鉴权同下单、报名那几路：/api/** 不过 Sa-Token 拦截器，所以谁触发的这里自己取登录态。
 * 和它们不同的是这一条**不要求登录**：游客刷到活动位是常态，那张表 user_id 那一列的注释写的就是
 * "未登录为 NULL"。MyMetaObjectHandler 那份留痕列（create_by）在没有登录态时填不进去，留 NULL，
 * 这也是它和 user_id 语义不同的地方：一个是"点活动的人"，一个是"这一行是谁的操作产生的"。
 *
 * 只挡两道：活动不在（selectById 带逻辑删除，删掉的行查不出来）和事件类型不在 1/2 这两档。
 * 状态那一格（未开始/投放中/已结束/已取消）不参与判断 —— 它是后台手动推的，
 * 而曝光/点击是当场发生的事实；把事实丢掉换来的不是干净，是"改了状态之后统计凭空少一截且无从解释"。
 * 判据同门店培训签到只挡「已取消」那一档，比它更松一格，因为埋点没有"占了别人的名额"这类副作用。
 *
 * 统计不回写：活动表那三格（曝光量/点击量/点击率）读的时候按这张表现算，
 * 所以这里插完行就结束，不去碰 shop_activity（口径同门店培训的 sign_count）。
 *
 * 前端只传 activityId + eventType 两格，剩下全是服务端现取：谁触发的取登录态、ip 取请求头、
 * ip 归属地按那份内存里的 xdb 解析、系统从 User-Agent 判 —— 和登录时往 sys_user 上盖那三笔同一个写法。
 * 这一条路不鉴权，参数里能写的字段等于谁都能填，所以一律不信前端。
 *
 * 不做防刷：同一人一秒内报十次曝光就落十行。要收得加按 (activity_id, user_id, 时间窗) 的去重，
 * 那是"曝光量"这个指标本身的口径问题，等有真实需求再说，先记在这儿。
 */
@Service
@RequiredArgsConstructor
public class ShopActivityTrackServiceImpl implements ShopActivityTrackService {

    private final ShopActivityMapper shopActivityMapper;

    private final ShopActivityClickMapper shopActivityClickMapper;

    /** 事件类型（字典 activity_event_type）：1 曝光 —— 活动被展示出来一次记一行 */
    private static final int EVENT_VIEW = 1;

    /** 事件类型（字典 activity_event_type）：2 点击 —— 这一档也是那张表的库里默认值 */
    private static final int EVENT_CLICK = 2;

    /** 收的事件类型就这两档，传别的进来直接拒（别把脏值写进流水，统计是按它分组数的） */
    private static final List<Integer> EVENT_TYPES = Arrays.asList(EVENT_VIEW, EVENT_CLICK);

    @Override
    public void track(ShopActivityTrackDto form) {
        if (form == null || form.getActivityId() == null) {
            throw new ServiceException("请指明是哪条活动的埋点");
        }
        Integer eventType = form.getEventType();
        if (eventType == null || !EVENT_TYPES.contains(eventType)) {
            throw new ServiceException("事件类型只能是 " + EVENT_VIEW + " 曝光或 " + EVENT_CLICK + " 点击");
        }
        ShopActivity activity = shopActivityMapper.selectById(form.getActivityId());
        if (activity == null) {
            throw new ServiceException("活动不存在");
        }

        LoginUserInfo login = LoginUserUtils.getLoginUser();
        ShopActivityClick record = new ShopActivityClick();
        record.setActivityId(activity.getId());
        record.setEventType(eventType);
        // 未登录就留 NULL：这一格是"谁点的"，不是"这次操作由哪个账号产生"（后者是 create_by 那格留痕）
        record.setUserId(login == null || login.getId() == null ? null : login.getId().longValue());
        // ip / ip来源 / 系统 这三格全由服务端现取，写法照登录时往 sys_user 上盖那三笔
        // （MySaTokenListener.doLogin）：埋点这条路不鉴权，参数里能写的东西谁都能填，所以一律不信前端
        String ip = IpUtil.getIp();
        record.setIp(ip);
        // getIp2region 读的是启动时加载进内存的那份 xdb，不发网络请求（getCityInfo/analyzeIp 那两个要外呼，别换）
        record.setIpLocation(IpUtil.getIp2region(ip));
        HttpServletRequest request = IpUtil.getRequest();
        record.setOs(UserAgentUtil.getOs(request == null ? null : request.getHeader("User-Agent")));
        shopActivityClickMapper.insert(record);
    }
}
