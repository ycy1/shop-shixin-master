package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.product.ShopProductSkuQueryDto;
import com.shop.dto.product.ShopProductSkuSaveDto;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopProductSpec;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopProductSpecMapper;
import com.shop.service.ShopProductSkuService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.utils.SpecUtils;
import com.shop.vo.product.ShopProductSkuVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品 SKU 的读写。这一层比商品那层多三件事：
 * 一是 spec_ids 存前要归一化（升序去重），并照着它从规格字典拼出 spec_text —— 页面不给填那一格文本；
 * 二是每条 SKU 都挂在某件商品名下，所以归属校验、重复校验都是先看父商品再看这一行；
 * 用到商品还没声明的规格时，顺手把那一组补进商品的 product_specs（见 declareOnProduct）；
 * 三是规格图收的是整组（sku_images），sku_image 由这一组派生，见 normalizeImages。
 */
@Service
@RequiredArgsConstructor
public class ShopProductSkuServiceImpl extends ServiceImpl<ShopProductSkuMapper, ShopProductSku>
        implements ShopProductSkuService {

    private final ShopProductMapper productMapper;

    private final ShopProductSpecMapper specMapper;

    @Override
    public IPage<ShopProductSkuVo> selectPage(ShopProductSkuQueryDto query) {
        if (query == null) {
            query = new ShopProductSkuQueryDto();
        }
        // 数据权限跟着商品那一侧走（口径同商品列表）：账号类型=门店的只看自己商品的规格
        query.setCurrentUserId(LoginUserUtils.isStore() ? StpUtil.getLoginIdAsLong() : null);
        return baseMapper.selectSkuPage(PageUtil.getPage(), query);
    }

    @Override
    public List<ShopProductSkuVo> selectByProductId(Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }
        // 这一条不判归属：能看到商品页就能看它的规格列表，改与删才判（和商品详情同一个口径）
        return baseMapper.selectByProductId(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopProductSkuSaveDto form) {
        ShopProductSku sku = toEntity(form);
        ShopProduct product = requireProduct(sku.getProductId());
        fillFromSpecs(sku, product);
        normalizeImages(sku);
        if (StringUtils.isBlank(sku.getSkuCode())) {
            sku.setSkuCode(GeneratorNoUtils.getSkuNo());
        }
        validate(sku, product, null);
        if (sku.getSalesCount() == null) {
            sku.setSalesCount(0);
        }
        if (sku.getSort() == null) {
            // 新加的规格排在这个商品已有规格后面：给 0 会插到默认那条前面，运营刚录完就找不到它
            sku.setSort(nextSort(sku.getProductId()));
        }
        save(sku);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopProductSkuSaveDto form) {
        if (form.getId() == null) {
            throw new ServiceException("SKU ID不能为空");
        }
        ShopProductSku exists = getById(form.getId());
        if (exists == null) {
            throw new ServiceException("商品规格不存在");
        }
        ShopProduct product = requireProduct(exists.getProductId());
        ShopProductSku sku = toEntity(form);
        // 挂在哪个商品下改不动：请求体里那格不算数，一律沿用库里的（库存/购物车/单据都是按这一行认商品的）
        sku.setProductId(product.getId());
        sku.setSkuCode(StringUtils.isBlank(sku.getSkuCode()) ? exists.getSkuCode() : sku.getSkuCode());
        fillFromSpecs(sku, product);
        normalizeImages(sku);
        validate(sku, product, sku.getId());
        // 销量这一列 DTO 里就没有（只有支付回调写），拷过来自然是 null，updateById 按 not_empty 会跳过它
        updateById(sku);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<ShopProductSku> skus = listByIds(ids);
        if (skus.isEmpty()) {
            return;
        }
        for (ShopProductSku sku : skus) {
            // 归属看父商品，requireProduct 里就带着这条判据
            requireProduct(sku.getProductId());
        }
        // 最后一条不给删：删空了这件商品就悬空了 —— 库存、购物车、单据明细都要往一个 sku_id 上记账，
        // 一件商品连一条规格都没有，它在交易链上是挂不住的
        for (Long productId : skus.stream().map(ShopProductSku::getProductId).distinct().collect(Collectors.toList())) {
            Long remain = baseMapper.selectCount(new LambdaQueryWrapper<ShopProductSku>()
                    .eq(ShopProductSku::getProductId, productId)
                    .notIn(ShopProductSku::getId, ids));
            if (remain == null || remain == 0) {
                ShopProduct product = productMapper.selectById(productId);
                throw new ServiceException("商品「" + (product == null ? productId : product.getProductName())
                        + "」名下只剩这一条规格，删掉这件商品就没有可下单的规格了，请直接删商品");
            }
        }
        removeByIds(ids);
    }

    /**
     * 父商品必须存在，且当前账号动得了它：SKU 表上没有 user_id，归属就是它所属商品的归属。
     */
    private ShopProduct requireProduct(Long productId) {
        if (productId == null) {
            throw new ServiceException("请选择所属商品");
        }
        ShopProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("所属商品不存在");
        }
        if (LoginUserUtils.isStore() && StpUtil.getLoginIdAsLong()!= product.getUserId()) {
            throw new ServiceException("商品「" + product.getProductName() + "」不是你创建的，无权添加规格");
        }
        return product;
    }

    /**
     * 按 spec_ids 校验并回填这一条 SKU 的规格信息。
     *
     * 存的是升序去重后的那一串（{红,L} 和 {L,红} 是同一条），文本从字典现拼，页面传什么都不认。
     * 每个 id 还得在这件商品「声明了的那几组规格」（shop_product.product_specs）里 —— 前台详情页是按
     * 那一串列候选的，SKU 挂一个商品没声明的规格值，页面上就点不到它。
     * 不声明的情况现在不再拦：SKU 弹窗里那颗「新增规格」建完就直接勾上提交，页面没有第二次「提交商品」
     * 的动作可以做，所以这一趟顺手把缺的补进商品那一列（见 declareOnProduct）。
     */
    private void fillFromSpecs(ShopProductSku sku, ShopProduct product) {
        List<Long> ids = SpecUtils.parseSpecIds(sku.getSpecIds());
        sku.setSpecIds(SpecUtils.normalizeSpecIds(sku.getSpecIds()));
        if (ids.isEmpty()) {
            sku.setSpecText("");
            return;
        }
        List<ShopProductSpec> specs = specMapper.selectList(new LambdaQueryWrapper<ShopProductSpec>()
                .select(ShopProductSpec::getId, ShopProductSpec::getSpecName,
                        ShopProductSpec::getSpecValue, ShopProductSpec::getSpecUnit)
                .in(ShopProductSpec::getId, ids)
                .orderByAsc(ShopProductSpec::getId));
        if (specs.size() != ids.size()) {
            throw new ServiceException("有规格值已被删除，请重新选择");
        }
        for (ShopProductSpec spec : specs) {
            if (spec.getStatus() != null && spec.getStatus() != 1) {
                throw new ServiceException("规格「" + spec.getSpecName() + "」已禁用，不能再开新的规格组合");
            }
        }
        List<Long> declared = SpecUtils.parseSpecIds(product.getProductSpecs());
        List<Long> outside = ids.stream().filter(id -> !declared.contains(id)).collect(Collectors.toList());
        if (!outside.isEmpty()) {
            declareOnProduct(product, outside);
        }
        sku.setSpecText(SpecUtils.buildSpecText(specs));
    }

    /**
     * 把这条 SKU 用到、但商品还没声明的规格值补进 shop_product.product_specs。
     *
     * 只更新这一列，不走 updateById：ShopProduct 上 brand_id/store_id/images 钉的是 IGNORED
     * （可空列，取消品牌就是要把 null 写进去），而这里手上只有一串 id，用实体整条更新会把那三列刷成 NULL。
     * 更新完把新值也写回入参这个实体 —— insert/update 后面还要拿 product 判重、报错文案，
     * 同一个请求里读到的必须是自己刚改过的那一份。
     * 和这条 SKU 的保存在同一个事务里（fillFromSpecs 的两个调用方都带 @Transactional），
     * SKU 校验没过整单回滚，不会出现「商品多声明了一组规格、却一条对应的 SKU 都没有」。
     */
    private void declareOnProduct(ShopProduct product, List<Long> extraIds) {
        List<Long> merged = new ArrayList<>(SpecUtils.parseSpecIds(product.getProductSpecs()));
        merged.addAll(extraIds);
        String specIds = SpecUtils.normalizeSpecIds(
                merged.stream().map(String::valueOf).collect(Collectors.joining(",")));
        productMapper.update(null, new LambdaUpdateWrapper<ShopProduct>()
                .eq(ShopProduct::getId, product.getId())
                .set(ShopProduct::getProductSpecs, specIds));
        product.setProductSpecs(specIds);
    }

    /**
     * 规格图 + 详情图这两组落库前的处理，口径同商品那边（ShopProductServiceImpl#normalizeImages）。
     *
     * 一是这两列都是 MySQL json，空字符串不是合法 JSON、写进去直接失败，而全局
     * field-strategy=not_empty 又会跳过空串让「把图全清掉」落不了库，所以一律归成 "[]"。
     * 二是把规格图的组首那张抄进 sku_image：购物车、销售单、下单快照读的都是那一列
     * （COALESCE(sku_image, main_image)），让它始终等于这一组的第一张，那些下游一行都不用动，
     * 「这一档没配图」也还是 sku_image 为 NULL 这一个说法。详情图不派生任何列 —— 它是前台详情页
     * 「商品详情」那一块从上往下整组铺开的一列长图，不参与轮播、也没有封面。
     * 存的不是合法 JSON 时整组当没图，不把脏数据写进 json 列让这条保存直接报错。
     */
    private void normalizeImages(ShopProductSku sku) {
        sku.setDetailImages(normalizeImageGroup(sku.getDetailImages()));
        JSONArray group = parseImageGroup(sku.getSkuImages());
        sku.setSkuImages(group == null ? "[]" : sku.getSkuImages());
        sku.setSkuImage(firstImageUrl(group));
    }

    /** 那一串拆成数组；空串、脏数据一律当「这一组没图」，返回 null */
    private JSONArray parseImageGroup(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return JSONUtil.parseArray(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeImageGroup(String raw) {
        return parseImageGroup(raw) == null ? "[]" : raw;
    }

    private String firstImageUrl(JSONArray group) {
        if (group == null) {
            return null;
        }
        // JSONArray 本身就是个 List，直接遍历（元素类型各 hutool 版本不一致，不依赖 stream）
        for (Object item : group) {
            String url = item instanceof JSONObject ? ((JSONObject) item).getStr("url") : null;
            if (StringUtils.isNotBlank(url)) {
                return url;
            }
        }
        return null;
    }

    private ShopProductSku toEntity(ShopProductSkuSaveDto form) {
        ShopProductSku sku = new ShopProductSku();
        BeanUtils.copyProperties(form, sku);
        return sku;
    }

    private void validate(ShopProductSku sku, ShopProduct product, Long excludeId) {
        if (sku.getProductPrice() == null) {
            throw new ServiceException("请填写单价");
        }
        if (StringUtils.isBlank(sku.getSkuCode())) {
            throw new ServiceException("SKU编码不能为空");
        }
        Long codeCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopProductSku>()
                .eq(ShopProductSku::getSkuCode, sku.getSkuCode())
                .ne(excludeId != null, ShopProductSku::getId, excludeId));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException("SKU编码「" + sku.getSkuCode() + "」已存在");
        }
        // 同一件商品下不能有两份一模一样的规格组合。库里没建这个唯一键（逻辑删除会和已删行、
        // 和那条默认规格撞键），所以判据只在这条 SQL 上：只看 in 用的那几行
        Long dupCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopProductSku>()
                .eq(ShopProductSku::getProductId, sku.getProductId())
                .eq(ShopProductSku::getSpecIds, sku.getSpecIds())
                .ne(excludeId != null, ShopProductSku::getId, excludeId));
        if (dupCount != null && dupCount > 0) {
            throw new ServiceException("商品「" + product.getProductName() + "」下已有"
                    + (StringUtils.isBlank(sku.getSpecText()) ? "默认规格" : "规格「" + sku.getSpecText() + "」") + "，不能重复添加");
        }
        if (sku.getSafetyStock() == null) {
            sku.setSafetyStock(0);
        }
        if (sku.getStockWarnEnable() == null) {
            sku.setStockWarnEnable(1);
        }
        if (sku.getGainPoints() == null) {
            sku.setGainPoints(0);
        }
        if (sku.getExchangePoints() == null) {
            sku.setExchangePoints(0);
        }
        if (sku.getSaleEnable() == null) {
            sku.setSaleEnable(1);
        }
    }

    private int nextSort(Long productId) {
        List<ShopProductSku> exists = list(new LambdaQueryWrapper<ShopProductSku>()
                .select(ShopProductSku::getSort)
                .eq(ShopProductSku::getProductId, productId));
        return exists.stream()
                .map(ShopProductSku::getSort)
                .filter(sort -> sort != null)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(0);
    }
}
