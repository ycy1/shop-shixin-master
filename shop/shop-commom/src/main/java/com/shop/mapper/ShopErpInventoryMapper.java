package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 商品库存 Mapper接口
 *
 * 列表和详情走自定义 SQL（要联商品名/编码、仓库名、操作人昵称，BaseMapper 挂不上联表，同仓库/采购单）；
 * 入库和出库那两条更新各走下面 increaseStock / decreaseStock 的手写 CAS，不走 updateById
 * （改预警参数才走 updateById，那条吃 @Version 乐观锁）。
 */
@Mapper
public interface ShopErpInventoryMapper extends BaseMapper<ShopErpInventory> {

    /**
     * 分页查询库存：关键字跨商品名称/编码/仓库名称，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpInventory> selectInventoryPage(IPage<ShopErpInventory> page,
                                                @Param("q") ShopErpInventory query);

    /**
     * 单条库存（带商品名/编码、仓库名、操作人昵称）
     */
    ShopErpInventory selectInventoryById(@Param("id") Long id);

    /**
     * 入库那条 SQL：加库存 + 刷成本价 + 记操作人 + version 自增，一条 UPDATE 走完。
     *
     * WHERE 带 version 是乐观锁。OptimisticLockerInnerInterceptor 只接管 BaseMapper 的
     * updateById / update(entity, wrapper)，自定义 SQL 它不动手，所以这条只能自己写；
     * 再说这条要在 SQL 里做 stock = stock + #{delta} 的增量，本来也不能换成读-改-写。
     * 返回 0 表示这一行刚被别的操作员动过，调用方要重读一次再来，不能覆盖对方的数。
     *
     * 最后那个 stock + #{delta} >= 0 是防减成负库存：入库这一版 delta 恒为正，
     * 出库走的是下面 {@link #decreaseStock} 那条，不共用这里的增量写法。
     */
    int increaseStock(@Param("id") Long id,
                      @Param("delta") Integer delta,
                      @Param("costPrice") BigDecimal costPrice,
                      @Param("userId") Long userId,
                      @Param("updateBy") Long updateBy,
                      @Param("version") Integer version);

    /**
     * 出库那条 SQL：减总库存 + 还锁定 + 记操作人 + version 自增，一条 UPDATE 走完（口径同 increaseStock 的镜像）。
     *
     * 和入库有两处不同：
     * 1. **不动 cost_price**。加权平均只在进货那一次改成本价，卖货是按现在的成本价结转，不改它
     *    （所以这条也没有 costPrice 参数，调用方要成本价就取这次读到的那一版快照）。
     * 2. 多一个 release 参数，同一句话里把 locked_stock 还回去。下单时 lockStock 锁的那一份，
     *    货真出了仓就该同时消掉：只减 stock 不还 locked 的话可用库存会被锁死、再也卖不出去。
     *    两者是各自独立的两个数（本次出库数不一定等于本次释放的锁定数，一张单可以分几次出），
     *    所以由调用方分别传，不写死成同一个。
     *
     * WHERE 后三条都是闸门，任何一条不满足就返回 0 行：
     * version 挡并发覆盖，stock - delta >= 0 挡减成负库存，
     * stock - locked_stock - delta + release >= 0 挡"出完之后可用库存变负"
     * （也就是还的锁定比该还的少、货却已经出出去了 —— 说明调用方把下单锁的那一份漏在了仓里）。
     */
    int decreaseStock(@Param("id") Long id,
                      @Param("delta") Integer delta,
                      @Param("release") Integer release,
                      @Param("userId") Long userId,
                      @Param("updateBy") Long updateBy,
                      @Param("version") Integer version);

    /**
     * 就近分仓：在这条规格「还有可用库存」的仓库里，挑离买家最近的那一家。
     *
     * 可用库存 = stock - locked_stock，已经被别的订单锁住的部分不能重复卖；
     * 只认启用的仓库（w.status = 1），停用仓不该发货；
     * 没录经纬度的仓库直接排除（算不出距离）—— 仓库管理里经纬度已经是必填，
     * 这一道是兜住那之前建出来的老数据（见 ShopErpWarehouseServiceImpl#validate）。
     *
     * @param longitude 起点经度（GCJ-02）：统一下单用的是收货地址上的那一份，
     *                  支付回调改仓也用那一条（sys_order.address_id 背后的收货地址），
     *                  那一单没落地址或地址被删了才退回用门店上的那一份（shop_store.longitude）；
     *                  SQL 本身不管起点是谁
     * @param latitude  起点纬度
     * @param quantity  这一行要买几件
     * @return 最近的可用仓库ID；一家都挑不出来返回 null（库存不足或没有带坐标的启用仓）
     */
    Long selectNearestWarehouseId(@Param("skuId") Long skuId,
                                  @Param("longitude") BigDecimal longitude,
                                  @Param("latitude") BigDecimal latitude,
                                  @Param("quantity") Integer quantity);

    /**
     * 按地区分仓：{@link #selectNearestWarehouseId} 的兜底档 —— 收货地址上没录经纬度、
     * 距离算不出来时，拿地址的行政区划代码去仓库的 area_code 里找同一家（口径同为"这一行还够卖 + 启用仓"）。
     *
     * 命中档位由细到粗：区 > 市 > 省，取最细的那一级（都在同一个区里的仓排在同一个市里的仓前面），
     * 同一级里再按仓库 id 取先建的，别让数据库随机挑；
     * 这一条**不卡仓库经纬度**：正因为起点没坐标才走到这里，再要求终点有坐标就永远挑不出仓。
     *
     * @param province 收货地址的省级行政区划代码（area_code 第一段），没录就传 null
     * @param city     市级（第二段）；东莞市这种 children 是镇的，第二段照样是市码
     * @param district 区级（第三段）
     * @param quantity 这一行要买几件
     * @return 同地区的可用仓库ID；一家都挑不出来返回 null（三段码全没传、库存不足、或没有一家启用仓录了地区）
     */
    Long selectWarehouseIdByArea(@Param("skuId") Long skuId,
                                 @Param("province") String province,
                                 @Param("city") String city,
                                 @Param("district") String district,
                                 @Param("quantity") Integer quantity);

    /**
     * 出货单分仓（优先档）：这一行商品的**门店仓** —— 挂在这个门店名下的那家启用仓库
     * （shop_erp_warehouse.store_id 命中门店，即仓库类型=门店仓那一类），
     * 并且这条规格在那家仓还够卖（可用库存 = stock - locked_stock >= 本行数量）。
     *
     * 和 {@link #selectNearestWarehouseId} 的分工：这一条按"归属"挑（不看距离，
     * 门店仓离门店多远都是它自己的仓），挑不出来（这家门店没建仓、仓停用了、货不够）
     * 才回落到按坐标就近那一条。
     *
     * @param quantity 这一行要出几件
     * @return 这家门店的可用仓ID；一家都挑不出来返回 null
     */
    Long selectStoreWarehouseId(@Param("skuId") Long skuId,
                                @Param("storeId") Long storeId,
                                @Param("quantity") Integer quantity);

    /**
     * 解除锁定：{@link #lockStock} 的镜像，把这家仓这条规格的 locked_stock 减掉 quantity（总库存照样不动）。
     *
     * 只给"改派发货仓"那一步用（ShopOrderPaidServiceImpl#routeWarehouse）：一行订单明细原本锁在 A 仓，
     * 改到 B 仓出库就要先把 B 锁上、再把 A 那份还回去，不然 A 仓白白被一笔已经不指向它的订单锁着。
     *
     * 不带 version 条件：和 lockStock 一样是条件增量，闸门写在 WHERE 的 locked_stock >= quantity 里 ——
     * 还的比锁的多说明调用方算错了，返回 0 行由调用方报错，绝不锁成负数。
     *
     * @return 影响行数，0 = 这一家仓没有这么多可还的锁定
     */
    int unlockStock(@Param("skuId") Long skuId,
                    @Param("warehouseId") Long warehouseId,
                    @Param("quantity") Integer quantity);

    /**
     * 可用库存（stock - locked_stock），锁仓之前先量一次用。
     *
     * warehouseId 传 null 时汇总这条规格在所有仓的可用量 —— 用来分清"就近分仓挑不出仓"
     * 到底是压根没货，还是附近有货但仓没录坐标（两种情况要跟用户说的话不一样）。
     * 传了 warehouseId 就是那一家的数（表上 (sku_id, warehouse_id) 唯一，SUM 只是顺手把
     * "没有这一行"也写成 0，调用方就不用判 null 了）。
     *
     * 注意：这只是"再判断一次"，不是并发保护。真正的闸门在 {@link #lockStock} 那条 UPDATE 的
     * WHERE 里；量完到锁之间被别人插进来是可能的，那一步照样会返回 0 行报错回滚。
     */
    Integer selectAvailableStock(@Param("skuId") Long skuId,
                                 @Param("warehouseId") Long warehouseId);

    /**
     * 锁定库存：下单占坑，把这家仓这条规格的 locked_stock 加上 quantity（总库存不动）。
     *
     * 走条件增量 UPDATE，不走"读-改-写"：WHERE 里的 stock - locked_stock >= quantity 就是防超卖的闸门，
     * 两个订单抢同一批货时数据库只会放行其中一个，另一个返回 0 行。
     * 因此这条也不需要吃 @Version：增量子已经在语句里，覆盖别人的写不可能发生（和 increaseStock 相反，
     * 那条要 set 成本价这种必须先读旧值才能算的列）。
     *
     * @return 影响行数，0 = 这一批刚被别人锁走，调用方要报错让整单回滚
     */
    int lockStock(@Param("skuId") Long skuId,
                  @Param("warehouseId") Long warehouseId,
                  @Param("quantity") Integer quantity);
}
