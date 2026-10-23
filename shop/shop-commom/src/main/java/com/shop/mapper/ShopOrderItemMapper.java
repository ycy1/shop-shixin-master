package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.entity.ShopOrderItem;
import com.shop.vo.order.ShopOrderItemVo;
import com.shop.vo.order.ShopOrderVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细 Mapper接口
 *
 * 这个表上的查询都放这儿，包括订单主表（sys_order）那两条：主表分页/详情是「我的订单」这一页的另一半，
 * 而它两个筛选（发货状态、所属门店）要落到明细行上，写在一个 mapper 里比拆两个文件互相引用清楚。
 * sys_order 自己的遗留读写在 SysOrderMapper（订单管理那页用），这边只读不写。
 */
@Mapper
public interface ShopOrderItemMapper extends BaseMapper<ShopOrderItem> {

    /**
     * 分页查询订单主表（一行一张单），带出名下明细的行数/件数/门店名
     */
    IPage<ShopOrderVo> selectOrderPage(IPage<ShopOrderVo> page, @Param("q") ShopOrderQueryDto query);

    /**
     * 单张订单的主表字段（详情弹窗上面那一块），不含明细
     */
    ShopOrderVo selectOrderById(@Param("id") Long id);

    /**
     * 一张订单名下的全部明细行（详情弹窗下面那张表），一单最多几十行，不分页。
     *
     * 明细行上只有 sku_id，商品编码在 SPU 那一行，所以 XML 里先联 shop_product_sku 再联 shop_product
     * 两跳取（两道都是 LEFT JOIN：那一行的规格后来被软删了也不该把这条明细整行滤掉）。
     * 商品名/规格文本/单价都是本表那份下单快照，读自己的列，不跟着联表取当前值。
     */
    List<ShopOrderItemVo> selectOrderItemsByOrderId(@Param("orderId") Long orderId);

    /**
     * 退款成功回调那一行：把这次退的件数累加到 returned_quantity，顺带把 ship_status 写成退货轨道上的
     * 那一档 —— 退满 2 已退货、只退了一部分 4 部分退货。
     *
     * 走条件增量 UPDATE 不走"读-改-写"（口径同库存那张表的 lockStock）：
     * WHERE 里那句 returned_quantity + #{quantity} &lt;= quantity 就是闸门。退款单建好之后这一行又被别的
     * 退款动过（并发、或者人工改过数据）时这句不成立，返回 0 行由调用方报错让整单回滚，
     * 绝不会把某一行退得比买得多。
     *
     * @return 影响行数，0 = 这一行还能退的件数不够了
     */
    int increaseReturnedQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 支付回调送分时回写「这一行送了几分」（shop_order_item.gain_points）。
     *
     * 这一格是当时档位的快照：送分按这一行那条规格的 shop_product_sku.gain_points × 件数算出来就钉在行上
     * （档位现在是按规格各存一份，同一件商品几个规格送的分可以不一样），
     * 之后商品或规格改了档位也不跟着变 —— 退款要收回当初送的分，读的就是这一格（写的人见
     * shop-api 的 ShopOrderPointsService#settle，读的人见同一个 service 的 #giveBack）。
     *
     * @param id     明细行 id
     * @param points 这一行送了几分（0 也照样写，把上次算出来的那份盖掉）
     * @return 影响行数
     */
    int updateGainPoints(@Param("id") Long id, @Param("points") Integer points);
}
