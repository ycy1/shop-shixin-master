package com.shop.utils;

import cn.hutool.extra.qrcode.QrCodeUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 二维码工具类
 *
 * @author: quequnlong
 * @date: 2026/8/21
 * @description:
 */
public class QrCodeUtils {

    /**
     * 生成二维码 PNG 字节
     *
     * @param content 二维码内容（通常是 URL）
     * @param size    图片边长（像素）
     * @return PNG 图片字节
     * @throws IOException 生成失败
     */
    public static byte[] generatePng(String content, int size) throws IOException {
        BufferedImage image = QrCodeUtil.generate(content, size, size);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
