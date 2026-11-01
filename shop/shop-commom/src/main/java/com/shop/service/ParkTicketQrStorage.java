package com.shop.service;

/**
 * 票面二维码图片的存放口子（主题公园）
 *
 * 出票那一步在 shop-commom（后台代客出票和支付回调要共用同一条出票路），
 * 但"把图片存起来、拿回一个可访问的 URL"这件事的本事在 shop-file（x-file-storage 那套依赖和实现都在那儿），
 * commom 够不着 —— 所以这里只留一个口子，由 shop-file 去接。
 *
 * 这样拆还有一个好处：将来要换成"存对象存储"或"直接给一段带签名的核销链接"，改的是实现那一头，
 * 出票那一条不用跟着动。
 */
public interface ParkTicketQrStorage {

    /**
     * 把一张 PNG 存起来
     *
     * @param pngBytes PNG 字节
     * @param fileName 落库的文件名（含 .png；调用方给的是这一张票的票码，一眼能对上是哪张票的票面）
     * @return 可访问的 URL（写进 shop_trendy_park_user_ticket.qr_img 的那一格）
     */
    String uploadPng(byte[] pngBytes, String fileName);
}
