package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopProductReadService;
import com.shop.dto.product.ShopProductWebQueryDto;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopProductSpec;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopProductSpecMapper;
import com.shop.utils.PageUtil;
import com.shop.utils.SpecUtils;
import com.shop.vo.product.ShopProductSpecWebVo;
import com.shop.vo.product.ShopProductWebVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xxj
 * @title ShopProductReadServiceImpl
 * @description 商品读接口（Web 前台），可见性判据在 ShopProductMapper.xml 的 SQL 里，
 * 这一层管三件事：分页、分类下级展开、「查不到」；详情那一条再多做两件 ——
 * 把 product_specs 那串 id 换成规格行，再把这个商品名下的 SKU 捞出来给规格选择器
 */
@Service
@RequiredArgsConstructor
public class ShopProductReadServiceImpl implements ShopProductReadService {

    private final ShopProductMapper shopProductMapper;

    private final ShopCategoryMapper shopCategoryMapper;

    private final ShopProductSpecMapper shopProductSpecMapper;

    private final ShopProductSkuMapper shopProductSkuMapper;

    @Override
    public IPage<ShopProductWebVo> page(ShopProductWebQueryDto query) {
        if (query == null) {
            query = new ShopProductWebQueryDto();
        }
        query.setCategoryIds(expandCategoryScope(query.getCategoryId()));
        return shopProductMapper.selectWebPage(PageUtil.getPage(), query);
    }

    /**
     * 分类命中带整棵下级：从这一级开始一层层往下收，返回含它自己的 id 集合。
     *
     * shop_category 只有 parent_id 和 level（1/2/3，没有 path 列），所以按层批量查、最多三趟，
     * 不上递归 CTE —— 那条 SQL 还要过 MP 分页插件的 count 改写，不值得。
     * 已见过的 id 不再入队，父指回子的脏数据也在这儿断掉，不会循环。
     * 禁用（status=0）的分类一并收：一个分类的商品能不能出现在前台，只看它自己上架了没有，
     * 不在这条链上顺手加第二条判据。
     */
    private List<Long> expandCategoryScope(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        List<Long> frontier = ids;
        while (!frontier.isEmpty()) {
            frontier = shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategory>()
                            .select(ShopCategory::getId)
                            .in(ShopCategory::getParentId, frontier))
                    .stream()
                    .map(ShopCategory::getId)
                    .filter(id -> !ids.contains(id))
                    .collect(Collectors.toList());
            ids.addAll(frontier);
        }
        return ids;
    }

    @Override
    public ShopProductWebVo detail(Long id) {
        ShopProductWebVo product = id == null ? null : shopProductMapper.selectWebById(id);
        if (product == null) {
            throw new ServiceException("商品不存在或已下架");
        }
        product.setSpecs(specsOf(product.getProductSpecs()));
        // 规格那几行另查一次：详情页要的是「哪个组合多少钱、还剩几件」，那是 SKU 表上的数，
        // 和上面 specs（这一件商品声明了哪几组规格值）不是一回事，列表那条不带
        product.setSkus(shopProductSkuMapper.selectWebSkusByProductId(product.getId()));
        return product;
    }

    /**
     * 把库里那一串规格 id 换成页面上印得出来的那几行。
     *
     * 只捞启用（status=1）的那几条：一条规格在后台被禁用后就再进不了商品表单的候选，前台也不该继续替它挂着；
     * 被删掉的由 MP 的逻辑删除谓词自动排除。所以「一列都没捞到」有两种来路（本来没挂、挂的全被禁/删了），
     * 都回空列表，页面据此整块不渲染，不给「商品规格」标题挂一张空表。
     * 按 id 升序返回，和后台那颗下拉（ShopProductSpecServiceImpl#listOptions）同一个排法，两处看到的是同一列。
     */
    private List<ShopProductSpecWebVo> specsOf(String productSpecs) {
        List<Long> ids = SpecUtils.parseSpecIds(productSpecs);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return shopProductSpecMapper.selectList(new LambdaQueryWrapper<ShopProductSpec>()
                        .select(ShopProductSpec::getId, ShopProductSpec::getSpecName,
                                ShopProductSpec::getSpecValue, ShopProductSpec::getSpecUnit)
                        .in(ShopProductSpec::getId, ids)
                        .eq(ShopProductSpec::getStatus, 1)
                        .orderByAsc(ShopProductSpec::getId))
                .stream()
                .map(item -> {
                    ShopProductSpecWebVo spec = new ShopProductSpecWebVo();
                    BeanUtils.copyProperties(item, spec);
                    return spec;
                })
                .collect(Collectors.toList());
    }
}
