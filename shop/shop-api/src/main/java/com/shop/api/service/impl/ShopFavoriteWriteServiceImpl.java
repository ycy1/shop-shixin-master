package com.shop.api.service.impl;

import com.shop.api.service.ShopFavoriteWriteService;
import com.shop.entity.ShopConsumerFavorite;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopConsumerFavoriteMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.utils.LoginUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 用户收藏 写入实现（判据抄后台 ShopConsumerFavoriteServiceImpl#deleteByIds，只把归属那一道改成「只认登录 id」）
 *
 * 后台那一路收的是 id 数组（列表上勾多条做批量），而且只有消费者账号才逐条比 user_id —— 平台/门店档本来就能删全部。
 * 前台这一路一条一个 id，归属无条件比：不比的话谁拿别人的收藏行 id 打这个接口就能替别人删。
 * 归属比不上、行已经删了、根本没这个 id —— 三种都回同一句「收藏记录不存在」，不分档
 *（分档就等于让调用方拿 id 一句句试出这一条挂在谁名下，口径同订单详情、收货地址）。
 *
 * 两颗都在这一份里：个人中心列表每行那颗「删除」按收藏行的 id 点，详情页右侧那颗「收藏」按 (类型, 对象) 点
 *（后者是 2026-10-03 放开的，判据抄后台的 insert + assertTargetExists，差别同样是只认登录 id）。
 */
@Service
@RequiredArgsConstructor
public class ShopFavoriteWriteServiceImpl implements ShopFavoriteWriteService {

    /** target_type 的三档，和字典 favorite_target_type、和那张表 CASE 联表的三档是同一套 */
    private static final int TARGET_TYPE_PRODUCT = 1;
    private static final int TARGET_TYPE_STORE = 2;
    private static final int TARGET_TYPE_BRAND = 3;

    private final ShopConsumerFavoriteMapper shopConsumerFavoriteMapper;
    private final ShopProductMapper shopProductMapper;
    private final ShopStoreMapper shopStoreMapper;
    private final ShopBrandMapper shopBrandMapper;

    /**
     * 全局配置钉了 logic-delete-field=isDeleted（实体上没有 @TableLogic），deleteById 就是一条 UPDATE is_deleted=1；
     * selectById 也自动带 is_deleted=0，所以对同一条点第二次照样回「不存在」。
     * 这一趟只有一条写，所以不像收货地址的新增/修改那样挂 @Transactional（那两条要两句一起成才算数）。
     */
    @Override
    public void delete(Long id) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再删除收藏记录").getId();
        if (id == null) {
            throw new ServiceException("收藏记录ID不能为空");
        }
        ShopConsumerFavorite exists = shopConsumerFavoriteMapper.selectById(id);
        if (exists == null || !loginUserId.equals(exists.getUserId())) {
            throw new ServiceException("收藏记录不存在");
        }
        shopConsumerFavoriteMapper.deleteById(id);
    }

    /**
     * 一颗按钮管两个方向，所以三条路按「先试取消」的顺序走，全程只可能写一条：
     * 没删的那一条存在就把它删掉（回 false）；否则把已删的那一条复活（回 true）；
     * 这一对从来没记过才插第二条。
     *
     * 为什么非得分这三条：uk_user_target(user_id, target_type, target_id) 不含 is_deleted，
     * 删掉的行还占着这个键，直接 insert「取消后又收藏回同一个对象」必撞键 ——
     * 所以照浏览足迹 markBrowsed 的做法把那一行捞回来（后台「新增」那一路现在也走同一条复活）。
     * 顺序也不能倒过来：先复活就会把「本来收藏着」的那一条当成待复活，取消收藏点一次反而什么也没删掉。
     *
     * 三条路各只有一条写，挂 @Transactional 没有意义（同上面那一句 delete）。
     */
    @Override
    public boolean toggle(Integer targetType, Long targetId) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再操作收藏").getId();
        assertTargetExists(targetType, targetId);

        int removed = shopConsumerFavoriteMapper.deleteByTarget(loginUserId, targetType, targetId, loginUserId);
        if (removed > 0) {
            return false;
        }
        // 时间传 null ⇒ XML 里落 NOW()：前台这一颗就是"按点按钮这一刻收藏"，没有补录时间的入口
        int revived = shopConsumerFavoriteMapper.reviveByTarget(loginUserId, targetType, targetId, loginUserId, null);
        if (revived > 0) {
            return true;
        }
        ShopConsumerFavorite favorite = new ShopConsumerFavorite();
        // userId 只认登录 id：这是「往自己名下加」的一颗，参数里本来也没有这一格能让页面点名
        favorite.setUserId(loginUserId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(targetId);
        try {
            shopConsumerFavoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 只可能是同一颗按钮被连点两下，后那一下撞在刚插好的行上；库里已经是「收藏着」，回 true 就对了
        }
        return true;
    }

    /**
     * 按类型确认收藏对象在（三档各去自己那张表里查），判据和文案抄后台那一份 assertTargetExists。
     *
     * 表上没有外键，所以这一趟拦的是"指向不存在的东西"；至于对象后来改名、下架，
     * 收藏这一条不受影响，列表按联表结果实时显示。
     */
    private void assertTargetExists(Integer targetType, Long targetId) {
        if (targetType == null) {
            throw new ServiceException("收藏类型只能是 1商品、2门店 或 3品牌");
        }
        if (targetId == null) {
            throw new ServiceException("请选择收藏对象");
        }
        if (targetType == TARGET_TYPE_PRODUCT) {
            if (shopProductMapper.selectById(targetId) == null) {
                throw new ServiceException("所选商品不存在");
            }
            return;
        }
        if (targetType == TARGET_TYPE_STORE) {
            if (shopStoreMapper.selectById(targetId) == null) {
                throw new ServiceException("所选门店不存在");
            }
            return;
        }
        if (targetType == TARGET_TYPE_BRAND) {
            if (shopBrandMapper.selectById(targetId) == null) {
                throw new ServiceException("所选品牌不存在");
            }
            return;
        }
        throw new ServiceException("收藏类型只能是 1商品、2门店 或 3品牌");
    }
}
