package com.shop.dto.plaza;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author ycy
 * @title ShopPlazaPublishDto
 * @description 前台发布广场消息的参数（Web 与 App 两端共用一条 comm 接口）
 *
 * 只有这五格是前端说了算的：
 * user_id 由登录账号钉、audit_status 一律落 0 待审核、status/publish_time 由审核那一路决定、
 * 三个计数读明细现算、content_text 由服务端从正文里算出来 ——
 * 这些格子都不在这里出现，参数能点名别人就等于替别人发言（口径同发表评论那份 DTO）。
 *
 * 正文是富文本组件产出的 HTML，图片内嵌在 &lt;img&gt; 标签里，所以不像评论那样收 `[图片](url)` 那一串、
 * 也不做张数闸：图还是「先传后提交」（multipart 全站只有 POST /api/file/uploadImage 一处），
 * 编辑器把回来的地址写进标签里再随正文一起提交。服务端只过两道理：
 * 剥掉标签后的文字不能是空、正文整体不能长到把 mediumtext 灌满（见 ShopPlazaWriteServiceImpl）。
 *
 * 广场消息图是单独的一格：同样先传后提交，回来的地址按上传顺序用英文逗号拼在 plazaImage 上（服务端收 9 张、
 * 单个地址和整串各有一道长度闸，见 PlazaContentUtils.assertPlazaImages），服务端不从正文里抓图来补这一格。
 */
@Data
@ApiModel(value = "发布广场消息参数")
public class ShopPlazaPublishDto {

    @ApiModelProperty(value = "标题，可空（首尾空白会被去掉）")
    private String title;

    @ApiModelProperty(value = "富文本正文（HTML），必填：剥掉标签后的文字不能是空")
    private String content;

    @ApiModelProperty(value = "广场消息图URL，可空（发布人自己上传的图，多张用英文逗号拼在这一格；不传或传空就是没图，卡片不配图）")
    private String plazaImage;

    @ApiModelProperty(value = "关联类型：0无关联 1商品 2门店 3品牌 4活动（字典 plaza_relation_type）；不传按 0")
    private Integer relationType;

    @ApiModelProperty(value = "关联对象ID：relationType 不是 0 时必填，按那一档指向商品/门店/品牌/活动那张表")
    private Long relationId;
}
