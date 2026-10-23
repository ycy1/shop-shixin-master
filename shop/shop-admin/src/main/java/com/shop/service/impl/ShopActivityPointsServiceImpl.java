package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.activity.ShopActivityPointsAdjustDto;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopActivityPointsService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分账户 服务实现（查询写法照商品库存 ShopErpInventoryServiceImpl，那颗「修改」见 adjust）
 *
 * 数据范围：分账号类型两档 ——
 * 平台/门店/ERP 账号**不收窄**（同活动、优惠券那几页的口径：积分是全平台一本账，
 * 按登录账号收窄会让门店/ERP 账号看不见大批用户的积分）；
 * 消费者账号只看自己那一行（「个人信息」那页的积分 tab 直接调这一条，不认前端传谁的 id）。
 * 这里的 user_id 是"积分是谁的"，不是 create_by 那个"谁建的账"（同购物车、用户券两张表）。
 */
@Service
@RequiredArgsConstructor
public class ShopActivityPointsServiceImpl extends ServiceImpl<ShopActivityPointsMapper, ShopActivityPoints>
        implements ShopActivityPointsService {

    /** 流水表：调整必须补一行凭证，否则这一页上那五个数就成了没人说得清的数 */
    private final ShopActivityPointsLogMapper shopActivityPointsLogMapper;

    /** 调整之前要确认这个人还在（加分会把没有账的人的第一行账建出来，不能建给一个不存在的人） */
    private final SysUserMapper sysUserMapper;

    /** 调整方向：1 增加（走送分那一条，累计获得和可用一起加） */
    private static final int TYPE_INCREASE = 1;

    /** 调整方向：2 减少（可用 → 累计消耗，判够不够） */
    private static final int TYPE_DECREASE = 2;

    /** 变动类型（字典 points_biz_type）：10 管理员调整 —— 这一档到这里才有写入方 */
    private static final int BIZ_TYPE_ADMIN = 10;

    @Override
    public IPage<ShopActivityPoints> selectPage(ShopActivityPoints query) {
        if (query == null) {
            query = new ShopActivityPoints();
        }
        // 消费者账号只看自己的那一行：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 昵称和手机号联 sys_user（sys_* 表没有 is_deleted 那一列，写法见 XML）
        return baseMapper.selectPointsPage(PageUtil.getPage(), query);
    }

    /**
     * 管理员调整积分：一次改账 + 一行流水，同一个事务
     *
     * 表单上只有"方向、多少个、为什么、这批分几时过期"这几格能填，那五个账面数字改不动：这一本是账，
     * 直接写那五个数就会出现"账上多了 1000 分、流水里什么都没有"，积分流水弹窗里
     * 「变动后余额」那一列从此接不上（口径同商品库存：只有成本价那几个参数能改，库存数改不了）。
     *
     * 加分复用送分那条 gainPoints（累计获得 + 可用一起加）；扣减走 deductBalancePoints
     *（可用 → 累计消耗，不是把累计获得减回去，理由写在那条语句的注释里）。
     * 两条都是带闸门的原子 UPDATE，0 行就是没改成，抛异常回滚，流水也不留。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int adjust(ShopActivityPointsAdjustDto form) {
        if (form == null || form.getUserId() == null) {
            throw new ServiceException("请指明调整的是哪个用户的积分");
        }
        boolean increase = Integer.valueOf(TYPE_INCREASE).equals(form.getType());
        if (!increase && !Integer.valueOf(TYPE_DECREASE).equals(form.getType())) {
            throw new ServiceException("调整方向只能是增加或减少");
        }
        int points = form.getPoints() == null ? 0 : form.getPoints();
        if (points <= 0) {
            throw new ServiceException("调整积分数量要大于 0（要加还是要减由方向说，这一格只给多少个）");
        }
        if (StringUtils.isBlank(form.getReason())) {
            throw new ServiceException("请填写调整原因，这一格会原样写进积分流水");
        }
        // 改账和写流水必须一起落，事务没开就别改：账动了凭证没留，这一笔就成了说不清的数
        TransactionUtils.requireActive("调整积分");

        Long userId = form.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在，积分没处放");
        }
        ShopActivityPoints account = selectByUserId(userId);
        if (increase) {
            gain(userId, points, account);
        } else {
            if (account == null) {
                throw new ServiceException("这个用户还没有积分账户，没有分可扣");
            }
            if (baseMapper.deductBalancePoints(userId, points) == 0) {
                // 这一句读的是刚查出来的那一版，只为了把话说清楚；真正的闸门是上面那条 UPDATE 的 WHERE
                throw new ServiceException("可用积分不够：这个用户现在有 " + zeroIfNull(account.getBalance())
                        + " 分可扣，本次要扣 " + points + " 分（冻结中的那一部分扣不到）");
            }
        }

        // 变动后余额读回来而不是自己加减：同一事务里刚改过的那一版才是账户上的真数，
        // 流水里那一列要能和下一笔接得上（写法同支付回调送分那一路）
        ShopActivityPoints after = selectByUserId(userId);
        int balance = zeroIfNull(after == null ? null : after.getBalance());
        ShopActivityPointsLog record = new ShopActivityPointsLog();
        record.setUserId(userId);
        record.setPoints(increase ? points : -points);
        record.setBalance(balance);
        record.setBizType(BIZ_TYPE_ADMIN);
        // biz_id / biz_no 留 NULL：这一笔不挂在任何单据上，谁动的记在 create_by、为什么动记在说明里
        record.setDescription("管理员" + (increase ? "增加 " : "扣减 ") + points + " 分：" + form.getReason().trim());
        // 过期时间只有增加这一头才有：流水表那一格本来就是"获取时记、过期时用"，扣掉的那一笔不过期。
        // 后台不自己按 expire_day 推默认值（那份配置住在 shop-api 的 ShopPointsSupport 里，两处各读一遍迟早对不上），
        // 这一格由人填，留空就是这一笔不设过期
        record.setExpireTime(increase ? form.getExpireTime() : null);
        shopActivityPointsLogMapper.insert(record);
        return balance;
    }

    /**
     * 加分：有账就走增量，没账就把第一行账建出来（这张表的行由第一次变动带出来，后台不单独建账）
     *
     * 增量的 UPDATE 对不存在的行是 0 行改动，不报错，所以要先判有没有行；
     * 建账时撞 uk_user（同一刻两个动作第一次给这个人加分）不报错，改走增量。
     * 只有这一颗按钮的加分走这一段 —— 签到那一路的给分住在 shop-api（那张表的两个写入方都在那边），
     * 两边不共用是因为模块依赖是单向的（shop-admin 依赖 shop-api，反过来摸不到）。
     */
    private void gain(Long userId, int points, ShopActivityPoints account) {
        if (account != null) {
            baseMapper.gainPoints(userId, points);
            return;
        }
        ShopActivityPoints created = new ShopActivityPoints();
        created.setUserId(userId);
        created.setTotalPoints(points);
        created.setUsedPoints(0);
        created.setBalance(points);
        created.setFrozenPoints(0);
        // expire_points 给 0：那一格是"即将过期积分"，要等过期那一条链才算
        created.setExpirePoints(0);
        try {
            baseMapper.insert(created);
        } catch (DuplicateKeyException e) {
            baseMapper.gainPoints(userId, points);
        }
    }

    /** 读这一个人的账户行（没账就是 null，加分那一路靠它分清要不要建第一行账）；
     *  一个用户一行，LIMIT 1 只是防脏数据炸成一页 */
    private ShopActivityPoints selectByUserId(Long userId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                .eq(ShopActivityPoints::getUserId, userId)
                .last("LIMIT 1"));
    }

    /** 空格子当 0（这几列库里有默认值，真读到 null 属于脏数据，别让它把流水写成 null） */
    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
