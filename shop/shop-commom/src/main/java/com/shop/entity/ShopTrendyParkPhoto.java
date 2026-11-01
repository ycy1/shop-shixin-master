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
 * 园区照片表（一家园区名下的每一张照片一行）
 *
 * 这张表是把园区表上那个 images json 数组换成的：数组里一个 url 一行，才挂得住来源、审核状态、
 * 精选和标签 —— 那几格在 JSON 字符串里没有地方放，而「用户上传的图要先审后展示」这条规矩，
 * 靠一个纯字符串数组是表达不出来的。
 *
 * ip_id 不是人填的：照片属于哪个 IP 跟着园区走（园区行上那一格），服务层按 park_id 带过来，
 * 前端传了也不认。两处各存一份迟早出现「照片说自己属于 A IP、园区写着 B IP」。
 *
 * tags 是 MySQL json 列，按项目约定映射成 String（同 ShopStore.coverImages、原来的 ShopTrendyPark.images）：
 * 这一格不能标 IGNORED —— not_empty 正好挡住了空串写进 json 列直接报错，
 * 「清空标签」是前端提交 "[]" 实现的，服务层还会把 blank 归一成 "[]"。
 *
 * status 四档里前台只认 1 通过；0 待审核是给「用户上传」那条路留的（那一条这一期还没开）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_park_photo")
@ApiModel(value = "园区照片")
public class ShopTrendyParkPhoto extends BaseEntity {

    /**
     * 两列的长度上限，和建表那两格一致：超了给一句话报错，别让 MySQL 悄悄截半句。
     * 后台那一路（shop-admin 的照片服务）和用户上传那一路（shop-commom 的投稿服务）读的是这两格 ——
     * 改列宽只改这里，两处一起跟着变。
     */
    public static final int IMAGE_URL_MAX = 500;
    public static final int DESCRIPTION_MAX = 64;
    /** 审核意见那一格的列宽，和建表那格一致：超了给一句话报错，别让 MySQL 悄悄截半句 */
    public static final int AUDIT_MSG_MAX = 255;

    /** 审核状态四档（字典 audit_status） */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PASSED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_HIDDEN = 3;

    /** 来源两档（字典 source_type） */
    public static final int SOURCE_OFFICIAL = 1;
    public static final int SOURCE_USER = 2;

    @ApiModelProperty(value = "所属园区ID(shop_trendy_park.id)", required = true)
    private Long parkId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "关联IP ID(shop_trendy_ip.id)，由服务层按园区带过来，NULL=园区本身不归属某个IP")
    private Long ipId;

    @ApiModelProperty(value = "来源类型：1官方上传 2用户上传（字典 source_type）")
    private Integer sourceType;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "上传用户ID(sys_user.id)，官方上传为NULL")
    private Long userId;

    @ApiModelProperty(value = "图片URL", required = true)
    private String imageUrl;

    @ApiModelProperty(value = "标签，JSON 数组字符串：[\"打卡\",\"夜景\"]")
    private String tags;

    @ApiModelProperty(value = "审核状态：0待审核 1通过 2拒绝 3已隐藏（字典 audit_status）")
    private Integer status;

    /**
     * 审核意见（随审核结论一起发给上传人那句）。
     *
     * 故意**不标 IGNORED**：这一格只由审核那一句专用 UPDATE 写（含写成 NULL），
     * 后台「修改」那颗是整行提交、表单里没有这一格，标了 IGNORED 就会被它抹成 NULL
     *（口径同用户票的票面二维码 qr_img —— 同一把坑）。
     */
    @ApiModelProperty(value = "审核意见，NULL=没写过；只有审核那一路会改它")
    private String auditMsg;

    @ApiModelProperty(value = "是否精选：1是 0否")
    private Integer isFeatured;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "照片描述，最多 64 个字")
    private String description;

    // 下面两格都不是本表列：园区名按 park_id 查回来，上传人按 user_id 查回来
    //（形状同 ShopTrendyParkTicket.parkName；查不到就留空，前端显示 '-'）
    @TableField(exist = false)
    @ApiModelProperty(value = "所属园区名称（服务层回填，非本表列）")
    private String parkName;

    @TableField(exist = false)
    @ApiModelProperty(value = "上传人昵称（服务层回填，非本表列；官方上传那几行本来就是空的）")
    private String userName;

    @ApiModelProperty(value = "查询：描述/标签关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
