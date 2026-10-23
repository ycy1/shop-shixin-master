package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.entity.ShopConsumerFavorite;
import com.shop.vo.favorite.ShopFavoriteVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 用户收藏 Mapper接口
 */
@Mapper
public interface ShopConsumerFavoriteMapper extends BaseMapper<ShopConsumerFavorite> {

    /**
     * 分页查询收藏，按 target_type 联商品/门店/品牌三张表带出对象名称，另带消费者昵称
     */
    IPage<ShopFavoriteVo> selectFavoritePage(IPage<ShopFavoriteVo> page, @Param("q") ShopFavoriteQueryDto query);

    /**
     * 查询单条收藏（同上，供修改弹窗回显）
     */
    ShopFavoriteVo selectFavoriteById(@Param("id") Long id);

    /**
     * 按 (消费者, 类型, 对象) 这一对取消收藏：只认没删的那一条。
     *
     * @return 影响行数；0 表示这一对本来就没什么可取消的（没收藏过，或者早就删掉了）
     */
    int deleteByTarget(@Param("userId") Long userId, @Param("targetType") Integer targetType,
                       @Param("targetId") Long targetId, @Param("updateBy") Long updateBy);

    /**
     * 把已删除的那一条复活：WHERE 上不带 is_deleted，所以这一对只要有过收藏（哪怕已删）就会被翻回来。
     * 连 favorite_time 一起刷成这一次 —— 列表上那格「收藏时间」读的是这一列，create_time 不动。
     *
     * @param favoriteTime 复活后要落的收藏时间；传 null 表示按当前时间记（前台那颗按钮就是这一档，
     *                     后台「新增」代客补录时把页面填的时间带进来）
     * @return 影响行数；0 表示这个消费者从没收藏过这一个对象，调用方再 INSERT
     */
    int reviveByTarget(@Param("userId") Long userId, @Param("targetType") Integer targetType,
                       @Param("targetId") Long targetId, @Param("updateBy") Long updateBy,
                       @Param("favoriteTime") LocalDateTime favoriteTime);
}
