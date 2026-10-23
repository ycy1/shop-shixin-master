package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品库存（进销存管理 -> 库存管理 -> 商品库存）：一条规格在一个仓库一行，靠 uk_sku_warehouse 定死
 *
 * 这一张表和别的 shop_* 表不一样，它是"账"不是"单据"：
 * 1. 没有新增/删除接口。行由入库动作自动建（见 ShopErpInventoryServiceImpl#inbound），
 *    删除会把已经入进来的数量凭空抹掉，而且软删的行仍然占着 (sku_id, warehouse_id) 唯一键，
 *    以后这个组合再入库就会撞键。
 * 2. stock / locked_stock / in_transit_stock 只能由单据动作改，库存页的「修改」只放开成本价和
 *    三个预警参数（见 ShopErpInventoryServiceImpl#update 的白名单）。
 * 3. available_stock 是数据库的 STORED 生成列，MySQL 不允许对它写入，所以下面标成 exist=false，
 *    只在 XML 的查询列里带出来。任何时候都别把它变成真列。
 * 4. user_id 只记"最后一次动这行的人"，故意不做数据范围：库存行是共享的，按人收窄会让两个操作员
 *    入同一条规格时各建一行、库存算成两份（和采购单/仓库/入库单那三件套相反）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_erp_inventory")
@ApiModel(value = "商品库存")
public class ShopErpInventory extends BaseEntity {

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id）；这行的身份之一，表单不给改")
    private Long skuId;

    @ApiModelProperty(value = "仓库ID（shop_erp_warehouse.id）；这行的身份之一，表单不给改")
    private Long warehouseId;

    // 不是 IGNORED：库存页的「修改」根本不碰这一列，只有入库动作会 set 它，
    // 给 IGNORED 反而会让"只改成本价"那次 UPDATE 把最后操作人写成 null
    @ApiModelProperty(value = "最后一次操作这行库存的ERP操作员用户ID（sys_user.id）；只记人，不做数据范围")
    private Long userId;

    @ApiModelProperty(value = "总库存（服务端单据动作维护，前端传了也不认）")
    private Integer stock;

    @ApiModelProperty(value = "锁定库存（下单占用）。涨在统一下单 ShopErpInventoryMapper#lockStock，"
            + "回在两处：商品出库那条 decreaseStock（货出仓时连带把对应的那份锁定还掉）、"
            + "以及取消未支付的单那条 unlockStock（一行货都没出过仓，整行原路还回去）；"
            + "超时没付又没人点取消的单子不会被还得回去（没有定时任务）")
    private Integer lockedStock;

    @ApiModelProperty(value = "在途库存（采购已下单未入库）。这一版没有写入方，恒为 0")
    private Integer inTransitStock;

    // 数据库生成列（stock - locked_stock），标成 exist=false 是为了让 MP 的 INSERT/UPDATE 永远跳过它，
    // 值由 XML 的查询列现取（同 storeName 那类联表列）
    @ApiModelProperty(value = "可用库存 = 总库存 - 锁定库存（数据库算，非本表可写列）")
    @TableField(exist = false)
    private Integer availableStock;

    @ApiModelProperty(value = "安全库存，低于此值预警补货；建行时从 shop_product_sku 快照，之后可按仓库单独调")
    private Integer safetyStock;

    // 可空且表单里能清掉（= 不设上限）→ 必须 IGNORED，否则全局 not_empty 会把 null 从 SET 里跳过
    @ApiModelProperty(value = "最大库存，高于此值预警积压；NULL 表示不设上限")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer maxStock;

    @ApiModelProperty(value = "是否开启库存预警：1是 0否")
    private Integer stockWarnEnable;

    // 同样给 IGNORED：成本价允许清空（还没入过货的行填错了一格想抹掉）。入库动作每次都会 set 新值，不受影响
    @ApiModelProperty(value = "成本价；入库时按加权平均刷新：(原库存×原成本价 + 本次入库数×本次单价) ÷ (原库存+本次入库数)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal costPrice;

    // 乐观锁：MybatisPlusConfig 注册了 OptimisticLockerInnerInterceptor，所以 updateById / update(entity, wrapper)
    // 会自动带上 WHERE version = 旧值、SET version = 旧值 + 1。它不是业务字段：前端把它原样带回来只是
    // 当并发凭据（"我改的是我看到的这一版"），带不回就退回服务端刚读到的那一版，改不了别人的数
    @ApiModelProperty(value = "乐观锁版本号（非表单字段，由服务端维护）")
    @Version
    private Integer version;

    // 以下都不是本表列：XML 联表带出来给列表和详情回显用（同 ShopErpWarehouse 的 storeName / userName）
    @ApiModelProperty(value = "商品名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String productName;

    @ApiModelProperty(value = "商品编码（联表回填，非本表列）")
    @TableField(exist = false)
    private String productCode;

    @ApiModelProperty(value = "规格文本（联表 shop_product_sku 回填，非本表列）")
    @TableField(exist = false)
    private String specText;

    // 只当查询条件用：本表没有商品列（一行是「一个 SKU × 一个仓」），按商品筛就是筛这一条规格的所属商品，
    // 命中该商品名下所有规格的库存行
    @ApiModelProperty(value = "查询：按所属商品筛（含它名下所有规格），非本表列")
    @TableField(exist = false)
    private Long productId;

    @ApiModelProperty(value = "仓库名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String warehouseName;

    @ApiModelProperty(value = "最后操作人昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "查询：商品名称/编码/仓库名称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：只看低于安全库存的行（true 时只留 开了预警 且 安全库存>0 且 总库存<安全库存 的行）")
    @TableField(exist = false)
    private Boolean lowStockOnly;
}
