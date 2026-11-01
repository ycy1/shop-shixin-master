package com.shop.vo.notice;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告详情 Web/App 端视图（GET /api/web/shop/notice/{id}、/api/app/shop/notice/{id}）
 *
 * 只有这一条端点带 content（后台 WangEditor 出来的 HTML）：列表那几处消息读的是 sys_notifications.message，
 * 那边在 service 里就剥成纯文本了，把 HTML 递到不要登录的端点上是白给一个 XSS 面。
 * 前台渲染前还要再过一道（web 走 lib/richtext.ts 的白名单，App 走 <rich-text> 自己的建树规则），两头各挡一遍。
 *
 * 不给的：noticePush（那是「这条发给谁」的推送范围，不是内容）、isShow、position。
 */
@Data
@ApiModel(value = "公告详情 Web/App 端视图")
public class NoticeDetailWebVo {

    @ApiModelProperty(value = "公告id")
    private Long id;

    @ApiModelProperty(value = "公告标题")
    private String title;

    @ApiModelProperty(value = "公告内容（富文本 HTML，前台过完白名单才渲染）")
    private String content;

    @ApiModelProperty(value = "发布时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;
}
