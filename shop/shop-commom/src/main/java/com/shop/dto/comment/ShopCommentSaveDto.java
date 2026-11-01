package com.shop.dto.comment;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author ycy
 * @title ShopCommentSaveDto
 * @description 前台发表评论 / 回复的参数（Web 与 App 两端共用一条 comm 接口）
 *
 * 没有 userId、没有 nickname、也没有 ip 那一串留痕格：能发的只有自己那一格，
 * 参数能点名别人就等于替别人发言（口径同本人签到、本人补签）。
 * 留痕四格（ip / 归属地 / 浏览器 / 系统）全由服务端现取，前端传什么都不认。
 *
 * 图片也不另开一格：content 里想带图就写成 `[图片](https://…)`，它和正文一起进这一格，
 * 三端各自把这串拆开画。所以图还是「先传后提交」（multipart 那条口全站只有一处，
 * POST /api/file/uploadImage，shop-file 那份 FileApiController，压缩和落存储都在那边），
 * 前端拿到回来的 URL 自己拼进正文再提交 —— 拼出来的是能被随便编的字符串，
 * 于是服务端要对正文里每一串地址过闸：只认 http(s) 开头、限长、限张数（见 ShopCommentWriteServiceImpl）。
 */
@Data
@ApiModel(value = "发表评论参数")
public class ShopCommentSaveDto {

    @ApiModelProperty(value = "评论类型：1商品 2门店 3品牌 4广场（字典 comment_type）；回复别人时这一格会被忽略，跟着被回复那条评论走")
    private Integer commentType;

    @ApiModelProperty(value = "评论对象：对应那张表的主键；回复别人时同上，也挂到被回复那条评论的对象上")
    private Long businessId;

    @ApiModelProperty(value = "评论内容：非空、文字部分不超 500 字（首尾空白会被去掉）。两样内嵌都写在这一格里 —— "
            + "emoji 直接是正文字符（这一格是 utf8mb4，页面上那颗表情面板插进去的就是普通字符），"
            + "图片写成 [图片](https://…) ，最多 9 张，地址必须是 POST /api/file/uploadImage 给回来的 http(s) 链接")
    private String content;

    @ApiModelProperty(value = "被回复的评论ID：不传或传 null 就是对这个对象发一条顶层评论；"
            + "传子评论的 id 时，服务端把它归到所属的顶层那一行下面，并把它的作者记进被回复人")
    private Integer replyToId;
}
