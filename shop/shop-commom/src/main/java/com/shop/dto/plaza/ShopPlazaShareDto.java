package com.shop.dto.plaza;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author ycy
 * @title ShopPlazaShareDto
 * @description 前台记一次分享的参数（两端共用一条 comm 接口）
 *
 * 只有这两格：分享哪一条、走的是哪一档渠道。userId 由登录账号钉，
 * ip 由服务端按请求现取（口径同评论留痕），share_content 和 device_id 这两列本期前端不填、留空。
 *
 * 这一条只是留痕：广场上的「分享数」由 shop_plaza_share 现算，所以这里既不加计数也不判重复 ——
 * 同一个人复制链接三次就是三行，和曝光点击那一条埋点同一个口径（不防刷，只是统计）。
 */
@Data
@ApiModel(value = "广场消息分享参数")
public class ShopPlazaShareDto {

    @ApiModelProperty(value = "消息ID（shop_plaza_message.id）")
    private Long messageId;

    @ApiModelProperty(value = "分享渠道：1微信好友 2朋友圈 3QQ 4微博 5复制链接 8其他（字典 share_channel）；不传按 5 复制链接")
    private Integer shareChannel;
}
