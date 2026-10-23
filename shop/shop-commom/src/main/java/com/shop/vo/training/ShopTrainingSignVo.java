package com.shop.vo.training;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 培训报名结果
 *
 * 和配送单接单那几个返回同一个理由：客户端拿到就能提示"报名成功，当前已报 X/Y 人"，
 * 不用再调一次培训详情。signedCount 是写完这一行之后现数出来的（含本次），
 * 不是 shop_store_training.sign_count 那一列 —— 那列没人回写，读的时候一律按报名表现算。
 */
@Data
@ApiModel(value = "培训报名结果")
public class ShopTrainingSignVo {

    @ApiModelProperty(value = "报名记录ID(shop_store_training_member.id)")
    private Long memberId;

    @ApiModelProperty(value = "培训ID(shop_store_training.id)")
    private Long trainingId;

    @ApiModelProperty(value = "培训编号")
    private String trainingNo;

    @ApiModelProperty(value = "培训名称")
    private String trainingName;

    @ApiModelProperty(value = "这次报名挂的门店ID(shop_store.id)")
    private Long storeId;

    @ApiModelProperty(value = "这次报名挂的门店名称")
    private String storeName;

    @ApiModelProperty(value = "报名人姓名（刚写进快照那一格，取自 sys_user.nickname，没昵称时取 username）")
    private String userName;

    @ApiModelProperty(value = "报名状态（字典 training_sign_status），报成本次恒为 1 已报名")
    private Integer signStatus;

    @ApiModelProperty(value = "报名时间（服务端取的当前时间）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime signTime;

    @ApiModelProperty(value = "报完之后这一场的已报名人数（按报名表现数，含本次；取消过的不算）")
    private Integer signedCount;

    @ApiModelProperty(value = "这一场的名额上限（shop_store_training.max_count），NULL=不限")
    private Integer maxCount;
}
