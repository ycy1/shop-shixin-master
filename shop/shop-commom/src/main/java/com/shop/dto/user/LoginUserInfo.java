package com.shop.dto.user;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "登录用户信息")
public class LoginUserInfo implements Serializable {

    /**
     * 用户ID。
     *
     * 这一格钉 @JsonTypeInfo(use = NONE) 不是装饰：这份快照是整份塞进 Redis 会话的，
     * 而那份序列化（sa-token 的 GenericJackson2JsonRedisSerializer）开着 Jackson 的默认类型标注。
     * Jackson 只对 JSON 的"天然类型"（String / Integer / Double / Boolean）免标类型，Long 不在其中 ——
     * 于是这一列由 Integer 改成 Long 之后，写入变成了 ["java.lang.Long",5]、读取也照着数组去找，
     * 库里那一版还是改类型之前写下的裸数字 5，一读就抛 MismatchedInputException（登录直接进不来）。
     * 显式关掉这一格的多态标注，读写就都是裸数字，改类型前后同一个格式，老会话照读。
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @ApiModelProperty(value = "用户ID")
    private Long id;

    @ApiModelProperty(value = "用户名")
    private String username;

    @ApiModelProperty(value = "微信openid")
    private String openid;

    @ApiModelProperty(value = "昵称")
    private String nickname;

    @ApiModelProperty(value = "头像")
    private String avatar;

    @ApiModelProperty(value = "性别")
    private Integer sex;

    @ApiModelProperty(value = "账号类型：0平台用户 1门店 2送货员 3消费者 4供应商 5ERP操作员（字典 account_type）")
    private Integer accountType;

    @ApiModelProperty(value = "token")
    private String token;

    @ApiModelProperty(value = "权限列表")
    private List<String> permissions;

    @ApiModelProperty(value = "角色信息")
    private List<String> roles;
}
