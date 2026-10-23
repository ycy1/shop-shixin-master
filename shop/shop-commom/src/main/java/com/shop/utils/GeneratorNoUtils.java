package com.shop.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * 订单号工具类
 *
 * @author qy
 * @since 1.0
 */
public class GeneratorNoUtils {

    /**
     * 获取订单编号
     * @return
     */
    public static String getOrderNo() {
        return "ORDER_" + getNo();
    }

    /**
     * 获取退款单编号
     * @return
     */
    public static String getRefundNo() {
        return "REFUND_" + getNo();
    }

    /**
     * 获取门店编号
     * @return
     */
    public static String getStoreNo() {
        return "STORE_" + getNo();
    }

    /**
     * 获取送货员编号
     * @return
     */
    public static String getRiderNo() {
        return "RIDER_" + getNo();
    }

    /**
     * 获取配送单编号
     * @return
     */
    public static String getDeliveryNo() {
        return "DELIVERY_" + getNo();
    }

    /**
     * 获取物流公司编码
     * @return
     */
    public static String getLogisticsNo() {
        return "LOGISTICS_" + getNo();
    }

    /**
     * 获取商品编码
     * @return
     */
    public static String getProductNo() {
        return "PRODUCT_" + getNo();
    }

    /**
     * 获取商品SKU编码
     *
     * 一条 SKU 一个码：库里 sku_code 是唯一键，所以和商品编码一样由服务端生成，页面不给填
     */
    public static String getSkuNo() {
        return "SKU_" + getNo();
    }

    /**
     * 获取供应商编码
     * @return
     */
    public static String getSupplierNo() {
        return "SUPPLIER_" + getNo();
    }

    /**
     * 获取采购单号
     * @return
     */
    public static String getPurchaseNo() {
        return "PURCHASE_" + getNo();
    }

    /**
     * 获取入库单号
     * @return
     */
    public static String getPurchaseInNo() {
        return "PURCHASE_IN_" + getNo();
    }

    /**
     * 获取对外销售单号
     * @return
     */
    public static String getSaleNo() {
        return "SALE_" + getNo();
    }

    /**
     * 获取销售出库单号
     * @return
     */
    public static String getSaleOutboundNo() {
        return "SALE_OUTBOUND_" + getNo();
    }

    /**
     * 获取仓库编码
     * @return
     */
    public static String getWarehouseNo() {
        return "WAREHOUSE_" + getNo();
    }

    /**
     * 获取招商编号
     * @return
     */
    public static String getInvestmentNo() {
        return "INVEST_" + getNo();
    }

    /**
     * 获取培训编号
     * @return
     */
    public static String getTrainingNo() {
        return "TRAIN_" + getNo();
    }

    /**
     * 获取优惠券模板券码
     *
     * 注意发到用户手上那张券的码不走这里：一批发放是同一秒里连着插的，这个「时间戳+3位随机数」一批里就会撞，
     * 而用户券表的 coupon_code 是唯一键，所以那一串号在 ShopActivityCouponServiceImpl 里按已发放数排
     */
    public static String getCouponNo() {
        return "COUPON_" + getNo();
    }

    /**
     * 获取库存调拨单号
     * @return
     */
    public static String getTransferNo() {
        return "TRANSFER_" + getNo();
    }

    /**
     * 获取库存盘点单号
     * @return
     */
    public static String getCheckNo() {
        return "CHECK_" + getNo();
    }

    /**
     * 获取编号
     * @return
     */
    public static String getNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String newDate = sdf.format(new Date());
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            result.append(random.nextInt(10));
        }
        return newDate + result;
    }

}
