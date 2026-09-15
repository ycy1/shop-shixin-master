package com.shop.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 扫码登录二维码
 *
 * @author: ycy
 */
@Data
@ApiModel(value = "扫码登录二维码")
public class QrLoginVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "登录码，同时是二维码内容，也是后续轮询/确认的凭据")
    private String code;

    @ApiModelProperty(value = "二维码图片（data:image/png;base64,... 可直接用作 img 的 src）")
    private String qrCodeImage;

    @ApiModelProperty(value = "有效期（秒）")
    private Integer expireSeconds;

    public static QrLoginVo of(String code, String qrCodeImage, Integer expireSeconds) {
        QrLoginVo vo = new QrLoginVo();
        vo.setCode(code);
        vo.setQrCodeImage(qrCodeImage);
        vo.setExpireSeconds(expireSeconds);
        return vo;
    }
}
