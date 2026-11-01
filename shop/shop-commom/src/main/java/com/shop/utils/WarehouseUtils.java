package com.shop.utils;

import com.shop.entity.ShopErpWarehouse;

/**
 * 仓库状态判据：哪一档还能动账、动不了的时候怎么报。
 *
 * 盘点中（2）和调拨中（3）都把仓里的账冻住 —— 一边数货一边进出，盘出来的数就不作数了；
 * 调拨本身已经在挪货，再叠一笔入库/出库会说不清剩多少。禁用（0）不在这道闸里：
 * 它只是不再出现在下拉候选里，已经压在单据上的货该入还得入（口径见各 service 的仓库校验）。
 */
public class WarehouseUtils {

    private WarehouseUtils() {
    }

    /** 这家仓的账是否冻住（盘点中 / 调拨中）；状态没带（老数据、稀疏实体）按没冻住处理 */
    public static boolean frozen(ShopErpWarehouse warehouse) {
        if (warehouse == null || warehouse.getStatus() == null) {
            return false;
        }
        int status = warehouse.getStatus();
        return status == ShopErpWarehouse.STATUS_STOCKTAKING || status == ShopErpWarehouse.STATUS_TRANSFER;
    }

    /**
     * 拒绝这句话：action 是动账那个动作名（入库 / 出库 / 从这家仓出库 / 下单发货）。
     * 页面回显走字典 warehouse_status，服务端抛异常时读不到字典，所以状态名在这儿自己写死，
     * 加新档时记得和字典一起对。
     */
    public static String frozenMessage(ShopErpWarehouse warehouse, String action) {
        return "仓库「" + warehouse.getWarehouseName() + "」当前是" + statusName(warehouse.getStatus())
                + "，账已冻结，不能" + action + "；请先在仓库管理里把它改回启用";
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        int value = status;
        if (value == ShopErpWarehouse.STATUS_STOCKTAKING) {
            return "盘点中";
        }
        if (value == ShopErpWarehouse.STATUS_TRANSFER) {
            return "调拨中";
        }
        if (value == ShopErpWarehouse.STATUS_ENABLED) {
            return "启用";
        }
        return "禁用";
    }
}
