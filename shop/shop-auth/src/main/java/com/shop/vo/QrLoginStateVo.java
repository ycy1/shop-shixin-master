package com.shop.vo;

import com.shop.dto.user.LoginUserInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 扫码登录状态
 *
 * <p>既作为接口响应返回给管理后台，也作为 Redis 里 {@code qr_login:} 的存储结构，
 * 两边共用一份定义，避免状态字段对不上。
 *
 * @author: ycy
 */
@Data
@ApiModel(value = "扫码登录状态")
public class QrLoginStateVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待扫码（二维码刚生成）
     */
    public static final String WAITING = "WAITING";

    /**
     * 已被 App 扫描，等待 App 端确认
     */
    public static final String SCANNED = "SCANNED";

    /**
     * App 已确认，浏览器可取走 token
     */
    public static final String CONFIRMED = "CONFIRMED";

    /**
     * 已在 App 端取消
     */
    public static final String CANCELED = "CANCELED";

    /**
     * 二维码已过期（或已被消费掉）。只作为轮询的响应值，不会写进 Redis——
     * Redis 里 key 直接消失就是过期。
     */
    public static final String EXPIRED = "EXPIRED";

    @ApiModelProperty(value = "状态：WAITING / SCANNED / CONFIRMED / CANCELED")
    private String state;

    @ApiModelProperty(value = "扫码人昵称（仅 SCANNED 有值，用于提示是谁在扫码）")
    private String nickname;

    @ApiModelProperty(value = "扫码人头像（仅 SCANNED 有值）")
    private String avatar;

    @ApiModelProperty(value = "登录用户信息（仅 CONFIRMED 有值，含 token）")
    private LoginUserInfo loginUserInfo;

    public static QrLoginStateVo waiting() {
        return of(WAITING, null, null, null);
    }

    public static QrLoginStateVo scanned(String nickname, String avatar) {
        return of(SCANNED, nickname, avatar, null);
    }

    public static QrLoginStateVo confirmed(LoginUserInfo loginUserInfo) {
        return of(CONFIRMED, null, null, loginUserInfo);
    }

    public static QrLoginStateVo canceled() {
        return of(CANCELED, null, null, null);
    }

    public static QrLoginStateVo expired() {
        return of(EXPIRED, null, null, null);
    }

    private static QrLoginStateVo of(String state, String nickname, String avatar, LoginUserInfo loginUserInfo) {
        QrLoginStateVo vo = new QrLoginStateVo();
        vo.setState(state);
        vo.setNickname(nickname);
        vo.setAvatar(avatar);
        vo.setLoginUserInfo(loginUserInfo);
        return vo;
    }
}
