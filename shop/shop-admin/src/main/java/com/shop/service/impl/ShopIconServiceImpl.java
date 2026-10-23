package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopIcon;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopIconMapper;
import com.shop.service.ShopIconService;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopIconServiceImpl extends ServiceImpl<ShopIconMapper, ShopIcon> implements ShopIconService {

    /** 图标类型：1 SVG 2 图片URL 3 字体类名 */
    private static final int TYPE_MIN = 1;
    private static final int TYPE_MAX = 3;

    private final ShopCategoryMapper categoryMapper;

    @Override
    public IPage<ShopIcon> selectPage(ShopIcon query) {
        return baseMapper.selectIconPage(PageUtil.getPage(), query == null ? new ShopIcon() : query);
    }

    @Override
    public List<ShopIcon> listOptions(Long categoryId) {
        return list(new LambdaQueryWrapper<ShopIcon>()
                .eq(categoryId != null, ShopIcon::getCategoryId, categoryId)
                .eq(ShopIcon::getStatus, 1)
                .orderByAsc(ShopIcon::getSort)
                .orderByAsc(ShopIcon::getId));
    }

    @Override
    public void insert(ShopIcon icon) {
        validate(icon, null);
        if (icon.getStatus() == null) {
            icon.setStatus(1);
        }
        if (icon.getSort() == null) {
            icon.setSort(0);
        }
        if (icon.getIconType() == null) {
            icon.setIconType(1);
        }
        icon.setUserId(StpUtil.getLoginIdAsLong());
        try {
            save(icon);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_icon_code，服务层的前置查重（只查未删除的）看不到它
            throw new ServiceException("图标编码「" + icon.getIconCode() + "」已被占用，可能来自一条已删除的图标");
        }
    }

    @Override
    public void update(ShopIcon icon) {
        if (icon.getId() == null) {
            throw new ServiceException("图标ID不能为空");
        }
        ShopIcon exists = getById(icon.getId());
        if (exists == null) {
            throw new ServiceException("图标不存在");
        }
        // 全局 field-strategy=not_empty 会跳过空值字段，这里先把未传的字段用原值补齐再整体校验，
        // 否则只改状态之类的局部更新会因为 iconName/iconCode 为空而被判成非法。
        // 仅限 NOT NULL 列：category_id 可空，不回填，null 就表示清空关联
        if (StringUtils.isBlank(icon.getIconName())) {
            icon.setIconName(exists.getIconName());
        }
        if (StringUtils.isBlank(icon.getIconCode())) {
            icon.setIconCode(exists.getIconCode());
        }
        if (icon.getIconType() == null) {
            icon.setIconType(exists.getIconType());
        }
        if (StringUtils.isBlank(icon.getIconValue())) {
            icon.setIconValue(exists.getIconValue());
        }
        validate(icon, icon.getId());
        try {
            updateById(icon);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("图标编码「" + icon.getIconCode() + "」已被占用，可能来自一条已删除的图标");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    private void validate(ShopIcon icon, Long excludeId) {
        if (StringUtils.isBlank(icon.getIconName())) {
            throw new ServiceException("图标名称不能为空");
        }
        if (StringUtils.isBlank(icon.getIconCode())) {
            throw new ServiceException("图标编码不能为空");
        }
        if (icon.getIconType() == null || icon.getIconType() < TYPE_MIN || icon.getIconType() > TYPE_MAX) {
            throw new ServiceException("图标类型不合法");
        }
        if (StringUtils.isBlank(icon.getIconValue())) {
            throw new ServiceException("图标内容不能为空");
        }
        // category_id 可空，但填了就必须存在
        if (icon.getCategoryId() != null) {
            ShopCategory category = categoryMapper.selectById(icon.getCategoryId());
            if (category == null) {
                throw new ServiceException("所选类目不存在");
            }
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopIcon>()
                .eq(ShopIcon::getIconCode, icon.getIconCode())
                .ne(excludeId != null, ShopIcon::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("图标编码「" + icon.getIconCode() + "」已存在");
        }
    }
}
