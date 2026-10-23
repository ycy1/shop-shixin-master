package com.shop.dto.training;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 培训报名参数（门店那边给自己报一场培训）
 *
 * 只收"报哪一场"这一件事：
 * 报名人一律取当前登录账号（那一行的 user_id 就是登录 id，参数里没有这一格，传了也不认），
 * 报名时间由服务端取当前时间，姓名/电话是服务端按登录账号从 sys_user 现取的快照 ——
 * 这三样只要能由参数决定，就等于谁都能替别人报名、还能把报名记录写成任何内容。
 *
 * 也没有门店那一格了（2026-09-24 起「总部统一培训」那一档取消，培训的所属门店必填）：
 * 报的这场是谁家的培训，就以那家店报名，选不了第二家。
 */
@Data
@ApiModel(value = "培训报名参数")
public class ShopTrainingSignDto {

    @ApiModelProperty(value = "培训ID(shop_store_training.id)，必填")
    private Long trainingId;
}
