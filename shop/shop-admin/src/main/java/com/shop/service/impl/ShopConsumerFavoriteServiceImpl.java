package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.dto.favorite.ShopFavoriteSaveDto;
import com.shop.entity.ShopBrand;
import com.shop.entity.ShopConsumerFavorite;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopStore;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopConsumerFavoriteMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopConsumerFavoriteService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.favorite.ShopFavoriteVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户收藏 服务实现（分页走自定义 XML 联表，写法照购物车 ShopConsumerCartServiceImpl）
 *
 * 数据范围同购物车、收货地址：只有消费者账号（account_type=3）按 user_id = 登录 id 收窄，
 * 平台/门店/送货员/供应商看全部。本表的 user_id 是"这条收藏属于哪个消费者"，不是创建人
 * （创建人留痕在 BaseEntity 的 create_by），所以别照抄 shop_* 的 storeIds 那套。
 *
 * 新增是"先复活、复活不到才插行"（口径同浏览足迹的 markBrowsed）：uk_user_target 不含 is_deleted，
 * 被删掉的那一条还占着这个键，直接 INSERT 必撞键。收藏时间可在表单里补录，复活时就刷这一格。
 */
@Service
@RequiredArgsConstructor
public class ShopConsumerFavoriteServiceImpl extends ServiceImpl<ShopConsumerFavoriteMapper, ShopConsumerFavorite>
        implements ShopConsumerFavoriteService {

    /** 收藏类型的取值，和字典 favorite_target_type、表注释里的口径保持一致 */
    private static final int TARGET_TYPE_PRODUCT = 1;

    private static final int TARGET_TYPE_STORE = 2;

    private static final int TARGET_TYPE_BRAND = 3;

    private final ShopProductMapper shopProductMapper;

    private final ShopStoreMapper shopStoreMapper;

    private final ShopBrandMapper shopBrandMapper;

    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopFavoriteVo> selectPage(ShopFavoriteQueryDto query) {
        if (query == null) {
            query = new ShopFavoriteQueryDto();
        }
        // 消费者账号只看自己的收藏：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 对象名称分散在商品/门店/品牌三张表上、关键字要跨这三张表搜，用 wrapper 表达不了联表，走 XML；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectFavoritePage(PageUtil.getPage(), query);
    }

    @Override
    public ShopFavoriteVo selectFavoriteById(Long id) {
        ShopFavoriteVo favorite = baseMapper.selectFavoriteById(id);
        if (favorite == null) {
            throw new ServiceException("收藏记录不存在");
        }
        assertOwn(favorite);
        return favorite;
    }

    @Override
    public void insert(ShopFavoriteSaveDto form) {
        ShopConsumerFavorite favorite = toEntity(form);
        // 新增不接受调用方指定主键：id 是 AUTO，但传进来 MP 会照写
        favorite.setId(null);
        // 消费者账号只能往自己的收藏夹里加，userId 同样覆盖，不让传别人 id 进来
        if (LoginUserUtils.isConsumer()) {
            favorite.setUserId(StpUtil.getLoginIdAsLong());
        }
        validate(favorite, null);
        // 这一对 (消费者, 类型, 对象) 只要记过（哪怕已被删除、还占着 uk_user_target）就把那一行复活，
        // 顺带把收藏时间刷成页面上补录的这一格（不填则 XML 里落 NOW()）；前台那颗按钮走的是同一条路
        Long loginUserId = StpUtil.getLoginIdAsLong();
        int revived = baseMapper.reviveByTarget(favorite.getUserId(), favorite.getTargetType(),
                favorite.getTargetId(), loginUserId, favorite.getFavoriteTime());
        if (revived > 0) {
            return;
        }
        // favoriteTime 为 null 时不用补：列钉 NOT NULL DEFAULT CURRENT_TIMESTAMP，MP 会整列跳过
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            // 只可能是两个请求同时给同一 (消费者, 类型, 对象) 建第一条，后到的那个撞在刚插好的行上；
            // 补一次复活，最终结果和"收藏上了"完全一致，不必回头报错
            baseMapper.reviveByTarget(favorite.getUserId(), favorite.getTargetType(),
                    favorite.getTargetId(), loginUserId, favorite.getFavoriteTime());
        }
    }

    @Override
    public void update(ShopFavoriteSaveDto form) {
        if (form.getId() == null) {
            throw new ServiceException("收藏记录ID不能为空");
        }
        ShopConsumerFavorite exists = getById(form.getId());
        if (exists == null) {
            throw new ServiceException("收藏记录不存在");
        }
        assertOwn(exists);
        ShopConsumerFavorite favorite = toEntity(form);
        // 归属消费者/类型/对象允许不传（局部更新），不传沿用原值：这三列都是 NOT NULL。
        // 但换类型时必须连对象一起换，只传 targetType 不传 targetId 会拿旧类型的 id 去新表里查，
        // 那种情况由 validate 报"所选XX不存在"，不做静默兜底。
        // 收藏时间同理可不传（全局 field-strategy=not_empty，null 的列整个跳过），不传就还是原来那一格
        if (favorite.getUserId() == null) {
            favorite.setUserId(exists.getUserId());
        }
        // 消费者也不能把自己这条挪到别人名下，挪完就等于把收藏送出去了
        assertOwn(favorite);
        if (favorite.getTargetType() == null) {
            favorite.setTargetType(exists.getTargetType());
        }
        if (favorite.getTargetId() == null) {
            favorite.setTargetId(exists.getTargetId());
        }
        validate(favorite, favorite.getId());
        try {
            updateById(favorite);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(duplicateMessage());
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 消费者账号删的必须是自己那几条：先按 id 捞出实体逐条校验，堵住直接拿别人 id 打接口这条路
        if (LoginUserUtils.isConsumer()) {
            List<ShopConsumerFavorite> favorites = listByIds(ids);
            for (ShopConsumerFavorite favorite : favorites) {
                assertOwn(favorite);
            }
        }
        // removeByIds 走全局逻辑删除，只把 is_deleted 置 1；
        // 注意 uk_user_target 不含 is_deleted，删掉的行仍占着唯一键，所以重新收藏同一对象走的是
        // insert 里的复活那一路（顺带刷 favorite_time），不会插出第二条
        removeByIds(ids);
    }

    /**
     * 消费者账号的归属校验，口径同 selectPage 的数据范围：非消费者直接放过（平台/门店/供应商看全部）。
     * 列表已经收窄了，这里补的是"绕过列表直接拿 id/别人的 userId 打接口"那条路。
     */
    private void assertOwn(ShopConsumerFavorite favorite) {
        if (!LoginUserUtils.isConsumer()) {
            return;
        }
        Long userId = favorite.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这条收藏记录不属于你，无权查看或修改");
        }
    }

    /**
     * 对象名称、昵称是回显字段，不在实体上，先按同名属性拷出一份只含 shop_consumer_favorite 列的实体
     */
    private ShopConsumerFavorite toEntity(ShopFavoriteSaveDto form) {
        ShopConsumerFavorite favorite = new ShopConsumerFavorite();
        BeanUtils.copyProperties(form, favorite);
        return favorite;
    }

    /**
     * 校验必填、消费者与收藏对象都存在（按 targetType 去对应表里查）、(消费者, 类型, 对象) 不重复。
     *
     * 表上没有外键，也没有冗余的对象名称列，所以这里只拦"指向不存在的东西"，
     * 至于对象后来改名、下架，收藏这条记录本身不受影响，列表里按联表结果实时显示。
     */
    private void validate(ShopConsumerFavorite favorite, Long excludeId) {
        if (favorite.getUserId() == null) {
            throw new ServiceException("请选择消费者");
        }
        if (sysUserMapper.selectById(favorite.getUserId()) == null) {
            throw new ServiceException("所选消费者不存在");
        }
        String targetName = assertTargetExists(favorite);
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopConsumerFavorite>()
                .eq(ShopConsumerFavorite::getUserId, favorite.getUserId())
                .eq(ShopConsumerFavorite::getTargetType, favorite.getTargetType())
                .eq(ShopConsumerFavorite::getTargetId, favorite.getTargetId())
                .ne(excludeId != null, ShopConsumerFavorite::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该消费者已收藏过「" + targetName + "」，无需重复收藏");
        }
    }

    /**
     * 按类型确认收藏对象在，并把它的名义返回给上层拼提示文案
     */
    private String assertTargetExists(ShopConsumerFavorite favorite) {
        Integer targetType = favorite.getTargetType();
        if (targetType == null) {
            throw new ServiceException("请选择收藏类型");
        }
        if (favorite.getTargetId() == null) {
            throw new ServiceException("请选择收藏对象");
        }
        if (targetType == TARGET_TYPE_PRODUCT) {
            ShopProduct product = shopProductMapper.selectById(favorite.getTargetId());
            if (product == null) {
                throw new ServiceException("所选商品不存在");
            }
            return product.getProductName();
        }
        if (targetType == TARGET_TYPE_STORE) {
            ShopStore store = shopStoreMapper.selectById(favorite.getTargetId());
            if (store == null) {
                throw new ServiceException("所选门店不存在");
            }
            return store.getStoreName();
        }
        if (targetType == TARGET_TYPE_BRAND) {
            ShopBrand brand = shopBrandMapper.selectById(favorite.getTargetId());
            if (brand == null) {
                throw new ServiceException("所选品牌不存在");
            }
            return brand.getBrandName();
        }
        throw new ServiceException("收藏类型只能是 1商品、2门店 或 3品牌");
    }

    private String duplicateMessage() {
        // 前置查重只能看到未删除的行，走到这里基本都是撞在一条已删除的收藏上
        return "该消费者已收藏过这个对象，可能来自一条已删除的收藏记录（唯一键 uk_user_target 仍占着）";
    }
}
