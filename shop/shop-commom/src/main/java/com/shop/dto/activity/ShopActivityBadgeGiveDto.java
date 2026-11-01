package com.shop.dto.activity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 徽章下发参数（「徽章管理」列表上那颗「下发」）
 *
 * 一次给一批用户发一枚徽章，一人一枚（uk_badge_user 一枚徽章一个人只有一行，已经拿到的那一行会跳过、
 * 不重复兑现）。奖励那两格（积分、券）由服务端按定义表现取现发，参数里不收 ——
 * 免得前端传个积分数进来就能凭空给一个人加一笔分。
 *
 * endTime 是这一批的有效期结束时间，留 NULL 就是永久有效（表上那一格 NULL=永久）；
 * 它在这里是"运营替这一批定的截止"，不是定义表上的列 —— 同一枚徽章分批发、各批有效期不同是常态。
 */
@Data
@ApiModel(value = "徽章下发参数")
public class ShopActivityBadgeGiveDto {

    @ApiModelProperty(value = "徽章ID（shop_activity_badge.id），必填")
    private Long badgeId;

    @ApiModelProperty(value = "要发给谁（sys_user.id），必填、至少一个；同一个人在一批里传多次只算一个人，"
            + "已经拿到过这一枚的那些人会跳过（返回值是实发人数）")
    private List<Long> userIds;

    @ApiModelProperty(value = "有效期结束时间，不传就是永久有效")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime endTime;
}
