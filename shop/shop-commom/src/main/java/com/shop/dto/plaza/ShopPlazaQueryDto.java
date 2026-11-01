package com.shop.dto.plaza;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author ycy
 * @title ShopPlazaQueryDto
 * @description 前台广场列表的查询参数（Web 与 App 两端同形，共用同一份读 service）
 *
 * 前端能点的就这五格：关键字搜标题/正文摘要/发布者昵称，关联类型切「挂商品的 / 挂门店的」那一档，
 * 再加审核状态（只有「我发的」那一页用得上）、「只看推荐」那一个开关（广场右侧那一块推荐位用）
 * 和「按点赞量排」那一个开关（搜索结果页那一块用，广场那一页不传）。
 *
 * 刻意没有 status 和 isDeleted 两格：
 * status=1 且 audit_status=1 那一刀由 ShopPlazaReadServiceImpl 传 onlyPublic=true 压在 SQL 里，
 * 广场那一页无论传什么都到不了这一档（游客不该翻到已下架的）；isDeleted 更不给 ——
 * 服务层是照着这个 DTO 一格一格搭查询条件的，没在 DTO 里出现的列，前端就没法点名
 * （isDeleted 由 ShopPlazaReadServiceImpl.page 钉死 0 —— 列表 SQL 现在不填就不拦那一刀，后台要混着看已删除的那几条；
 * 软删行因此只有后台两条列表看得见）。
 * auditStatus 也不构成口子：广场那一页的服务层那一刀在 WHERE 里排在它后面，
 * 就算点名「待审核」也还是被 status=1 AND audit_status=1 收着，只有「我发的」那一页才真正吃到它（看的正是自己那几条）。
 * 分页吃查询串里的 pageNum/pageSize，由 PageUtil 直接从请求里取，和后台列表同一套（广场页一次 10 条也是前端在这两格上给的）。
 */
@Data
@ApiModel(value = "广场消息查询参数")
public class ShopPlazaQueryDto {

    @ApiModelProperty(value = "标题/正文摘要/发布者昵称关键字，可空")
    private String keyword;

    @ApiModelProperty(value = "关联类型：0无关联 1商品 2门店 3品牌 4活动（字典 plaza_relation_type）；不传就是全都看")
    private Integer relationType;

    @ApiModelProperty(value = "审核状态：0待审核 1已发布 2已拒绝（字典 plaza_audit_status）；只对「我发的」那一页有用，广场那一页被 onlyPublic 那一刀收着")
    private Integer auditStatus;

    @ApiModelProperty(value = "只看推荐：1就只捞 is_recommend=1 的那一些（广场右侧推荐位用）；不传或 0 就是整条流")
    private Integer isRecommend;

    @ApiModelProperty(value = "按点赞量倒序：1 就把序换成「赞最多的排最前」（搜索结果页那一块用）；不传或 0 就是广场那一页原本的流（置顶打头、再按发布时间倒序）")
    private Integer orderByLike;
}
