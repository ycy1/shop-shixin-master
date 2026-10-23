package com.shop.vo.training;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 培训签到结果
 *
 * 和报名那一个返回同一个理由：客户端拿到就能提示"签到成功"，不用再调一次参训人员列表。
 * 这一格只回签到那两列（attend_status / attend_time），报名的快照（姓名/电话/门店）不动也不回，
 * 前端要看报名情况走「培训人员」那一条读接口。
 */
@Data
@ApiModel(value = "培训签到结果")
public class ShopTrainingAttendVo {

    @ApiModelProperty(value = "报名记录ID(shop_store_training_member.id)")
    private Long memberId;

    @ApiModelProperty(value = "培训ID(shop_store_training.id)")
    private Long trainingId;

    @ApiModelProperty(value = "培训编号")
    private String trainingNo;

    @ApiModelProperty(value = "培训名称")
    private String trainingName;

    @ApiModelProperty(value = "出勤状态（字典 training_attend_status），签成本次恒为 1 已签到")
    private Integer attendStatus;

    @ApiModelProperty(value = "签到时间（服务端取的当前时间）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime attendTime;
}
