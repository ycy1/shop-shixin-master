package com.shop.vo.store;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 门店 Web 端视图（/api/web/shop/search/stores 和 /api/web/shop/store/{id} 共用）
 *
 * 故意不照后台那样直出 ShopStore 实体：那上面有经纬度、店长用户 id、来源招商线索、创建人和审计字段，
 * 这一条不放登录态、谁都能读，一起吐出去就是泄露。要加字段先问一句：这一格能不能给外人看。
 * 联系人姓名也不带 —— 消费者要的是「打哪个电话」，不是「找哪个人」。
 *
 * coverImages 只在详情那条给（列表那条的列清单里没有它，回来就是 null）：列表一行挂一串图片 JSON，
 * 十二行就是十几 KB 白给，而卡片上用的是 logo。
 */
@Data
@ApiModel(value = "门店 Web 端视图")
public class ShopStoreWebVo {

    @ApiModelProperty(value = "门店id")
    private Long id;

    @ApiModelProperty(value = "门店名称")
    private String storeName;

    @ApiModelProperty(value = "门店类型：1直营 2加盟 3合作（字典 store_type）")
    private Integer storeType;

    @ApiModelProperty(value = "门店LOGO URL")
    private String logo;

    @ApiModelProperty(value = "门店图片，MySQL json 列原样给回（[{\"title\":\"\",\"url\":\"\"}]），只有详情那条填")
    private String coverImages;

    @ApiModelProperty(value = "地区中文，如 北京市 市辖区 东城区")
    private String areaZh;

    @ApiModelProperty(value = "详细地址")
    private String address;

    @ApiModelProperty(value = "联系电话")
    private String contactPhone;

    @ApiModelProperty(value = "营业时间，如 09:00-22:00")
    private String businessHours;

    @ApiModelProperty(value = "营业状态：1营业中 0休息中 2已打烊（字典 store_business_status）")
    private Integer businessStatus;

    @ApiModelProperty(value = "是否配送：1是 0否")
    private Integer isDelivery;
}
