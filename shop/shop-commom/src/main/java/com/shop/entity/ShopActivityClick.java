package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动曝光/点击记录（一次曝光或一次点击一行，纯流水）
 *
 * 一张表装两种事件，靠 eventType 分：1 曝光 2 点击。前台埋点只有那一条写入路
 * （POST /api/comm/activity/track，两种事件共用一个端点、传事件类型区分），后台这一页只读。
 *
 * userId 和 createBy 不是一回事：前者是"点活动的那个人"（消费者，埋点时按登录态填，未登录为 NULL），
 * 后者是 MyMetaObjectHandler 按当前登录用户强制盖的留痕列，埋点走前台时两者通常同值，但语义不同。
 *
 * ip / ipLocation / os 这三格和 sys_user 登录时盖的那三笔同源同写法（服务端现取，前端一个字段都不传）：
 * 埋点这条路不鉴权，参数里能写的字段等于谁都能填，所以只信 activityId 和 eventType 两格。
 *
 * 活动表那三格统计（曝光量/点击量/点击率）一律读的时候按这张表现算，口径同门店培训的 sign_count，
 * 所以这张表插完行什么都不用回写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_click")
@ApiModel(value = "活动曝光/点击记录")
public class ShopActivityClick extends BaseEntity {

    @ApiModelProperty(value = "活动ID（shop_activity.id）")
    private Long activityId;

    @ApiModelProperty(value = "事件类型：1曝光 2点击（字典 activity_event_type）")
    private Integer eventType;

    @ApiModelProperty(value = "点击/曝光用户ID（sys_user.id），未登录为 NULL")
    private Long userId;

    @ApiModelProperty(value = "IP地址")
    private String ip;

    /** 和 sys_user 那两列同一套：埋点这一步由服务端从请求里现取（IpUtil + UserAgentUtil），前端不传 */
    @ApiModelProperty(value = "IP归属地（服务端按 ip 现解析，非前端传）")
    private String ipLocation;

    @ApiModelProperty(value = "操作系统（服务端从 User-Agent 解析，非前端传）")
    private String os;

    /** 用户名联表现取（同库存流水那一页的操作人）：流水是历史凭证，昵称改了不该回头刷已记下的行 */
    @ApiModelProperty(value = "用户昵称（联表带出，非本表列）")
    @TableField(exist = false)
    private String userName;
}
