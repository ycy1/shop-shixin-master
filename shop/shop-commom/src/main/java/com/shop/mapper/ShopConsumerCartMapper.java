package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.cart.ShopCartQueryDto;
import com.shop.entity.ShopConsumerCart;
import com.shop.vo.cart.ShopCartVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper接口
 */
@Mapper
public interface ShopConsumerCartMapper extends BaseMapper<ShopConsumerCart> {

    /**
     * 分页查询购物车，联表带出商品名称/主图与单价/规格文本、分类、品牌、门店、消费者名称。
     *
     * 购物车一行记的是某个规格（sku_id），名称和主图在商品（SPU）那一行、单价和规格文本在这一条规格
     * 那一行，所以 XML 里是先联 shop_product_sku 再拿它的 product_id 联 shop_product 两跳；
     * 一行一个规格，单价直接取那一行自己的，不再按商品聚合。
     */
    IPage<ShopCartVo> selectCartPage(IPage<ShopCartVo> page, @Param("q") ShopCartQueryDto query);

    /**
     * 查询单条购物车（同上，供修改弹窗回显）
     */
    ShopCartVo selectCartById(@Param("id") Long id);

    /**
     * 前台「购物车」那一页：一次取全某个用户名下的全部行，不分页（数量/勾选/合计都横跨全部行）。
     *
     * 规格或商品已被删掉的行不给（那两跳联出来单价和名称都是 NULL）；商品下架、规格停售的行照给，
     * 由 SQL 现算的 buyable 那一格标成不可购，前端灰掉且不计入合计。
     */
    List<ShopCartVo> selectCartListByUser(@Param("userId") Long userId);

    /**
     * 加购：这一对（消费者, 规格）车上已经有没删的那一条，就在它上面加 delta 件。
     *
     * 走 UPDATE quantity = quantity + delta 而不是先查出来加完写回，是为了让两个标签页各点一次加购
     * 叠得起来；返回影响行数，0 就是这一对没有活着的行（调用方接着试复活那条路）。
     */
    int increaseQuantityBySku(@Param("userId") Long userId, @Param("skuId") Long skuId,
                              @Param("delta") Integer delta, @Param("updateBy") Long updateBy);

    /**
     * 加购第二条路：把 (消费者, 规格) 那条已删除的行复活成「这次加了 quantity 件、默认选中」。
     *
     * 唯一键 uk_user_sku 不含 is_deleted，删掉的行还占着这个键，直接插第二条必撞键
     *（口径同收藏的 reviveByTarget）。categoryId / brandId / storeId 是这次重新从商品上带的快照。
     */
    int reviveBySku(@Param("userId") Long userId, @Param("skuId") Long skuId,
                    @Param("quantity") Integer quantity,
                    @Param("categoryId") Long categoryId, @Param("brandId") Long brandId,
                    @Param("storeId") Long storeId, @Param("updateBy") Long updateBy);

    /**
     * 「全选 / 取消全选」：按人一次把名下没删、且**还能买**的行刷成同一个 selected，不走 id 数组。
     *
     * 下架/停售的那些不刷（页面上那些行的勾是禁用着的，刷成选中就成了前端下一次重拉看见一排点不动的勾）。
     */
    int updateSelectedByUser(@Param("userId") Long userId, @Param("selected") Integer selected,
                             @Param("updateBy") Long updateBy);
}
