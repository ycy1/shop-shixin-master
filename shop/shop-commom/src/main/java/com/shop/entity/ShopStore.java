package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 门店表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_store")
@ApiModel(value = "门店")
public class ShopStore extends BaseEntity {

    @ApiModelProperty(value = "门店编码，唯一；新增时不传则由系统生成（STORE_+时间戳）")
    private String storeCode;

    @ApiModelProperty(value = "门店名称")
    private String storeName;

    @ApiModelProperty(value = "门店类型：1直营 2加盟 3合作（字典 store_type）")
    private Integer storeType;

    // 品牌可空，"取消绑定品牌"是正常操作，所以同样要 IGNORED（口径同 ShopProduct.brandId）
    @ApiModelProperty(value = "绑定品牌ID(shop_brand.id)，NULL 表示这家店不绑品牌")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long brandId;

    // 品牌名/LOGO 不是本表列，服务层按 brand_id 查 shop_brand 回填，只给列表那一列「绑定品牌」显示用
    // （表单里的下拉自己拿 options 映射，不读这两格）
    @ApiModelProperty(value = "绑定品牌名称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String brandName;

    @ApiModelProperty(value = "绑定品牌LOGO（服务层回填，非本表列）")
    @TableField(exist = false)
    private String brandLogo;

    // 下面这些列都可空，且"清空"是正常操作：全局 field-strategy=not_empty 会把 null 和空串一起
    // 从 UPDATE 的 SET 里跳过，不显式声明 IGNORED 的话前端清空后保存不生效
    @ApiModelProperty(value = "联系人")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactName;

    @ApiModelProperty(value = "联系电话")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contactPhone;

    // 两列都可空，且"清空地区"是正常操作，所以同样要显式 IGNORED
    // area_code 是逗号分隔的行政区划代码（110000,110100,110101），前端级联选择器直接 join/split；
    // area_zh 是提交时一并冗余下来的中文地名（同 sys_user 那两列的写法），列表显示不用每次解析
    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码：110000,110100,110101")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaCode;

    @ApiModelProperty(value = "地区中文，如 北京市 市辖区 东城区")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaZh;

    @ApiModelProperty(value = "详细地址")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String address;

    @ApiModelProperty(value = "经度")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal latitude;

    @ApiModelProperty(value = "营业时间，如 09:00-22:00")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String businessHours;

    @ApiModelProperty(value = "营业状态：1营业中 0休息中 2已打烊（字典 store_business_status）")
    private Integer businessStatus;

    @ApiModelProperty(value = "是否配送：1是 0否")
    private Integer isDelivery;

    @ApiModelProperty(value = "配送半径(米)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer deliveryRadius;

    @ApiModelProperty(value = "起送金额")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal minDeliveryAmount;

    @ApiModelProperty(value = "门店LOGO URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String logo;

    // shop_store.cover_images 是 MySQL json 列，按项目约定映射成 String（同 ShopProduct.images、
    // ShopBrand.brandSpokesperson）。这个字段不能加 IGNORED：全局 not_empty 跳过空串，
    // 正好挡住了空串写进 json 列直接报错；"清空门店图片"是前端提交 "[]" 实现的，
    // 服务层还会把 blank 归一成 "[]"。
    @ApiModelProperty(value = "门店图片，JSON 数组字符串：[{\"title\":\"\",\"url\":\"\"}]")
    private String coverImages;

    // 店长可空，"取消指定店长"是正常操作，所以同样要 IGNORED
    @ApiModelProperty(value = "店长用户ID")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long managerId;

    // 昵称不是 shop_store 的列，服务层按 manager_id 查 sys_user 回填，
    // 给列表显示和店长选择器回显用（同 ShopCategory 的 exist=false 字段）
    @ApiModelProperty(value = "店长昵称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String managerName;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动
    @ApiModelProperty(value = "创建人用户ID")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    // 来源招商线索现在在门店的修改弹窗里也改得动（由表单那格「招商信息」下拉整行提交），
    // 所以两列都是 IGNORED：不显式声明的话全局 not_empty 会把"取消绑定"那个 null 跳过、清不掉。
    // 两列必须一起写：编号是 id 的冗余快照，服务层 fillInvestmentSource 按 id 从库里查出来覆盖，
    // 只清一格会让门店列表那颗「招商信息」按钮（判 investment_id）和占用判据（判 investment_no）对不上。
    @ApiModelProperty(value = "来源招商线索ID(shop_store_investment.id)，NULL 表示这家店不是招商来的")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long investmentId;

    @ApiModelProperty(value = "来源招商编号（按 investment_id 从库里带的冗余快照）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String investmentNo;

    @ApiModelProperty(value = "查询：门店名称/编码关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    // 数据范围：非 admin 时由服务层强制置成当前登录用户ID，列表 SQL 用它做
    // "user_id = 我 OR manager_id = 我"。不复用 userId/managerId：那两个是本表真实列，
    // 会随查询串绑进来被调用方控制，而且单列条件拼不出 OR
    @ApiModelProperty(value = "查询：数据范围用户ID（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private Long currentUserId;
}
