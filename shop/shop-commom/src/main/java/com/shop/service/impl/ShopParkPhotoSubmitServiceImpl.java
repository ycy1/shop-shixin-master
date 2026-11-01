package com.shop.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.dto.park.ShopParkPhotoSubmitDto;
import com.shop.entity.ShopTrendyPark;
import com.shop.entity.ShopTrendyParkPhoto;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.ShopTrendyParkPhotoMapper;
import com.shop.service.ShopParkPhotoSubmitService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 投稿 = 把「谁、哪家园区、哪张图」这一行钉成待审核落到库里。
 *
 * 只有一处值得单独说一句：那个人数上限。这一条是对公网开的，一个账号刷起来能把后台的审核队列灌满，
 * 所以先数一下「这个人名下还没审的投稿有几张」，够 9 张就不再收 —— 取 9 是因为详情页卡片上一屏正好 6 格、
 * 审核页一页 10 行，一人一屏就看得完，不至于为了清队列去翻页。
 * 图本身能刷（先传文件再投稿，文件那一步全站同一个收文件的口在管），这一格只管别把队列灌满。
 */
@Service
@RequiredArgsConstructor
public class ShopParkPhotoSubmitServiceImpl implements ShopParkPhotoSubmitService {

    /** 园区状态：营业中（停业的收投稿没人审，前台也不会展示） */
    private static final int PARK_OPEN = 1;

    /** 一个账号名下「待审核」的投稿最多压几张 */
    private static final int MAX_PENDING_PER_USER = 9;

    private final ShopTrendyParkPhotoMapper photoMapper;

    private final ShopTrendyParkMapper parkMapper;

    @Override
    public ShopTrendyParkPhoto submit(ShopParkPhotoSubmitDto dto) {
        long userId = currentUserId();
        if (dto == null) {
            throw new ServiceException("请先选好要上传的图片");
        }
        if (dto.getParkId() == null) {
            throw new ServiceException("请选择要投稿的园区");
        }
        ShopTrendyPark park = parkMapper.selectById(dto.getParkId());
        if (park == null || !Integer.valueOf(PARK_OPEN).equals(park.getStatus())) {
            throw new ServiceException("园区不存在或暂停营业");
        }
        if (StringUtils.isBlank(dto.getImageUrl())) {
            throw new ServiceException("请先选好要上传的图片");
        }
        String imageUrl = dto.getImageUrl().trim();
        if (imageUrl.length() > ShopTrendyParkPhoto.IMAGE_URL_MAX) {
            throw new ServiceException("图片地址过长（最多 " + ShopTrendyParkPhoto.IMAGE_URL_MAX + " 个字符）");
        }
        String description = StringUtils.trimToNull(dto.getDescription());
        if (description != null && description.length() > ShopTrendyParkPhoto.DESCRIPTION_MAX) {
            throw new ServiceException("照片描述最多 " + ShopTrendyParkPhoto.DESCRIPTION_MAX + " 个字");
        }

        Long pending = photoMapper.selectCount(new LambdaQueryWrapper<ShopTrendyParkPhoto>()
                .eq(ShopTrendyParkPhoto::getUserId, userId)
                .eq(ShopTrendyParkPhoto::getSourceType, ShopTrendyParkPhoto.SOURCE_USER)
                .eq(ShopTrendyParkPhoto::getStatus, ShopTrendyParkPhoto.STATUS_PENDING));
        if (pending != null && pending >= MAX_PENDING_PER_USER) {
            throw new ServiceException("你还有 " + pending + " 张投稿在等审核，先等审核结果再传");
        }

        ShopTrendyParkPhoto row = new ShopTrendyParkPhoto();
        row.setParkId(park.getId());
        // 所属 IP 跟着园区走（口径同后台那一路：两处各填一份迟早对不上）
        row.setIpId(park.getIpId());
        row.setSourceType(ShopTrendyParkPhoto.SOURCE_USER);
        row.setUserId(userId);
        row.setImageUrl(imageUrl);
        // 投稿不带标签：用户自己写的词不进词表，运营审核时再补（那一列 NOT NULL 语义，给 "[]"）
        row.setTags("[]");
        row.setStatus(ShopTrendyParkPhoto.STATUS_PENDING);
        row.setIsFeatured(0);
        row.setSort(0);
        row.setDescription(description);
        photoMapper.insert(row);
        return row;
    }

    /** 谁投的只能由登录态决定：参数能点名别人就等于替别人投稿 */
    private long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            throw new ServiceException("请先登录后再上传照片");
        }
    }
}
