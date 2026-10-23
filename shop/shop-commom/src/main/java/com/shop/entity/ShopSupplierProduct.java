package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商商品（供货关系）：一行 = 某个供应商供某个规格（SKU），行上带供货价等业务参数。
 *
 * 有了这张表，"哪个供应商能供哪些商品"不再由供应商那格「供应分类」推断（那列已删），
 * 采购单明细的商品候选也就是这里的一次查询（见 ShopSupplierProductMapper#selectProductOptionsBySupplier）。
 *
 * product_name / category_id 是按商品表回填的冗余快照：新增和修改时由服务层覆盖，前端传什么都不认；
 * 商品改名不会回刷已有的行（他的口径是"冗余 展示使用"，省掉列表和下拉再联一次 shop_product）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_supplier_product")
@ApiModel(value = "供应商商品")
public class ShopSupplierProduct extends BaseEntity {

    @ApiModelProperty(value = "供应商ID（shop_erp_supplier.id），必填")
    private Long supplierId;

    @ApiModelProperty(value = "商品SKU ID（shop_product_sku.id），必填；(supplier_id, sku_id) 唯一")
    private Long skuId;

    @ApiModelProperty(value = "商品名称（服务层按商品表回填的冗余快照，非表单字段）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String productName;

    @ApiModelProperty(value = "商品所属分类ID（服务层按商品表回填的冗余快照，非表单字段；空=该商品没挂分类）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long categoryId;

    @ApiModelProperty(value = "供货价（采购单明细的默认单价），可空=还没谈定")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal supplyPrice;

    @ApiModelProperty(value = "供应商自己的商品编码/货号，可空")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String supplyCode;

    @ApiModelProperty(value = "最小起订量，必须大于 0")
    private Integer minOrderQty;

    @ApiModelProperty(value = "供货周期(天)，可空")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer leadTime;

    @ApiModelProperty(value = "是否该规格的默认供应商：1是 0否；同一规格下最多一行为 1，由服务层互斥")
    private Integer isDefault;

    @ApiModelProperty(value = "合作状态：1合作中 0已停止（字典 supplier_product_status）；采购候选只认 1")
    private Integer status;

    // 以下都不是本表列：XML 联表带出来给列表和下拉回显用（同采购单联供应商名的写法）
    @ApiModelProperty(value = "供应商名称（联表回填，非本表列）")
    @TableField(exist = false)
    private String supplierName;

    @ApiModelProperty(value = "商品编码（联表回填，非本表列）")
    @TableField(exist = false)
    private String productCode;

    // 不是本表列，要多联一跳 shop_product_sku：本表这行记的是 sku_id，规格文本在 SKU 那一行上。
    // 一个供应商现在供的是具体规格，同一商品能有好几行供货关系，光靠本表那份商品名快照分不出哪行是哪行
    @ApiModelProperty(value = "规格文本（联 shop_product_sku 回填，非本表列）")
    @TableField(exist = false)
    private String specText;

    @ApiModelProperty(value = "查询：商品名称/供货编码/供应商名称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    // 只用于查询条件：一个登录账号可以绑多家供应商（绑定不唯一），供应商账号的数据范围要按一组 id 收
    @ApiModelProperty(value = "查询：限定的供应商ID集合（服务层按登录人覆盖，前端传什么都不认，非本表列）")
    @TableField(exist = false)
    private List<Long> supplierIds;
}
