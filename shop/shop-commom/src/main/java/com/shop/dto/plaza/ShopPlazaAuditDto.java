package com.shop.dto.plaza;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 广场消息审核参数（后台「广场消息 → 审核」那颗，通过和拒绝都走这一条；列表上方那颗「批量审核」共用这一份）
 *
 * 只收"哪一条、判哪一档、为什么这么判"三格：这一趟做的是审核流转，
 * 正文/关联对象/置顶那几格归「修改」（整行提交，压 sys:plaza:update），参数里能点名它们就等于用一颗审核按钮改内容。
 *
 * 落在 1 已发布 那一刻服务端补两笔（同一个事务）：publish_time 盖 NOW()（只在这一档第一次进时盖，之后不覆盖），
 * 给发布者 10 积分并补一行 biz_type=12 的积分流水。所以「通过」不只是一格状态，它是这条链唯一给分的入口 ——
 * 拒绝和重复点「通过」都不送分（判据见 ShopPlazaMessageServiceImpl#audit）。
 *
 * ids 那一格只给批量那一条端点用（一次把选中的几条判成同一档、同一句备注）；
 * 单条那一条认 id，批量那一条认 ids，两头不互相当默认值 —— 少递哪一格都是当场报错，不静默按 0 条办。
 */
@Data
@ApiModel(value = "广场消息审核参数")
public class ShopPlazaAuditDto {

    @ApiModelProperty(value = "消息ID（shop_plaza_message.id），单条审核必填")
    private Long id;

    @ApiModelProperty(value = "消息ID集合，批量审核必填（同一批一律判成 auditStatus 那一档、写同一句备注）")
    private List<Long> ids;

    @ApiModelProperty(value = "审核结果：1已发布 2已拒绝，必填。0 待审核是发布落库那一档，不是人能判出来的")
    private Integer auditStatus;

    @ApiModelProperty(value = "审核备注，可空；判 2 已拒绝 时建议填 —— 这一格是发布者在前台唯一看得见的说法（为什么没上广场）")
    private String auditRemark;
}
