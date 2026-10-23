package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.entity.ShopConsumerBrowse;
import com.shop.vo.browse.ShopBrowseVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 浏览足迹 Mapper接口
 */
@Mapper
public interface ShopConsumerBrowseMapper extends BaseMapper<ShopConsumerBrowse> {

    /**
     * 分页查询足迹，联商品/分类/sys_user 带出商品名、主图、分类名与消费者昵称，
     * 单价现取这条商品名下 SKU 的最低价（足迹只记「看过哪件商品」，没记看到的是哪个规格）
     */
    IPage<ShopBrowseVo> selectBrowsePage(IPage<ShopBrowseVo> page, @Param("q") ShopBrowseQueryDto query);

    /**
     * 查询单条足迹（同上，供修改弹窗回显）
     */
    ShopBrowseVo selectBrowseById(@Param("id") Long id);

    /**
     * 上报一次浏览：按 (user_id, product_id) 把 browse_time 刷成最近一次。
     *
     * 这条 SQL 故意不过滤 is_deleted，所以删掉的足迹会被复活——uk_user_product 不含 is_deleted，
     * 少了这一步，"删了再看同一个商品"就会撞唯一键。
     *
     * @return 影响行数；0 表示这个消费者还没浏览过这个商品，调用方再 INSERT
     */
    int markBrowsed(@Param("userId") Long userId, @Param("productId") Long productId,
                    @Param("browseTime") LocalDateTime browseTime, @Param("updateBy") Long updateBy);

    /**
     * 按 (user_id, product_id) 统计已有条数，同样不带 is_deleted 条件（已删除的行仍占着唯一键）。
     * 只给"修改"用：改完可能和另一条足迹撞成同一对，那一对在库里本来就只能有一条。
     */
    Long countSameTarget(@Param("userId") Long userId, @Param("productId") Long productId,
                         @Param("excludeId") Long excludeId);
}
