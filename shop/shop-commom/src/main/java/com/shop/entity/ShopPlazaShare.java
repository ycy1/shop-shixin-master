package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 广场消息分享（一次分享一行，只插不改不删，同积分/钱包流水那一档的用法）
 *
 * 记这一张表的唯一动作是前台点「分享」：只 INSERT 一行，谁都不回写 shop_plaza_message.share_count ——
 * 那个数由 ShopPlazaMessageMapper.xml 按这张表现算（口径同点赞数）。没有修改和删除入口，
 * 所以表单里除了 messageId / shareChannel / shareContent 之外都不由前端决定 —— ip 由服务端按请求补。
 *
 * user_id 是分享者（发布人自己转发也算），和留痕的 create_by 是两个概念：create_by 由 MP 按登录账号强制覆盖，
 * 服务层钉的 user_id 才是这一行「谁分享的」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_plaza_share")
@ApiModel(value = "广场消息分享")
public class ShopPlazaShare extends BaseEntity {

    @ApiModelProperty(value = "消息ID（shop_plaza_message.id）")
    private Long messageId;

    @ApiModelProperty(value = "分享用户ID（sys_user.id）；服务端按登录账号钉，改不了")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    @ApiModelProperty(value = "分享渠道：1微信好友 2朋友圈 3QQ 4微博 5复制链接 8其他（字典 share_channel）")
    private Integer shareChannel;

    @ApiModelProperty(value = "分享附言，可空")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String shareContent;

    @ApiModelProperty(value = "分享IP（服务端按请求补）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String ip;

    @ApiModelProperty(value = "设备ID（前端带上才有，可空）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String deviceId;
}
