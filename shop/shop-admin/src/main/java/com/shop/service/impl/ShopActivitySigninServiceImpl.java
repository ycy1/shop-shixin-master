package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivitySignin;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivitySigninMapper;
import com.shop.service.ShopActivitySigninService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.utils.SigninUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户签到记录 服务实现（查询写法照积分账户 ShopActivityPointsServiceImpl）
 *
 * 数据范围：分账号类型两档（同积分账户那页）——
 * 平台/门店/ERP 账号**不收窄**（签到是全平台一本账，按登录账号收窄会让门店/ERP 账号看不见大批用户的签到）；
 * 消费者账号只看自己的那几行（「个人信息」那页的「签到记录」日历直接调这两条，不认前端传谁的 id）。
 * 这里的 user_id 是"这一行是谁的签到"，不是 create_by 那个"谁补的签"。
 *
 * 这一份只读：写的那一头（落行 + 给积分 + 记流水）在 shop-api 的 ShopSigninService 里，
 * 前台正常签到（sign_type=1）和后台补签（sign_type=2）共用那一份，所以日历上两种行都会出现。
 * 「连续签到天数」这一格也由这一份现算（那张表上没有这一列，算式在 SigninUtils）——
 * shop-api 那一份写的时候要数一次给流水说明用，两边读的是同一个工具，不是两套口径。
 */
@Service
@RequiredArgsConstructor
public class ShopActivitySigninServiceImpl extends ServiceImpl<ShopActivitySigninMapper, ShopActivitySignin>
        implements ShopActivitySigninService {

    /** 日历弹窗里月份那一格的格式（和前端 el-calendar 传过来的是同一个串） */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public IPage<ShopActivitySignin> selectPage(ShopActivitySignin query) {
        if (query == null) {
            query = new ShopActivitySignin();
        }
        // 消费者账号只看自己的那几行：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 昵称和手机号联 sys_user（sys_* 表没有 is_deleted 那一列，写法见 XML），几个天数/积分数是 GROUP BY 现算的
        IPage<ShopActivitySignin> page = baseMapper.selectSigninPage(PageUtil.getPage(), query);
        // 连续天数没存列（这一列已删），所以那一格由本服务逐行现算：一页就 10 行，
        // 每行一次单表索引查询（user_id 走 uk_user_date 的左前缀、倒序取 7 条），比在 GROUP BY 里再套一层窗口函数好读
        for (ShopActivitySignin row : page.getRecords()) {
            if (row.getUserId() != null && row.getLastSignDate() != null) {
                row.setCurrentContinuous(continuousStreak(row.getUserId(), row.getLastSignDate()));
            }
        }
        return page;
    }

    /**
     * 某个人某个月的签到记录：只回本月签过的那几天，没签的日子由前端画成空格子
     *（月份留空按当月；一个月最多 31 行，所以不分页）
     *
     * 返回的每一行上盖的是同一个数 —— "这个人到最近那一天为止连着签了几天"。
     * 连没连是这一本账整体的事，跟"这一个月里第几天"不相干（翻到上个月那个数不该变），
     * 所以前端把这个数说一次（日历上面那一排汇总），不在每格里重复说。
     */
    @Override
    public List<ShopActivitySignin> selectMonthRecords(Long userId, String month) {
        // 同一个口径：消费者账号点名要谁的记录都不算，只看自己的（个人信息那页的日历走这一条）
        Long targetUserId = LoginUserUtils.isConsumer() ? StpUtil.getLoginIdAsLong() : userId;
        if (targetUserId == null) {
            throw new ServiceException("请指明看哪个用户的签到记录");
        }
        String currentMonth = month == null || month.isBlank()
                ? LocalDate.now().format(MONTH_FORMAT) : month.trim();
        List<ShopActivitySignin> records = baseMapper.selectMonthRecords(targetUserId, currentMonth);
        // 起点是今天：这个月和它之前的日子都在回看范围内，所以这一趟只问一次服务端
        int continuous = continuousStreak(targetUserId, LocalDate.now());
        records.forEach(item -> item.setCurrentContinuous(continuous));
        return records;
    }

    /** 连着第几天：把"最晚那天往前逐日回看、最多 7 天、遇断即停"这一套算式交给 SigninUtils（两处读的是同一份） */
    private int continuousStreak(Long userId, LocalDate upto) {
        return SigninUtils.continuousStreak(baseMapper.selectRecentSignDates(userId, upto));
    }
}
