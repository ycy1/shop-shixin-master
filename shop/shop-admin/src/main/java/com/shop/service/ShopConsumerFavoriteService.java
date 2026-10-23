package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.dto.favorite.ShopFavoriteSaveDto;
import com.shop.entity.ShopConsumerFavorite;
import com.shop.vo.favorite.ShopFavoriteVo;

import java.util.List;

/**
 * 用户收藏 服务接口
 */
public interface ShopConsumerFavoriteService extends IService<ShopConsumerFavorite> {

    /**
     * 分页查询收藏（联表按类型带出商品名/门店名/品牌名，另带消费者昵称）
     *
     * @param query 查询条件（keyword/userId/targetType/时间范围）
     * @return 收藏分页
     */
    IPage<ShopFavoriteVo> selectPage(ShopFavoriteQueryDto query);

    /**
     * 单条收藏详情（供修改弹窗回显）
     *
     * @param id 收藏记录id
     * @return 收藏视图对象
     */
    ShopFavoriteVo selectFavoriteById(Long id);

    /**
     * 新增收藏：按 targetType 校验对象存在，(消费者, 类型, 对象) 已收藏过时报错
     *
     * @param form 新增参数
     */
    void insert(ShopFavoriteSaveDto form);

    /**
     * 修改收藏：换了类型或对象都会重新按新对象校验存在性与重复
     *
     * @param form 修改参数
     */
    void update(ShopFavoriteSaveDto form);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 记录id集合
     */
    void deleteByIds(List<Long> ids);
}
