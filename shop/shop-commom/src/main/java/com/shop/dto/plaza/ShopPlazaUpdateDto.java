package com.shop.dto.plaza;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author ycy
 * @title ShopPlazaUpdateDto
 * @description 前台改自己那条广场消息的参数（Web 与 App 两端共用一条 comm 接口）
 *
 * 形状和发布那一份一模一样，只多一个 id：能改的还是那五格，改完一律打回 0 待审核（判据见 ShopPlazaWriteServiceImpl#update）。
 * user_id / audit_status / status / publish_time / 三个计数 / content_text 依然不在这里出现 ——
 * 归属是发布那一刻钉死的，上下架由后台管，摘要那一格由服务端从正文里算（口径同发布那一份）。
 */
@Data
@ApiModel(value = "修改广场消息参数")
public class ShopPlazaUpdateDto {

    @ApiModelProperty(value = "消息ID，必填（只能改自己的那一条）")
    private Long id;

    @ApiModelProperty(value = "标题，可空（首尾空白会被去掉）")
    private String title;

    @ApiModelProperty(value = "富文本正文（HTML），必填：剥掉标签后的文字不能是空")
    private String content;

    @ApiModelProperty(value = "广场消息图URL，可空（多张用英文逗号拼在这一格；不传或传空就是把这一串图清掉）")
    private String plazaImage;

    @ApiModelProperty(value = "关联类型：0无关联 1商品 2门店 3品牌 4活动（字典 plaza_relation_type）；不传按 0")
    private Integer relationType;

    @ApiModelProperty(value = "关联对象ID：relationType 不是 0 时必填，按那一档指向商品/门店/品牌/活动那张表")
    private Long relationId;
}
