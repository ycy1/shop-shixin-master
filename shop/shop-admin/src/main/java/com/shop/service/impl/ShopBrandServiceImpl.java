package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopBrand;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopStore;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.service.ShopBrandService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopBrandServiceImpl extends ServiceImpl<ShopBrandMapper, ShopBrand> implements ShopBrandService {

    private final ShopProductMapper productMapper;

    private final ShopCategoryMapper categoryMapper;

    /** 只用来看有没有门店还绑着这个品牌；注入 mapper 而不是 service，避免两边互相依赖 */
    private final ShopStoreMapper storeMapper;

    @Override
    public IPage<ShopBrand> selectPage(ShopBrand query) {
        if (query == null) {
            query = new ShopBrand();
        }
        // 数据权限按账号类型收窄（口径同门店/商品/配送单列表）：只有账号类型=门店的只看自己创建的品牌，其它类型看全部。
        // 用局部变量而不是 query.getUserId()：那是实体列，会随 ?userId= 绑进来，等于让调用方自己定能看哪些行
        Long currentUserId = StpUtil.getLoginIdAsLong();
        boolean store = LoginUserUtils.isStore();
        LambdaQueryWrapper<ShopBrand> wrapper = new LambdaQueryWrapper<ShopBrand>()
                .like(StringUtils.isNotBlank(query.getBrandName()), ShopBrand::getBrandName, query.getBrandName())
                .eq(query.getStatus() != null, ShopBrand::getStatus, query.getStatus())
                .eq(query.getCategoryId() != null, ShopBrand::getCategoryId, query.getCategoryId())
                .eq(store, ShopBrand::getUserId, currentUserId)
                // 启用在前（status：1启用 0禁用），同状态内仍按 sort、id
                .orderByDesc(ShopBrand::getStatus)
                .orderByAsc(ShopBrand::getSort)
                .orderByDesc(ShopBrand::getId);
        // 用 BaseMapper 的分页而不是自定义 XML，全局逻辑删除条件才会自动拼上
        return page(PageUtil.getPage(), wrapper);
    }

    @Override
    public List<ShopBrand> listOptions(Long categoryId) {
        List<Long> categoryIds = categoryId == null ? null : selfAndAncestorIds(categoryId);
        return list(new LambdaQueryWrapper<ShopBrand>()
                .eq(ShopBrand::getStatus, 1)
                // 未分类的品牌对任何商品分类都是候选，所以是"这些分类 OR 未分类"而不是单纯 in
                .and(categoryIds != null, w -> w.in(ShopBrand::getCategoryId, categoryIds)
                        .or().isNull(ShopBrand::getCategoryId))
                .orderByAsc(ShopBrand::getSort)
                .orderByAsc(ShopBrand::getId));
    }

    /**
     * 品牌大多只挂到一级类目，商品选的却是叶子类目，所以候选分类要连各级上级一起算。
     * 传入的 id 一定在结果里（类目恰好被删掉时上级链走不动，也不能因此放宽成全部品牌），
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
    public void insert(ShopBrand brand) {
        validate(brand, null);
        normalizeSpokesperson(brand);
        if (brand.getStatus() == null) {
            brand.setStatus(1);
        }
        if (brand.getSort() == null) {
            brand.setSort(0);
        }
        brand.setUserId(StpUtil.getLoginIdAsLong());
        try {
            save(brand);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_brand_name，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("品牌「" + brand.getBrandName() + "」已被占用，可能来自一个已删除的品牌");
        }
    }

    @Override
    public void update(ShopBrand brand) {
        if (brand.getId() == null) {
            throw new ServiceException("品牌ID不能为空");
        }
        ShopBrand exists = getById(brand.getId());
        if (exists == null) {
            throw new ServiceException("品牌不存在");
        }
        // 判库里那行的归属，不判请求体：否则把 userId 一起改掉就自己把自己转出去了
        assertWritable(Collections.singletonList(exists));
        validate(brand, brand.getId());
        normalizeSpokesperson(brand);
        try {
            updateById(brand);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("品牌「" + brand.getBrandName() + "」已被占用，可能来自一个已删除的品牌");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        assertWritable(listByIds(ids));
        // selectCount 会自动过滤已删除商品，所以下架/删除的商品不会拦住删除品牌
        Long used = productMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                .in(ShopProduct::getBrandId, ids));
        if (used != null && used > 0) {
            throw new ServiceException("该品牌下还有 " + used + " 个商品，请先在商品里取消关联或删除商品");
        }
        // 门店绑了这个品牌的也要拦（2026-10-05 加的 brand_id）：这一格删品牌不会自动清掉，
        // 放过去就留下一家挂着不存在品牌的门店 —— 门店列表那一列显示 '-'、表单下拉里也选不回来
        Long storeUsed = storeMapper.selectCount(new LambdaQueryWrapper<ShopStore>()
                .in(ShopStore::getBrandId, ids));
        if (storeUsed != null && storeUsed > 0) {
            throw new ServiceException("该品牌已绑定 " + storeUsed + " 家门店，请先在门店里取消绑定");
        }
        removeByIds(ids);
    }

    /**
     * 写操作的归属校验：账号类型=门店的只能动自己创建的品牌，口径同列表的 user_id 过滤
     * （老数据 user_id 是 NULL，所以门店账号本来就看不到、这里也一律改不动）。
     */
    private void assertWritable(List<ShopBrand> brands) {
        if (!LoginUserUtils.isStore()) {
            return;
        }
        Long userId = StpUtil.getLoginIdAsLong();
        for (ShopBrand brand : brands) {
            if (!userId.equals(brand.getUserId())) {
                throw new ServiceException("品牌「" + brand.getBrandName() + "」不是你创建的，无权修改或删除");
            }
        }
    }

    /**
     * brand_spokesperson 是 MySQL json 列，空字符串不是合法 JSON，写进去会直接报错，
     * 统一归一成 "[]"（和商品的 images 同一处理）。
     */
    private void normalizeSpokesperson(ShopBrand brand) {
        if (StringUtils.isBlank(brand.getBrandSpokesperson())) {
            brand.setBrandSpokesperson("[]");
        }
    }

    private void validate(ShopBrand brand, Long excludeId) {
        if (StringUtils.isBlank(brand.getBrandName())) {
            throw new ServiceException("品牌名称不能为空");
        }
        // category_id 可空，但填了就必须存在
        if (brand.getCategoryId() != null && categoryMapper.selectById(brand.getCategoryId()) == null) {
            throw new ServiceException("所选分类不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopBrand>()
                .eq(ShopBrand::getBrandName, brand.getBrandName())
                .ne(excludeId != null, ShopBrand::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("品牌「" + brand.getBrandName() + "」已存在");
        }
    }
}
