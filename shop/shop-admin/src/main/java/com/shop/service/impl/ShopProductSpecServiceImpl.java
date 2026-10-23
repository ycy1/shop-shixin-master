package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopProductSpec;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopProductSpecMapper;
import com.shop.service.ShopProductSpecService;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopProductSpecServiceImpl extends ServiceImpl<ShopProductSpecMapper, ShopProductSpec> implements ShopProductSpecService {

    private final ShopCategoryMapper categoryMapper;

    @Override
    public IPage<ShopProductSpec> selectPage(ShopProductSpec query) {
        if (query == null) {
            query = new ShopProductSpec();
        }
        return baseMapper.selectSpecPage(PageUtil.getPage(), query);
    }

    @Override
    public List<ShopProductSpec> listOptions(Long categoryId) {
        List<Long> categoryIds = categoryId == null ? null : selfAndAncestorIds(categoryId);
        return list(new LambdaQueryWrapper<ShopProductSpec>()
                // 口径同品牌下拉：规格常常只建到上级类目，商品选的却是叶子类目，所以要把各级父类一起算进来；
                // 不限类目（category_id 为空）的规格对任何分类都是候选，所以是"这些分类 OR 不限类目"而不是单纯 in
                .and(categoryIds != null, w -> w.in(ShopProductSpec::getCategoryId, categoryIds)
                        .or().isNull(ShopProductSpec::getCategoryId))
                .eq(ShopProductSpec::getStatus, 1)
                .orderByAsc(ShopProductSpec::getId));
    }

    /**
     * 商品表单的规格候选要连各级上级类目一起算（实现与 ShopBrandServiceImpl 的同名方法一致）。
     * 传入的 id 一定在结果里（类目恰好被删掉时上级链走不动，也不能因此放宽成全部规格），
     * 所以拼出来的 IN 列表不会为空。
     */
    private List<Long> selfAndAncestorIds(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long cursor = categoryId;
        // parent_id 为 0 表示顶级；visited 挡住脏数据里的父子环
        while (cursor != null && cursor != 0 && visited.add(cursor)) {
            ids.add(cursor);
            ShopCategory category = categoryMapper.selectById(cursor);
            cursor = category == null ? null : category.getParentId();
        }
        return ids;
    }

    @Override
    public void insert(ShopProductSpec spec) {
        validate(spec, null);
        if (spec.getStatus() == null) {
            spec.setStatus(1);
        }
        spec.setUserId(StpUtil.getLoginIdAsLong());
        save(spec);
    }

    @Override
    public void update(ShopProductSpec spec) {
        if (spec.getId() == null) {
            throw new ServiceException("规格ID不能为空");
        }
        ShopProductSpec exists = getById(spec.getId());
        if (exists == null) {
            throw new ServiceException("规格不存在");
        }
        // 名称沿用旧值（表单不会清空它），类目不沿用 —— categoryId 是 IGNORED，请求体里没带就是明确要清成"不限类目"
        if (StringUtils.isBlank(spec.getSpecName())) {
            spec.setSpecName(exists.getSpecName());
        }
        validate(spec, spec.getId());
        updateById(spec);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    private void validate(ShopProductSpec spec, Long excludeId) {
        if (StringUtils.isBlank(spec.getSpecName())) {
            throw new ServiceException("规格名称不能为空");
        }
        // 类目可空（空 = 不限类目），但填了就必须存在，口径同品牌
        ShopCategory category = null;
        if (spec.getCategoryId() != null) {
            category = categoryMapper.selectById(spec.getCategoryId());
            if (category == null) {
                throw new ServiceException("所选类目不存在");
            }
        }
        // 同一类目下「规格名 + 规格值」不允许重复，例如"颜色-红色"只能有一条。
        // 类目为空那一路必须走 IS NULL：eq 传 null 会拼出 category_id = NULL，条件永远不成立，查重会静默失效
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopProductSpec>()
                .eq(spec.getCategoryId() != null, ShopProductSpec::getCategoryId, spec.getCategoryId())
                .isNull(spec.getCategoryId() == null, ShopProductSpec::getCategoryId)
                .eq(ShopProductSpec::getSpecName, spec.getSpecName())
                .eq(StringUtils.isNotBlank(spec.getSpecValue()), ShopProductSpec::getSpecValue, spec.getSpecValue())
                .isNull(StringUtils.isBlank(spec.getSpecValue()), ShopProductSpec::getSpecValue)
                .ne(excludeId != null, ShopProductSpec::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException((category == null ? "不限类目" : "类目「" + category.getCategoryName() + "」")
                    + "下已存在规格「" + spec.getSpecName()
                    + (StringUtils.isBlank(spec.getSpecValue()) ? "" : "-" + spec.getSpecValue()) + "」");
        }
    }
}
