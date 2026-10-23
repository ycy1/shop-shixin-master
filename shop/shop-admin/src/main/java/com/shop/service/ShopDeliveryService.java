package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopDelivery;

import java.util.List;

/**
 * 配送单表 服务类
 */
public interface ShopDeliveryService extends IService<ShopDelivery> {

    /**
     * 分页查询配送单
     *
     * @param query keyword 模糊匹配配送单号/出库单号/收件人/手机号，
     *              outboundId、riderId、deliveryType、status 精确匹配，
     *              acceptBeginTime/acceptEndTime 卡接单时间，assignBeginTime/assignEndTime 卡派单时间
     *              （账号类型=送货员的看到的是"派给自己的 + 还没人接的那一批"，并按他常驻地点到发货点的距离由近到远排；
     *               其它后台账号看全部 —— 这一单不再属于某个门店）
     */
    IPage<ShopDelivery> selectPage(ShopDelivery query);

    /**
     * 按 id 查询配送单，并回填送货员姓名和计价规则名
     */
    ShopDelivery detail(Long id);

    /**
     * 新增配送单
     */
    void insert(ShopDelivery delivery);

    /**
     * 修改配送单
     */
    void update(ShopDelivery delivery);

    /**
     * 批量删除配送单
     */
    void deleteByIds(List<Long> ids);

    /**
     * 「商品出库」列表那颗「生成配送单」弹框里那颗「确认」：把这张出库单绑着的那一条配送单从 **0 待派单** 推成 **1 待接单**
     * （2026-09-21 他改的口径："待所有商品出库后 生成配送单 只是查询该出库单的配送单 / 确认后配送单改为待接单"）。
     *
     * 配送单不再是这里建出来的：它在**统一下单**那一刻就建好了（重量、距离、配送费全在那一次定死，
     * 买家付的钱里已经含了它），支付回调再把它换绑到出库单上（见 shop-api 的 ShopOrderDeliveryService）。
     * 这一颗按钮点下去只是"货已经出齐、这一趟可以派了" —— 从这一步起它才进送货员的抢单池
     * （池子的口径是 status = 1，见 ShopDeliveryMapper.xml 那一段）。
     *
     * 三个条件都在这一个方法里判，所以它可以被反复点：这张单还没出满、它那条配送单不存在、
     * 或者那条已经过了 0 待派单（确认过 / 已取消），就抛异常说清是哪一条不满足。
     * 派单方式记成 1 系统派单、派单时间记成点下去的这一刻 —— 这一路没有人挑送货员，
     * 谁先抢到算谁的（接单那一步在公共接口 ShopRiderCommonController#acceptDelivery）。
     *
     * @param outboundId shop_erp_sale_outbound.id（一出库单一配送）
     */
    void confirmDelivery(Long outboundId);
}
