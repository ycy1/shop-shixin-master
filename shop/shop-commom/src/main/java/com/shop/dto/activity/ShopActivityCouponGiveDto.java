package com.shop.dto.activity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 优惠券发放参数（后台「发放记录」弹窗顶上那颗「发放」）
 *
 * 一次给一批用户发券，每人张数由 quantity 说（不传就是一人一张）。
 * 面额、有效期、适用范围这些都由服务端按模板现取（含快照那六格），
 * 参数里只认"发给哪张券、发给谁、每人几张、备注"四格 —— 免得前端传个面额进来就能发一张不存在的券。
 */
@Data
@ApiModel(value = "优惠券发放参数")
public class ShopActivityCouponGiveDto {

    @ApiModelProperty(value = "券模板ID(shop_activity_coupon.id)，必填")
    private Long couponId;

    @ApiModelProperty(value = "持券用户ID列表（sys_user.id），必填、至少一个；同一个用户在一批里传多次只算一个人")
    private List<Long> userIds;

    @ApiModelProperty(value = "每个用户发几张，不传按 1，要大于 0；本次占的发行额度 = 人数 × 这一格，够不够由服务端按总量校验那一条 UPDATE 判")
    private Integer quantity;

    @ApiModelProperty(value = "备注（选填，写进每一行的 remark，例如：618 补偿发放）")
    private String remark;
}
