package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityBadgeUserMapper;
import com.shop.service.ShopActivityBadgeUserService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户徽章 服务实现（查询写法照用户优惠券 ShopActivityCouponUserServiceImpl，
 * 删除、改状态、改展示那三条是本模块自己的：那张表连删除都没开，这一张开了三条往回收的小口子）
 *
 * 数据范围分账号类型两档 —— 平台/门店/ERP 账号**不收窄**（同活动明细、库存流水、用户券那几页的口径：
 * 这一页是平台侧看一枚徽章发到了谁手上，一收窄就会出现"别人发出去的那些在这份明细里凭空消失"）；
 * 消费者账号只看自己名下那几枚。
 *
 * 消费者那一档走的是后台「个人信息」页的「徽章」那一格（前台「我的徽章」不读这一条，
 * 它走 shop-api 的 /api/web/shop/profile/badge/list，那边不管什么账号都钉登录 id）。
 * 压上这一档是为了和积分流水、用户券那两张明细表同一套口径 ——
 * 一张记录表能读到全平台谁拿了什么徽章，是不该开给消费者的口子。
 * 两条写口子也跟着这一档走：那一行 id 是前端给的，不验就等于把"动别人名下徽章"开成了口子。
 */
@Service
public class ShopActivityBadgeUserServiceImpl
        extends ServiceImpl<ShopActivityBadgeUserMapper, ShopActivityBadgeUser>
        implements ShopActivityBadgeUserService {

    @Override
    public IPage<ShopActivityBadgeUser> selectRecordPage(ShopActivityBadgeUser query) {
        if (query == null) {
            query = new ShopActivityBadgeUser();
        }
        // 消费者账号只看自己名下的徽章：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 昵称和手机号联 sys_user、is_deleted = 0 都写在 XML（自定义 SQL 不走全局逻辑删除）；
        // 名称那三格读本表自己的快照，所以这一条不联定义表取名
        return baseMapper.selectRecordPage(PageUtil.getPage(), query);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        List<Long> recordIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (recordIds.isEmpty()) {
            return;
        }
        if (LoginUserUtils.isConsumer()) {
            Long loginUserId = StpUtil.getLoginIdAsLong();
            // 只数"这些 id 里有几行挂在这个登录用户名下"，够数才让删；selectCount 走全局逻辑删除，
            // 所以软删的旧行算不在他名下 —— 那一档本来就由下面那条物理删除一起清掉，不该由消费者账号碰
            long mine = baseMapper.selectCount(new LambdaQueryWrapper<ShopActivityBadgeUser>()
                    .in(ShopActivityBadgeUser::getId, recordIds)
                    .eq(ShopActivityBadgeUser::getUserId, loginUserId));
            if (mine != recordIds.size()) {
                throw new ServiceException("只能删除自己名下的徽章记录");
            }
        }
        // 物理删除（不是 removeByIds 那条 is_deleted = 1）：判据见 Mapper 接口和 XML 上那两段 ——
        // uk_badge_user 不含 is_deleted，留一行软删记录就等于把这个人这一枚永久封住。
        // 积分和券在这儿一笔都不动：这一颗按钮删的是"这枚徽章在他手上"这一条记录，不是当初那笔兑现
        baseMapper.deletePhysicallyByIds(recordIds);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new ServiceException("改成有效还是失效，这一格不能空");
        }
        if (status != ShopActivityBadgeUser.STATUS_VALID && status != ShopActivityBadgeUser.STATUS_INVALID) {
            throw new ServiceException("状态只有 1 有效、0 失效两档 —— 过期不落这一格，前端拿有效期现比");
        }
        requireMine(id);
        // 稀疏实体那一条口径同 updateIsShow：这一张表上没有 IGNORED 列，半截实体不会把别的列刷成 NULL
        ShopActivityBadgeUser update = new ShopActivityBadgeUser();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
    }

    @Override
    public void updateIsShow(Long id, Integer isShow) {
        if (isShow == null) {
            throw new ServiceException("改成展示还是隐藏，这一格不能空");
        }
        if (isShow != ShopActivityBadgeUser.SHOW_YES && isShow != ShopActivityBadgeUser.SHOW_NO) {
            throw new ServiceException("是否展示只有 1 展示、0 隐藏两档");
        }
        requireMine(id);
        // 稀疏实体只带 id 和这一格：MP 默认 NOT_NULL 策略，SET 里只有 is_show 加留痕那两格，
        // 快照三格、有效期、状态不传就不动（这一张表上没有 IGNORED 列，半截实体不会把别的列刷成 NULL）
        ShopActivityBadgeUser update = new ShopActivityBadgeUser();
        update.setId(id);
        update.setIsShow(isShow);
        updateById(update);
    }

    /**
     * 改状态、改展示那两条共用那三道闸：id 得给、这一行读得到、消费者只能动自己名下那一行。
     *
     * 走全局逻辑删除，所以软删行读不到 —— 那一行本来就等着被物理删除清掉，不该在这儿改它。
     * 归属这一道不能省：那一行 id 是前端给的，不验就等于把"动别人名下那枚徽章"开成口子；
     * 平台/门店/ERP 账号不收窄，和读接口那一条同一个口径。
     */
    private void requireMine(Long id) {
        if (id == null) {
            throw new ServiceException("改哪一条获取记录，得给这一行的 id");
        }
        ShopActivityBadgeUser record = getById(id);
        if (record == null) {
            throw new ServiceException("获取记录不存在");
        }
        if (LoginUserUtils.isConsumer() && !Objects.equals(record.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new ServiceException("只能修改自己名下的徽章记录");
        }
    }
}
