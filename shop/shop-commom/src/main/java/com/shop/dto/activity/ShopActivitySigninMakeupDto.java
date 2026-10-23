package com.shop.dto.activity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 后台补签参数（签到管理 ->「签到记录」日历里点某一天补签那一颗）
 *
 * 只有"给谁、补哪一天"两格能填：签到类型（2 补签）、获得积分、连续天数、签到时间这几样都是服务端算的，
 * 表单填不了 —— 一张日历上能点的日子只有"这个月还没签过"的那些，判据由服务端再挡一次
 *（今天和将来不给补，因为那不该叫补签）。
 *
 * 补下来的这一行和正常签到那一行只差一格 sign_type：积分一样给、流水一样记（biz_type=1 签到），
 * 所以这里不需要"要不要给分"那一格 —— 给了就得连带改账户表和积分流水，那三件是同一个事务里的。
 */
@Data
@ApiModel(value = "后台补签参数")
public class ShopActivitySigninMakeupDto {

    @ApiModelProperty(value = "用户ID（sys_user.id），必填 —— 给哪一个人补")
    private Long userId;

    @ApiModelProperty(value = "补签归属日期（yyyy-MM-dd），必填。只能是今天之前的、这个人还没签过的那一天")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD)
    private LocalDate signDate;
}
