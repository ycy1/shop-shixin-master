package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.product.ShopProductQueryDto;
import com.shop.dto.product.ShopProductSaveDto;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.service.ShopProductService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.product.ShopProductVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 商品（SPU）读写。
 *
 * 这一层只管 shop_product 那一行的事：新增/修改表单收的都是整件商品的属性，
 * 单价、规格、库存预警这些「一个规格一个数」的列在 shop_product_sku，由 ShopProductSkuServiceImpl 逐条录。
 * 唯一的例外是新增时会连带建一条「默认 SKU」，理由见 insert 里那段注释。
 */
@Service
@RequiredArgsConstructor
public class ShopProductServiceImpl extends ServiceImpl<ShopProductMapper, ShopProduct> implements ShopProductService {

    private final ShopProductSkuMapper skuMapper;

    private final ShopCategoryMapper categoryMapper;

    private final ShopBrandMapper brandMapper;

    private final ShopStoreMapper storeMapper;

    @Override
    public IPage<ShopProductVo> selectPage(ShopProductQueryDto query) {
        if (query == null) {
            query = new ShopProductQueryDto();
        }
        // 数据权限按账号类型收窄（口径同门店/送货员/配送单列表）：只有账号类型=门店的只看自己创建的商品，
        // 其它类型看全部。一律覆盖 currentUserId，前端传什么都不认
        query.setCurrentUserId(LoginUserUtils.isStore() ? StpUtil.getLoginIdAsLong() : null);
        return baseMapper.selectProductPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopProductVo selectProductById(Long id) {
        ShopProductVo product = baseMapper.selectProductById(id);
        if (product == null) {
            throw new ServiceException("商品不存在");
        }
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopProductSaveDto form) {
        ShopProduct product = toEntity(form);
        // 新增时表单那栏是禁用的、不会传编码，和门店/物流公司编码一样由系统生成
        if (StringUtils.isBlank(product.getProductCode())) {
            product.setProductCode(GeneratorNoUtils.getProductNo());
        }
        validate(product, null);
        if (product.getSort() == null) {
            product.setSort(0);
        }
        // 上下架和轮播位都是 SPU 上的列（重构前那两格在 SKU 表上）：新增默认上架，轮播位由运营逐条选，新商品默认不进
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getCarouselEnable() == null) {
            product.setCarouselEnable(0);
        }
        normalizeImages(product);
        product.setUserId(StpUtil.getLoginIdAsLong());
        save(product);
        // 一件商品至少有一条 SKU，否则它在交易链上是悬空的：库存、购物车、单据明细都按 sku_id 记账，
        // 没有这一行的话这件商品连入个库都无处挂。这一条 spec_ids 是空串（=没有具体规格的那个默认款），
        // 单价和重量留空，运营在列表的「SKU管理」弹窗里补，或者直接再加几条有规格的把它搁一边。
        saveDefaultSku(product.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopProductSaveDto form) {
        if (form.getId() == null) {
            throw new ServiceException("商品ID不能为空");
        }
        ShopProduct exists = getById(form.getId());
        if (exists == null) {
            throw new ServiceException("商品不存在");
        }
        // 判库里那行的归属，不判请求体（user_id 是 NEVER 列，请求体里带什么都不算数）
        assertWritable(Collections.singletonList(exists));
        ShopProduct product = toEntity(form);
        // 编码/名称/类目允许不传，不传时沿用原值参与校验
        if (StringUtils.isBlank(product.getProductCode())) {
            product.setProductCode(exists.getProductCode());
        }
        if (StringUtils.isBlank(product.getProductName())) {
            product.setProductName(exists.getProductName());
        }
        if (product.getCategoryId() == null) {
            product.setCategoryId(exists.getCategoryId());
        }
        validate(product, product.getId());
        normalizeImages(product);
        updateById(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        assertWritable(listByIds(ids));
        // removeByIds 走全局逻辑删除，只改 shop_product.is_deleted
        removeByIds(ids);
        // SKU 跟着商品一起软删：实体有 isDeleted 字段，mapper.delete 会被 MP 转成逻辑删除的 UPDATE。
        // 库存/购物车/单据明细里那些行不受影响（它们只认 sku_id，行还在，只是再也选不到了）
        skuMapper.delete(new LambdaQueryWrapper<ShopProductSku>()
                .in(ShopProductSku::getProductId, ids));
    }

    /**
     * 写操作的归属校验：账号类型=门店的只能动自己创建的商品，口径同列表的 currentUserId。
     * 老数据 user_id 是 NULL，门店账号本来就看不到、这里也一律改不动。
     */
    private void assertWritable(List<ShopProduct> products) {
        if (!LoginUserUtils.isStore()) {
            return;
        }
        Long userId = StpUtil.getLoginIdAsLong();
        for (ShopProduct product : products) {
            if (!userId.equals(product.getUserId())) {
                throw new ServiceException("商品「" + product.getProductName() + "」不是你创建的，无权修改或删除");
            }
        }
    }

    /**
     * 表单按同名属性拷成实体。
     *
     * ShopProductSaveDto 现在收的全是 shop_product 的列（含 status / carousel_enable），
     * 和实体一一对上，所以能直接拷 —— 只要别再往 DTO 里塞 SKU 那侧的字段。
     */
    private ShopProduct toEntity(ShopProductSaveDto form) {
        ShopProduct product = new ShopProduct();
        BeanUtils.copyProperties(form, product);
        return product;
    }

    private void validate(ShopProduct product, Long excludeId) {
        if (StringUtils.isBlank(product.getProductCode())) {
            throw new ServiceException("商品编码不能为空");
        }
        if (StringUtils.isBlank(product.getProductName())) {
            throw new ServiceException("商品名称不能为空");
        }
        if (product.getCategoryId() == null) {
            throw new ServiceException("请选择商品分类");
        }
        ShopCategory category = categoryMapper.selectById(product.getCategoryId());
        if (category == null) {
            throw new ServiceException("所选商品分类不存在");
        }
        // brand_id 可空（不关联品牌），但传了就必须存在
        if (product.getBrandId() != null && brandMapper.selectById(product.getBrandId()) == null) {
            throw new ServiceException("所选品牌不存在");
        }
        // 文创商品是这家店自己的周边，没有归属店就进不了门店详情页「文创商品」那一档，所以这一档必填门店
        if (Integer.valueOf(1).equals(product.getCulturalEnable()) && product.getStoreId() == null) {
            throw new ServiceException("文创商品必须选择所属门店");
        }
        // 普通商品的 store_id 仍可空（=不限定门店），但传了就必须存在
        if (product.getStoreId() != null && storeMapper.selectById(product.getStoreId()) == null) {
            throw new ServiceException("所选门店不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                .eq(ShopProduct::getProductCode, product.getProductCode())
                .ne(excludeId != null, ShopProduct::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("商品编码「" + product.getProductCode() + "」已存在");
        }
    }

    /**
     * images 是 MySQL json 列，空字符串不是合法 JSON 会直接写入失败；
     * 而全局 field-strategy=not_empty 又会跳过空字符串字段，导致"清空轮播图"落不了库。
     * 统一归一成 "[]" 两个问题一起解决。
     */
    private void normalizeImages(ShopProduct product) {
        if (StringUtils.isBlank(product.getImages())) {
            product.setImages("[]");
        }
    }

    /**
     * 建那条默认 SKU：没有具体规格的那一款。
     *
     * 数字都给到"不碍事"的档位（销量 0、预警开着、可单卖、不发积分、排序 0），
     * 单价、重量、安全/最大库存留 NULL —— 可空列由全局 field-strategy=not_empty 自然跳过，
     * 是「还不知道」而不是「零」，页面上显示 '-'，下单前必须先补单价（SKU 的 validate 会拦没单价的那几条）。
     */
    private void saveDefaultSku(Long productId) {
        ShopProductSku sku = new ShopProductSku();
        sku.setProductId(productId);
        sku.setSkuCode(GeneratorNoUtils.getSkuNo());
        sku.setSpecIds("");
        sku.setSpecText("");
        sku.setSalesCount(0);
        sku.setSafetyStock(0);
        sku.setStockWarnEnable(1);
        sku.setGainPoints(0);
        sku.setExchangePoints(0);
        sku.setSaleEnable(1);
        sku.setSort(0);
        skuMapper.insert(sku);
    }
}
