package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.cart.ShopCartQueryDto;
import com.shop.dto.cart.ShopCartSaveDto;
import com.shop.entity.ShopConsumerCart;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerCartMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopConsumerCartService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.cart.ShopCartVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车 服务实现（分页走自定义 XML 联表，写法照 shop_product）
 *
 * 与商品/门店那几个模块不同：收窄的口径是"消费者账号只看自己的购物车"（user_id = 登录 id），
 * 而不是按名下门店/创建人收窄。本表的 user_id 是"这条购物车属于哪个消费者"，
 * 不是创建人（创建人留痕在 BaseEntity 的 create_by），所以别照抄 shop_* 的 storeIds 那套。
 */
@Service
@RequiredArgsConstructor
public class ShopConsumerCartServiceImpl extends ServiceImpl<ShopConsumerCartMapper, ShopConsumerCart>
        implements ShopConsumerCartService {

    private final ShopProductSkuMapper shopProductSkuMapper;

    private final ShopProductMapper shopProductMapper;

    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopCartVo> selectPage(ShopCartQueryDto query) {
        if (query == null) {
            query = new ShopCartQueryDto();
        }
        // 消费者账号只看自己的购物车：userId 一律覆盖成登录 id，前端传什么都不认
        // （口径同配送单收窄；平台/供应商等其它账号类型不收窄，仍看全部）
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 关键字要搜商品名称/编码和规格文本、单价也在规格侧，用 wrapper 表达不了两跳联表，走 XML；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectCartPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopCartVo selectCartById(Long id) {
        ShopCartVo cart = baseMapper.selectCartById(id);
        if (cart == null) {
            throw new ServiceException("购物车记录不存在");
        }
        assertOwn(cart);
        return cart;
    }

    @Override
    public void insert(ShopCartSaveDto form) {
        ShopConsumerCart cart = toEntity(form);
        // 新增不接受调用方指定主键：id 是 AUTO，但传进来 MP 会照写
        cart.setId(null);
        // 消费者账号只能往自己的购物车里加，userId 同样覆盖，不让传别人 id 进来
        if (LoginUserUtils.isConsumer()) {
            cart.setUserId(StpUtil.getLoginIdAsLong());
        }
        validate(cart, null);
        normalize(cart);
        try {
            save(cart);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(duplicateMessage(cart));
        }
    }

    @Override
    public void update(ShopCartSaveDto form) {
        if (form.getId() == null) {
            throw new ServiceException("购物车记录ID不能为空");
        }
        ShopConsumerCart exists = getById(form.getId());
        if (exists == null) {
            throw new ServiceException("购物车记录不存在");
        }
        assertOwn(exists);
        ShopConsumerCart cart = toEntity(form);
        // 数量/选中状态/归属消费者允许不传（局部更新），不传沿用原值：这三列 NOT NULL，
        // 回填只用在必填列上，可空的分类/品牌/门店一律由 validate 按规格所属商品重新带出，不能沿用
        if (cart.getUserId() == null) {
            cart.setUserId(exists.getUserId());
        }
        // 消费者也不能把自己这条挪到别人名下，挪完就等于把记录送出去了
        assertOwn(cart);
        if (cart.getSkuId() == null) {
            cart.setSkuId(exists.getSkuId());
        }
        if (cart.getQuantity() == null) {
            cart.setQuantity(exists.getQuantity());
        }
        if (cart.getSelected() == null) {
            cart.setSelected(exists.getSelected());
        }
        validate(cart, cart.getId());
        try {
            updateById(cart);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(duplicateMessage(cart));
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 消费者账号删的必须是自己那几条：先按 id 捞出实体逐条校验，堵住直接拿别人 id 打接口这条路
        if (LoginUserUtils.isConsumer()) {
            List<ShopConsumerCart> carts = listByIds(ids);
            for (ShopConsumerCart cart : carts) {
                assertOwn(cart);
            }
        }
        // removeByIds 走全局逻辑删除，只把 is_deleted 置 1；
        // 注意 uk_user_sku 不含 is_deleted，删掉的行仍占着唯一键，所以重新加同一个规格会撞键，
        // 由 insert/update 的 DuplicateKeyException 转成业务提示
        removeByIds(ids);
    }

    /**
     * 消费者账号的归属校验，口径同 selectPage 的数据范围：非消费者直接放过（平台/供应商看全部）。
     * 列表已经收窄了，这里补的是"绕过列表直接拿 id/别人的 userId 打接口"那条路。
     */
    private void assertOwn(ShopConsumerCart cart) {
        if (!LoginUserUtils.isConsumer()) {
            return;
        }
        Long userId = cart.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这条购物车记录不属于你，无权查看或修改");
        }
    }

    /**
     * 单价、商品名这些是回显字段，不在实体上，先按同名属性拷出一份只含 shop_consumer_cart 列的实体
     */
    private ShopConsumerCart toEntity(ShopCartSaveDto form) {
        ShopConsumerCart cart = new ShopConsumerCart();
        BeanUtils.copyProperties(form, cart);
        return cart;
    }

    private void normalize(ShopConsumerCart cart) {
        if (cart.getQuantity() == null) {
            cart.setQuantity(1);
        }
        if (cart.getSelected() == null) {
            cart.setSelected(1);
        }
    }

    /**
     * 校验必填、(消费者, 规格) 唯一，并在这条规格所属的商品上回填 category_id / brand_id / store_id 三个冗余列。
     *
     * 这三列不让表单传：它们是加购当时的商品快照，商品后来改了分类或门店，购物车里仍按快照统计才有意义。
     * 购物车行只挂 sku_id，商品那一跳由 SKU 上的 product_id 带出，所以这里是两跳。
     */
    private void validate(ShopConsumerCart cart, Long excludeId) {
        if (cart.getUserId() == null) {
            throw new ServiceException("请选择消费者");
        }
        if (sysUserMapper.selectById(cart.getUserId()) == null) {
            throw new ServiceException("所选消费者不存在");
        }
        if (cart.getSkuId() == null) {
            throw new ServiceException("请选择商品规格");
        }
        ShopProductSku sku = shopProductSkuMapper.selectById(cart.getSkuId());
        if (sku == null) {
            throw new ServiceException("所选商品规格不存在");
        }
        ShopProduct product = shopProductMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new ServiceException("这条规格所属的商品不存在，不能加入购物车");
        }
        cart.setCategoryId(product.getCategoryId());
        cart.setBrandId(product.getBrandId());
        cart.setStoreId(product.getStoreId());
        // category_id 在表上是 NOT NULL，而商品的分类理论上可能为空（历史数据），到这儿必须拦住
        if (cart.getCategoryId() == null) {
            throw new ServiceException("商品「" + product.getProductName() + "」还没有分类，不能加入购物车");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopConsumerCart>()
                .eq(ShopConsumerCart::getUserId, cart.getUserId())
                .eq(ShopConsumerCart::getSkuId, cart.getSkuId())
                .ne(excludeId != null, ShopConsumerCart::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该消费者的购物车里已有「" + displayName(sku, product)
                    + "」，请直接修改那条记录的数量");
        }
    }

    /**
     * 提示语里说的是哪一条：有规格文本就报规格，默认SKU（spec_text 为空）退化成只报商品名
     */
    private String displayName(ShopProductSku sku, ShopProduct product) {
        String specText = StringUtils.trimToEmpty(sku.getSpecText());
        return specText.isEmpty() ? product.getProductName()
                : product.getProductName() + " " + specText;
    }

    private String duplicateMessage(ShopConsumerCart cart) {
        // 前置查重只能看到未删除的行，走到这里基本都是撞在一条已删除的记录上
        return "该消费者的购物车里已有这个商品规格，可能来自一条已删除的购物车记录（唯一键 uk_user_sku 仍占着）";
    }
}
