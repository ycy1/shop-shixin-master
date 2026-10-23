package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSpec;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSpecMapper;
import com.shop.service.ShopCategoryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopCategoryServiceImpl extends ServiceImpl<ShopCategoryMapper, ShopCategory> implements ShopCategoryService {

    /**
     * 类目最大层级，对应 shop_category.level 列注释的 1/2/3
     */
    private static final int MAX_LEVEL = 3;

    private final ShopProductMapper productMapper;

    private final ShopProductSpecMapper specMapper;

    @Override
    public List<ShopCategory> listCategoryTree(ShopCategory query) {
        if (query == null) {
            query = new ShopCategory();
        }
        return buildCategoryTree(baseMapper.selectCategoryList(query));
    }

    /**
     * 根据扁平类目列表构建树形结构（根节点：无父节点或父节点不在当前结果集中）
     */
    private List<ShopCategory> buildCategoryTree(List<ShopCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<ShopCategory>> childrenMap = categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(ShopCategory::getParentId));
        categories.forEach(c -> c.setChildren(childrenMap.getOrDefault(c.getId(), Collections.emptyList())));
        Set<Long> keptIds = categories.stream().map(ShopCategory::getId).collect(Collectors.toSet());
        return categories.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0 || !keptIds.contains(c.getParentId()))
                .collect(Collectors.toList());
    }

    @Override
    public void addCategory(ShopCategory category) {
        if (StringUtils.isBlank(category.getCategoryName())) {
            throw new ServiceException("类目名称不能为空");
        }
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        int level = 1;
        if (category.getParentId() != 0) {
            ShopCategory parent = getById(category.getParentId());
            if (parent == null) {
                throw new ServiceException("父类目不存在");
            }
            level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        }
        if (level > MAX_LEVEL) {
            throw new ServiceException("类目最多支持 " + MAX_LEVEL + " 级");
        }
        checkNameDuplicated(category.getParentId(), category.getCategoryName(), null);
        category.setLevel(level);
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }
        category.setUserId(StpUtil.getLoginIdAsLong());
        save(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(ShopCategory category) {
        if (category.getId() == null) {
            throw new ServiceException("类目ID不能为空");
        }
        ShopCategory exists = getById(category.getId());
        if (exists == null) {
            throw new ServiceException("类目不存在");
        }
        Long parentId = category.getParentId() == null ? exists.getParentId() : category.getParentId();
        if (Objects.equals(parentId, category.getId())) {
            throw new ServiceException("父类目不能是自己");
        }

        int level = 1;
        if (parentId != null && parentId != 0) {
            ShopCategory parent = getById(parentId);
            if (parent == null) {
                throw new ServiceException("父类目不存在");
            }
            // 沿新父节点向上回溯，若途中遇到自己说明形成了循环引用
            Long cursor = parentId;
            Set<Long> visited = new HashSet<>();
            while (cursor != null && cursor != 0) {
                if (cursor.equals(category.getId())) {
                    throw new ServiceException("父类目不能是其子类目");
                }
                if (!visited.add(cursor)) {
                    break;
                }
                ShopCategory node = getById(cursor);
                if (node == null) {
                    break;
                }
                cursor = node.getParentId();
            }
            level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        }

        // 自身层级 + 子树深度不能超过上限，否则移动后子孙类目会越界
        int depth = subtreeDepth(category.getId());
        if (level + depth - 1 > MAX_LEVEL) {
            throw new ServiceException("该类目下还有 " + depth + " 层子类目，移动到第 " + level
                    + " 级会超出最多 " + MAX_LEVEL + " 级的限制");
        }

        String categoryName = StringUtils.isBlank(category.getCategoryName())
                ? exists.getCategoryName() : category.getCategoryName();
        checkNameDuplicated(parentId, categoryName, category.getId());

        category.setParentId(parentId);
        category.setLevel(level);
        updateById(category);

        // 只有层级真的变了才需要下推，否则会把整棵子树的 update_time 都刷一遍
        if (!Objects.equals(exists.getLevel(), level)) {
            refreshChildrenLevel(category.getId(), level);
        }
    }

    /**
     * 递归刷新子孙类目的层级
     */
    private void refreshChildrenLevel(Long parentId, int parentLevel) {
        List<ShopCategory> children = list(new LambdaQueryWrapper<ShopCategory>()
                .eq(ShopCategory::getParentId, parentId));
        for (ShopCategory child : children) {
            ShopCategory update = new ShopCategory();
            update.setId(child.getId());
            update.setLevel(parentLevel + 1);
            updateById(update);
            refreshChildrenLevel(child.getId(), parentLevel + 1);
        }
    }

    /**
     * 子树深度，自身算 1 层
     */
    private int subtreeDepth(Long id) {
        List<ShopCategory> children = list(new LambdaQueryWrapper<ShopCategory>()
                .eq(ShopCategory::getParentId, id));
        if (children.isEmpty()) {
            return 1;
        }
        int max = 0;
        for (ShopCategory child : children) {
            max = Math.max(max, subtreeDepth(child.getId()));
        }
        return max + 1;
    }

    /**
     * 同一父类目下不允许重名
     */
    private void checkNameDuplicated(Long parentId, String categoryName, Long excludeId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopCategory>()
                .eq(ShopCategory::getParentId, parentId)
                .eq(ShopCategory::getCategoryName, categoryName)
                .ne(excludeId != null, ShopCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("同级下已存在名为「" + categoryName + "」的类目");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            ShopCategory category = getById(id);
            if (category == null) {
                continue;
            }
            if (count(new LambdaQueryWrapper<ShopCategory>().eq(ShopCategory::getParentId, id)) > 0) {
                throw new ServiceException("类目「" + category.getCategoryName() + "」下存在子类目，不能删除");
            }
            Long productCount = productMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                    .eq(ShopProduct::getCategoryId, id));
            if (productCount != null && productCount > 0) {
                throw new ServiceException("类目「" + category.getCategoryName() + "」下存在 " + productCount + " 个商品，不能删除");
            }
            Long specCount = specMapper.selectCount(new LambdaQueryWrapper<ShopProductSpec>()
                    .eq(ShopProductSpec::getCategoryId, id));
            if (specCount != null && specCount > 0) {
                throw new ServiceException("类目「" + category.getCategoryName() + "」下存在 " + specCount + " 条规格，不能删除");
            }
        }
        // shop_category 有 is_deleted 列，实体继承 BaseEntity 后 MP 自动把这里变成 UPDATE 软删除；
        // 上面几条占用校验也自动带上 is_deleted = 0，已删除的行不会挡着删除也不会挡着重名
        removeBatchByIds(ids);
    }
}
