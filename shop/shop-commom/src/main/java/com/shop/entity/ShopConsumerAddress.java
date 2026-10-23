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

/**
 * 消费者收货地址
 *
 * 地区按项目统一口径只存两列：area_code（逗号分隔的行政区划代码）+ 冗余的 area_zh，同 sys_user、shop_store。
 * is_default 在同一 user_id 下互斥，由服务层保证（库上不建唯一索引，条件唯一表达不出来）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_consumer_address")
@ApiModel(value = "消费者收货地址")
public class ShopConsumerAddress extends BaseEntity {

    // 这一列和其它 shop_* 表的 user_id 含义不同：别处是"创建人"，这里是地址归属的消费者，
    // 后台创建人留痕看 create_by。消费者账号（account_type=3）的列表按这一列=登录 id 收窄，
    // 详情/加/改/删再校验一次归属；其它账号类型不收窄。
    @ApiModelProperty(value = "消费者用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "收货人姓名")
    private String receiverName;

    @ApiModelProperty(value = "收货人电话")
    private String receiverPhone;

    // 以下都是可空列：全局 field-strategy=not_empty 会跳过 null，清空后写不回库，所以显式 IGNORED
    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码：110000,110100,110101")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaCode;

    @ApiModelProperty(value = "地区中文，如 北京市 市辖区 东城区")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaZh;

    @ApiModelProperty(value = "详细地址")
    private String detail;

    @ApiModelProperty(value = "邮编")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String postalCode;

    @ApiModelProperty(value = "经度(GCJ-02)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal latitude;

    @ApiModelProperty(value = "标签：家/公司/学校（字典 address_tag）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String tag;

    @ApiModelProperty(value = "是否默认：1是 0否（同一消费者下最多一条为 1）")
    private Integer isDefault;
}
