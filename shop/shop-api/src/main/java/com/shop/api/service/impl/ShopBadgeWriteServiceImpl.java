package com.shop.api.service.impl;

import com.shop.api.service.ShopBadgeWriteService;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityBadgeUserMapper;
import com.shop.utils.LoginUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户徽章 展示那一格的写入实现（判据抄后台 ShopActivityBadgeUserServiceImpl#updateIsShow，
 * 差别是把「消费者账号才逐条比 user_id」改成无条件只认登录 id）
 *
 * 归属比不上、行已经被物理删掉、根本没这个 id —— 三种都回同一句「徽章不存在」，不分档
 *（分档就等于让调用方拿 id 一句句试出这一枚挂在谁名下，口径同收藏、收货地址）。
 */
@Service
@RequiredArgsConstructor
public class ShopBadgeWriteServiceImpl implements ShopBadgeWriteService {

    private final ShopActivityBadgeUserMapper shopActivityBadgeUserMapper;

    /**
     * 两句：先按 id 读一行验归属，再只交 id + is_show 那一格回去。
     *
     * 全局配置钉了 logic-delete-field=isDeleted，所以 selectById 自带 is_deleted = 0、
     * updateById 也带 —— 软删那一行读不到，正好回同一句「徽章不存在」。
     * 半截实体不刷别的列：这张表没有钉 FieldStrategy.IGNORED 的列，全局 not_empty 策略下
     * SET 里只有 is_show 加留痕那两格，快照三格、有效期、状态传了也不认（本方法只取两格）。
     *
     * 这一趟只改一条，所以不像发放那一路一样挂 @Transactional（那一条要在同一事务里落一枚徽章、
     * 加一笔积分、发几张券，少一样整批都不算数）。
     */
    @Override
    public void setShow(Long id, Integer isShow) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后修改徽章展示").getId();
        if (id == null || isShow == null) {
            throw new ServiceException("改哪一枚徽章、改成展示还是隐藏，两样都得给");
        }
        if (isShow != ShopActivityBadgeUser.SHOW_YES && isShow != ShopActivityBadgeUser.SHOW_NO) {
            throw new ServiceException("是否展示只有 1 展示、0 隐藏两档");
        }
        ShopActivityBadgeUser record = shopActivityBadgeUserMapper.selectById(id);
        if (record == null || !loginUserId.equals(record.getUserId())) {
            throw new ServiceException("徽章不存在");
        }
        ShopActivityBadgeUser update = new ShopActivityBadgeUser();
        update.setId(id);
        update.setIsShow(isShow);
        if (shopActivityBadgeUserMapper.updateById(update) != 1) {
            throw new ServiceException("修改失败");
        }
    }
}
