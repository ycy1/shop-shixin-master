package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpSupplier;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopSupplierProduct;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpSupplierMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopSupplierProductMapper;
import com.shop.service.ShopSupplierProductService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 供应商商品（供货关系）服务实现，写法照供应商 ShopErpSupplierServiceImpl（同 erp 命名空间、同样按账号类型收窄）
 *
 * 一行 = 一个供应商供一个**具体规格（SKU）**：本表这行只记 sku_id，行身份由唯一索引 uk_supplier_sku
 * (supplier_id, sku_id) 定死，所以一家供应商供同一商品的几个规格就是几行。
 * 规格属于哪个商品（SPU）记在 shop_product_sku.product_id 上，本表没有商品那一列，
 * 要商品那一头的信息一律走两跳：skuMapper.selectById(skuId) → productMapper.selectById(sku.getProductId())。
 *
 * 两件事在这里定下来：
 *   1. product_name / category_id 是冗余快照 —— 一律由服务层按**商品（SPU）那一行**现取覆盖
 *      （先按 skuId 取规格，再按规格上的 productId 取商品），前端传什么都不认；
 *      规格文本不落在这两列上：列表走 XML 的两跳联表，单条详情按 sku_id 现取一次（修改弹窗要显示它）；
 *   2. is_default 的互斥由服务层保证（口径同收货地址的默认地址）：把一个规格下的某行设成默认，
 *      其它行自动改回 0，不靠唯一索引（"每个规格最多一个默认供应商"用索引表达不出来）。
 *
 * 数据范围：供应商账号（account_type=4）只看/只改自己名下供应商的供货关系 —— 供货价是各家的底价，
 * 不能互相看。一个账号可以绑多家供应商（绑定不唯一），所以范围是「他名下的那几家」这一组，
 * 新增弹窗里可选的供应商也就是这几家。别的账号（平台、门店、ERP操作员）看全部、可选全部。
 */
@Service
@RequiredArgsConstructor
public class ShopSupplierProductServiceImpl extends ServiceImpl<ShopSupplierProductMapper, ShopSupplierProduct>
        implements ShopSupplierProductService {

    /** 供应商账号没绑定任何供应商时用的占位 id：表主键从 1 开始，0 永远查不到行，等价于"给他一个空列表" */
    private static final Long NO_SUPPLIER = 0L;

    private final ShopErpSupplierMapper supplierMapper;

    /** 规格那一跳：本表只记 sku_id，规格行（含它所属的 productId）在这儿取 */
    private final ShopProductSkuMapper skuMapper;

    /** 商品那一跳：供货关系上 product_name / category_id 两个冗余快照的来源仍然是商品（SPU）那一行 */
    private final ShopProductMapper productMapper;

    @Override
    public IPage<ShopSupplierProduct> selectPage(ShopSupplierProduct query) {
        if (query == null) {
            query = new ShopSupplierProduct();
        }
        // 一律覆盖：供应商账号钉成他名下的全部供应商（一家账号可以绑多家），其它账号清空这个条件。
        // 搜索栏那格 supplierId 不覆盖，两个条件是 AND，他筛别人家的供应商只会查到空列表
        query.setSupplierIds(LoginUserUtils.isSupplier() ? ownedSupplierIds() : null);
        IPage<ShopSupplierProduct> result = baseMapper.selectSupplierProductPage(PageUtil.getPage(), query);
        // 供应商名、商品编码和规格文本都是 XML 里联表现取的（本表这行只有 sku_id，商品那一头要先过 shop_product_sku
        // 再联 shop_product，两跳都在那条 SQL 里），这里不用再回填（同采购单列表）
        return result;
    }

    @Override
    public ShopSupplierProduct selectRelationById(Long id) {
        ShopSupplierProduct relation = getById(id);
        if (relation == null) {
            throw new ServiceException("供货关系不存在");
        }
        assertOwn(relation);
        // 规格文本不在本表上（这里只有 sku_id），修改弹窗要显示「商品名 + 规格」，所以按这个 id 现取一次。
        // 规格被删了就取不到，回显退化成只有商品名 —— 不影响提交（那两列本来就钉回原值）
        ShopProductSku sku = skuMapper.selectById(relation.getSkuId());
        if (sku != null) {
            relation.setSpecText(sku.getSpecText());
        }
        return relation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(ShopSupplierProduct relation) {
        normalize(relation);
        // 供应商账号只能往自己名下的供应商建关系：一家账号可以绑多家，所以他选哪一家都合法，
        // 但要判选中的这家确实挂在他名下（前端下拉只列他名下那几家是收窄，服务层判的才是边界）
        if (LoginUserUtils.isSupplier()) {
            assertSupplierOwned(relation.getSupplierId());
        }
        validate(relation, null);
        try {
            save(relation);
        } catch (DuplicateKeyException e) {
            // 前置查重只看得见的未删除行，逻辑删除的行仍然占着 uk_supplier_sku(supplier_id, sku_id)
            // （和购物车、供应商编码同一个坑）
            throw new ServiceException(duplicateMessage(relation));
        }
        clearOtherDefaults(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ShopSupplierProduct relation) {
        if (relation.getId() == null) {
            throw new ServiceException("供货关系ID不能为空");
        }
        ShopSupplierProduct exists = getById(relation.getId());
        if (exists == null) {
            throw new ServiceException("供货关系不存在");
        }
        assertOwn(exists);
        // 供应商/规格这两列定死了一条关系的身份（就是 uk_supplier_sku 那两格），改不了：要换就删了重建。
        // 无条件钉回原值（不是"为空才沿用"），否则供应商账号提交个别人的 supplier_id 就能把记录挪出自己名下，
        // 提交个别人的 sku_id 也一样能把这条关系改成供别的规格
        relation.setSupplierId(exists.getSupplierId());
        relation.setSkuId(exists.getSkuId());
        normalize(relation);
        validate(relation, relation.getId());
        try {
            updateById(relation);
        } catch (DuplicateKeyException e) {
            // 改的是别的列时理论上撞不到键（身份两列刚被钉回原值），走到这里同样是已删除的行占着 uk_supplier_sku
            throw new ServiceException(duplicateMessage(relation));
        }
        clearOtherDefaults(relation);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 供应商账号删的必须是自己那家的：堵住绕过列表直接拿别人 id 打接口这条路
        if (LoginUserUtils.isSupplier()) {
            for (ShopSupplierProduct relation : listByIds(ids)) {
                assertOwn(relation);
            }
        }
        // 不做"已进过采购单不许删"的拦截：采购明细行自己存了 sku_id 和下单当时的商品名、价格快照，
        // 删掉供货关系不影响历史单子的读取
        removeByIds(ids);
    }

    /**
     * 登录人名下的供应商 id 集合（一个账号可以绑多家，所以数据范围是一组而不是一个）；
     * 一家都没绑时返回只含 {@link #NO_SUPPLIER} 的集合——表主键从 1 开始，IN (0) 查不出任何行，
     * 让他看到的是空列表而不是全部（不能因为"没绑"就放开）。绑定关系本身不唯一（uk 只在 supplier_code 上）。
     */
    private List<Long> ownedSupplierIds() {
        List<Long> ids = new ArrayList<>();
        for (ShopErpSupplier supplier : supplierMapper.selectList(new LambdaQueryWrapper<ShopErpSupplier>()
                .select(ShopErpSupplier::getId)
                .eq(ShopErpSupplier::getUserId, StpUtil.getLoginIdAsLong()))) {
            ids.add(supplier.getId());
        }
        return ids.isEmpty() ? Collections.singletonList(NO_SUPPLIER) : ids;
    }

    /**
     * 供应商账号的归属校验，口径同 selectPage：非供应商直接放过。
     * 判的是库里那行的 supplier_id 属不属于登录人名下的那几家（请求体里带什么都不算数）。
     */
    private void assertOwn(ShopSupplierProduct relation) {
        if (!LoginUserUtils.isSupplier()) {
            return;
        }
        if (!ownedSupplierIds().contains(relation.getSupplierId())) {
            throw new ServiceException("这条供货关系不属于你的供应商，无权查看或修改");
        }
    }

    /**
     * 新增时用：判选中的这家供应商确实挂在登录人名下。
     * 供应商不存在、存在但没绑账号、绑的是别人 —— 三种都挡掉（他名下的判定和 {@link #ownedSupplierIds} 同一个依据）
     */
    private void assertSupplierOwned(Long supplierId) {
        ShopErpSupplier supplier = supplierId == null ? null : supplierMapper.selectById(supplierId);
        Long ownerId = supplier == null ? null : supplier.getUserId();
        if (ownerId == null || ownerId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("只能给你名下的供应商建供货关系");
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报"不能为空"
     */
    private void normalize(ShopSupplierProduct relation) {
        if (relation.getMinOrderQty() == null) {
            relation.setMinOrderQty(1);
        }
        if (relation.getIsDefault() == null) {
            relation.setIsDefault(0);
        }
        if (relation.getStatus() == null) {
            relation.setStatus(1);
        }
        if (StringUtils.isNotBlank(relation.getSupplyCode())) {
            relation.setSupplyCode(relation.getSupplyCode().trim());
        }
    }

    /**
     * 供应商和规格都要真有其人，然后把冗余的两列按规格所属的**商品（SPU）那一行**现取覆盖（前端传什么都不认）；
     * (supplier_id, sku_id) 查重 —— 就是 uk_supplier_sku 那两格，一家供应商对同一个规格只能有一行，
     * 改单时排除自己这条。
     *
     * 商品那一头不再是本表的列，所以这里必须走两跳：sku_id → shop_product_sku → 它的 product_id → shop_product。
     * 两跳都是 selectById（自带 is_deleted = 0），一次新增/修改就这两条主键查询，不构成 N+1；
     * 批量场景才需要 selectBatchIds，这里没有循环里逐条查的写法。
     */
    private void validate(ShopSupplierProduct relation, Long excludeId) {
        if (relation.getSupplierId() == null) {
            throw new ServiceException("请选择供应商");
        }
        if (supplierMapper.selectById(relation.getSupplierId()) == null) {
            throw new ServiceException("所选供应商不存在");
        }
        if (relation.getSkuId() == null) {
            throw new ServiceException("请选择商品规格");
        }
        // 第一跳：规格行。selectById 带 is_deleted = 0，删掉的规格查不出来
        ShopProductSku sku = skuMapper.selectById(relation.getSkuId());
        if (sku == null) {
            throw new ServiceException("所选商品规格不存在");
        }
        // 第二跳：这条规格所属的商品（SPU）。下架商品行还在，仍然可以建供货关系
        ShopProduct product = productMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new ServiceException("这条规格所属的商品不存在");
        }
        // 冗余快照的口径不变：product_name / category_id 永远来自商品那一行，不是规格那一行
        relation.setProductName(product.getProductName());
        relation.setCategoryId(product.getCategoryId());
        // 规格文本顺手记在实体上，只给撞唯一键那条提示语用（specText 不是本表列，不会写库；
        // 默认SKU 的 spec_text 是 NULL，提示语退化成只报商品名）
        relation.setSpecText(sku.getSpecText());
        if (relation.getSupplyPrice() != null && relation.getSupplyPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("供货价不能是负数");
        }
        if (relation.getMinOrderQty() != null && relation.getMinOrderQty() < 1) {
            throw new ServiceException("最小起订量至少是 1");
        }
        if (relation.getLeadTime() != null && relation.getLeadTime() < 0) {
            throw new ServiceException("供货周期不能是负数");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopSupplierProduct>()
                .eq(ShopSupplierProduct::getSupplierId, relation.getSupplierId())
                .eq(ShopSupplierProduct::getSkuId, relation.getSkuId())
                .ne(excludeId != null, ShopSupplierProduct::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该供应商已供规格「" + specLabel(product, sku) + "」，请直接修改那条已有的供货关系");
        }
    }

    /**
     * 提示语里说的是哪一条规格：有规格文本就报「商品名 规格文本」，默认SKU（spec_text 为 NULL）退化成只报商品名
     */
    private String specLabel(ShopProduct product, ShopProductSku sku) {
        String specText = StringUtils.trimToEmpty(sku.getSpecText());
        return specText.isEmpty() ? product.getProductName()
                : product.getProductName() + " " + specText;
    }

    /**
     * 撞 uk_supplier_sku 的说法。这里不回查规格，直接用 validate 已经填在实体上的两份快照
     * （product_name 是商品名、specText 只在内存里，两格都是同一次校验读出来的，不会对不上）
     */
    private String duplicateMessage(ShopSupplierProduct relation) {
        String specText = StringUtils.trimToEmpty(relation.getSpecText());
        return "该供应商已供这个商品规格「" + relation.getProductName()
                + (specText.isEmpty() ? "" : " " + specText) + "」，"
                + "可能是有一条已删除的记录占着唯一键 uk_supplier_sku";
    }

    /**
     * 设了默认就把同一规格下其它行的 is_default 抹回 0（口径同收货地址的默认地址互斥）；
     * 判的是 sku_id —— 一行是「某供应商供某个规格」，默认供应商是在这一条规格上选的，
     * 同一商品的其它规格各有自己的默认供应商，互不干扰。
     * 取消默认（传 0）不补选别人，留一个"这个规格没有默认供应商"的状态是正常状态
     */
    private void clearOtherDefaults(ShopSupplierProduct relation) {
        if (relation.getIsDefault() == null || relation.getIsDefault() != 1) {
            return;
        }
        update(new LambdaUpdateWrapper<ShopSupplierProduct>()
                .eq(ShopSupplierProduct::getSkuId, relation.getSkuId())
                .ne(ShopSupplierProduct::getId, relation.getId())
                .set(ShopSupplierProduct::getIsDefault, 0));
    }
}
