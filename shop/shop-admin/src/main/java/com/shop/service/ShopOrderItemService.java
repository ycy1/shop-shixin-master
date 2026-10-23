package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.entity.ShopOrderItem;
import com.shop.vo.order.ShopOrderVo;
import com.shop.vo.refund.ShopOrderRefundVo;

import java.util.List;

/**
 * 订单明细 服务接口（后台这一页只读；写入在小程序侧的统一下单接口）
 *
 * 三个方法服务的是「我的订单」现在的样子：列表一行一张单，详情弹窗上半是主单、下半是名下全部明细行，
 * 列表上那颗「退款单」看的是这一单名下的退货记录。
 */
public interface ShopOrderItemService extends IService<ShopOrderItem> {

    /**
     * 分页查询订单主表（一行一张单，带名下明细的行数/件数/门店名，不带明细行）
     *
     * @param query 查询条件（keyword/userId/storeId/shipStatus/status/时间范围）
     * @return 订单分页
     */
    IPage<ShopOrderVo> selectOrderPage(ShopOrderQueryDto query);

    /**
     * 单张订单详情：主表字段 + 名下全部明细行（弹窗上半 + 下半那张表）。
     * 明细行上的 refundingQuantity 在这一刻现算（按名下在途退款单的 extra 逐行加），
     * 退货弹窗的"还能退"要用它，不然前端给的 max 会比后端肯收的大
     *
     * @param id 订单id（sys_order.id）
     * @return 订单视图对象，items 已填
     */
    ShopOrderVo selectOrderById(Long id);

    /**
     * 一张订单名下的全部退款单（列表上那颗「退款单」弹窗：一行一张退款单，展开看它退了哪些商品行）
     *
     * @param orderId 订单id（sys_order.id）
     * @return 退款单列表，按 id 正序（最新一张在最后），items 已填
     */
    List<ShopOrderRefundVo> selectRefundsByOrderId(Long orderId);
}
