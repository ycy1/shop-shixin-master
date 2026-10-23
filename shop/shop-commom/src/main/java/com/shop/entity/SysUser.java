package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.ArrayTypeHandler;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_user")
@ApiModel(value = "用户信息")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "用户名")
    private String username;

    @ApiModelProperty(value = "密码")
    private String password;

    @ApiModelProperty(value = "微信openid")
    private String openid;

    @ApiModelProperty(value = "性别")
    private Integer sex;

    @ApiModelProperty(value = "用户标签")
    private String userTags;

    @ApiModelProperty(value = "地区编码")
    private String areaCode;

    @ApiModelProperty(value = "中文地址")
    private String areaZh;

    // 故意不写 updateStrategy=IGNORED（门店/仓库那几张表写了）：sys_user 有三个「拿半截实体就 updateById」的
    // 写入方（个人中心保存 AuthServiceImpl#updateProfile、重置密码、生成二维码），IGNORED 会让它们把坐标刷成 NULL。
    // 代价是清空坐标保存不生效，跟这张表的 area_code 一个脾气（全局 field-strategy=not_empty）。
    // 下面的 address 详细地址同理，三列一起受这条约束。
    @ApiModelProperty(value = "经度(GCJ-02)，前端地图选点写入")
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)，前端地图选点写入")
    private BigDecimal latitude;

    // 和上面两列同一个脾气：不加 IGNORED，清空详细地址保存不生效
    @ApiModelProperty(value = "详细地址（街道、门牌号），地图选点时由逆地理自动回填")
    private String address;

    @ApiModelProperty(value = "状态")
    private Integer status;

    @ApiModelProperty(value = "ip地址")
    private String ip;

    @ApiModelProperty(value = "ip来源")
    private String ipLocation;

    @ApiModelProperty(value = "操作系统")
    private String os;

    @ApiModelProperty(value = "上次登录时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

    @ApiModelProperty(value = "浏览器")
    private String browser;

    @ApiModelProperty(value = "昵称")
    private String nickname;

    @ApiModelProperty(value = "头像")
    private String avatar;

    @ApiModelProperty(value = "背景图")
    private String backImage;

    @ApiModelProperty(value = "二维码")
    private String qrImg;

    @ApiModelProperty(value = "手机号")
    private String mobile;

    @ApiModelProperty(value = "邮箱")
    private String email;

    @ApiModelProperty(value = "个性签名")
    private String signature;

    @ApiModelProperty(value = "登录方式")
    private String loginType;

    @ApiModelProperty(value = "账号类型：0平台用户 1门店 2送货员 3消费者 4供应商 5ERP操作员（字典 account_type）")
    private Integer accountType;

    // 一个账号类型筛不出「这几档都要」的时候用它（如优惠券的「负责的运营」要平台+门店）；两条同时传是 AND，调用方只会给一条
    @ApiModelProperty(value = "账号类型列表（用户列表筛选用）")
    @TableField(exist = false)
    private List<Integer> accountTypes;

    @ApiModelProperty(value = "所属部门ID列表（用户列表筛选用）")
    @TableField(exist = false)
    private List<Long> deptIds;

    @ApiModelProperty(value = "用户名/昵称/账号关键字（用户列表筛选用）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "排除的部门ID（添加部门人员时过滤已在该部门下的人员）")
    @TableField(exist = false)
    private Long excludeDeptId;

    @ApiModelProperty(value = "用户ID列表（导出勾选用户时传入）")
    @TableField(exist = false)
    private List<Long> ids;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime updateTime;
}
