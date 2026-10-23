package com.shop.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.SysRefund;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.vo.refund.ShopOrderRefundVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 退款表 Mapper接口
 */
@Mapper
public interface SysRefundMapper extends BaseMapper<SysRefund> {

    Page<SysRefund> selectRefundPage(@Param("page") Page<SysRefund> page, @Param("query")SysRefund sysRefund);

    /**
     * 一张订单名下的全部退款单（后台「我的订单 → 退款单」弹窗那一张表，不分页）。
     *
     * 回的是 VO 挑出来的那几格 + 审核人昵称（唯一要多联的一张表就是 sys_user，为的是 auditor_id）；
     * 顺带把 extra 那一列捞回来给服务层解析成 items（VO 上那一格 @JsonIgnore，不会出接口）。
     * 商品名称/编码/规格不在这里联 —— 由 ShopOrderItemServiceImpl 按 orderItemId 从这一单的明细行里带。
     */
    List<ShopOrderRefundVo> selectRefundVoByOrderId(@Param("orderId") Long orderId);
} 