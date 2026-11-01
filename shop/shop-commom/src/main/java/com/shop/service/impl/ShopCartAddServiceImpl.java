package com.shop.service.impl;

import com.shop.entity.ShopConsumerCart;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerCartMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.service.ShopCartAddService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 加购这一条写的唯一一份实现（原来整段在 shop-api 的购物车写入里，抽盒要用同一份判据才下沉过来）
 *
 * 三条路按顺序走，绝不「撞了就报错」：
 *   1) 车上已经有没删的这一条 → UPDATE quantity = quantity + n（读出来加完写回去中间会被另一个标签页插一次）；
 *   2) 有一条删掉的行还占着 uk_user_sku_source（这个键不含 is_deleted）→ 把它复活成「这次加了 n 件、重新选中」，
 *      三格商品快照按这一次重新带；
 *   3) 这一坐标（人 + 规格 + 来源）从来没记过 → 建第一条，撞键（连点两下）就退回去叠数量。
 * 数量上下都不过库存闸：可用库存只在下单那一刻才真的拦（口径同购物车那几处写）。
 *
 * userId 由调用方给（谁登录、谁抽的盒都在各自那一层钉好），这一层不查登录态 ——
 * 抽盒那一路要往抽中的人名下加东西，手上是服务层已经取到的登录 id，不该再回头取一次 session。
 */
@Service
@RequiredArgsConstructor
public class ShopCartAddServiceImpl implements ShopCartAddService {

    /** shop_product.status：1上架 0下架（SPU 上的列）—— 进车只认这一道；规格的 sale_enable 是 ERP 建销售单那边的开关 */
    private static final int ENABLED = 1;

    private final ShopConsumerCartMapper shopConsumerCartMapper;

    private final ShopProductSkuMapper shopProductSkuMapper;

    private final ShopProductMapper shopProductMapper;

    @Override
    public void add(Long userId, Long skuId, int quantity) {
        if (quantity < 1) {
            throw new ServiceException("数量要大于 0");
        }
        if (skuId == null) {
            throw new ServiceException("请选择商品规格");
        }
        ShopProductSku sku = shopProductSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new ServiceException("所选商品规格不存在");
        }
        ShopProduct product = shopProductMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new ServiceException("这条规格所属的商品不存在，不能加入购物车");
        }
        // 下架当场拒：下架的商品前台列表本来就看不到（卡片进不来详情页），能走到这里的只有
        // 「详情页开着、这期间后台把它下了」那一种。留着它进车也到下单那一刻才被拒，不如当场说清楚。
        if (!Integer.valueOf(ENABLED).equals(product.getStatus())) {
            throw new ServiceException("商品「" + product.getProductName() + "」已下架，不能加入购物车");
        }
        // category_id 在表上是 NOT NULL，两道判据都在写之前过完：走到一半才发现它空着就留下一个填不出来的行
        if (product.getCategoryId() == null) {
            throw new ServiceException("商品「" + product.getProductName() + "」还没有分类，不能加入购物车");
        }
        write(userId, sku.getId(), quantity, product, sku.getProductPrice(),
                ShopConsumerCart.SOURCE_PRODUCT, ShopConsumerCart.SOURCE_ID_NONE);
    }

    @Override
    public boolean tryAdd(Long userId, Long skuId, int quantity, BigDecimal price, Long sourceId) {
        if (userId == null || skuId == null || quantity < 1) {
            return false;
        }
        ShopProductSku sku = shopProductSkuMapper.selectById(skuId);
        if (sku == null) {
            return false;
        }
        ShopProduct product = shopProductMapper.selectById(sku.getProductId());
        // 四道判据和上面那一条一模一样，只是过不了的那三种从「抛」换成「return false」
        if (product == null
                || !Integer.valueOf(ENABLED).equals(product.getStatus())
                || product.getCategoryId() == null) {
            return false;
        }
        // 单价听调用方的（盲盒传的是盒给的价格）；没给才按这条规格当下的现价写
        write(userId, sku.getId(), quantity, product, price == null ? sku.getProductPrice() : price,
                ShopConsumerCart.SOURCE_BOX, sourceId);
        return true;
    }

    /**
     * 三条路那一段（前置判据都过完了才会走到这里），商品快照、这一次约定的单价和来源都由调用方带着。
     *
     * 来源是这里的第三、第四格坐标、不是装饰：同一个规格在车里可以并排躺着好几行（自己加购的一行、
     * 每台盒抽中入车各一行），三条写法全都按 (人, 规格, 来源类型, 来源ID) 定点走 —— 少带一格就会串到
     * 对方那一行上，把人家约定好的单价刷掉（要修的就是这个：手动加购一次把盒价冲回原价、
     * 另一台盒抽中一次把这台盒的价冲掉）。
     */
    private void write(Long userId, Long skuId, int quantity, ShopProduct product, BigDecimal price,
                       int sourceType, Long sourceId) {
        // 「没有来源」在库里是 0，不是空 —— 这一格进唯一键，而 MySQL 的唯一索引不约束 NULL
        Long source = sourceId == null ? ShopConsumerCart.SOURCE_ID_NONE : sourceId;
        if (shopConsumerCartMapper.increaseQuantityBySku(userId, skuId, sourceType, source,
                quantity, price, userId) > 0) {
            return;
        }
        if (shopConsumerCartMapper.reviveBySku(userId, skuId, sourceType, source, quantity, price,
                product.getCategoryId(), product.getBrandId(), product.getStoreId(), userId) > 0) {
            return;
        }
        ShopConsumerCart cart = new ShopConsumerCart();
        cart.setUserId(userId);
        cart.setSkuId(skuId);
        cart.setQuantity(quantity);
        cart.setPrice(price);
        cart.setSelected(ENABLED);
        cart.setSourceType(sourceType);
        cart.setSourceId(source);
        cart.setCategoryId(product.getCategoryId());
        cart.setBrandId(product.getBrandId());
        cart.setStoreId(product.getStoreId());
        try {
            shopConsumerCartMapper.insert(cart);
        } catch (DuplicateKeyException e) {
            // 只可能是连点两下 / 两个标签页同时加同一来源的同一个规格，后那一下撞在刚建好的那一行上：叠上去就对了
            shopConsumerCartMapper.increaseQuantityBySku(userId, skuId, sourceType, source, quantity, price, userId);
        }
    }

    /**
     * 端盒那一趟 = 车里的一整盒一行，三条路的形状和上面那一份完全一样，只有落进去的那几格不同：
     * 这一行不指向任何一条规格（sku_id 和表上同样 NOT NULL 的 category_id 都落 0 当哨兵，品牌、门店留空），
     * 数量那一格是「几盒」，单价是这一盒的端盒价，盒里有哪几款各几件**不在行上存** ——
     * 页面上展开、下单拆明细都是拿 source_id 现查那台盒的款式清单（见 ShopTrendyBoxStyleService）。
     *
     * 同一台盒端第二次并到这一行上 +1，单价被这一次的最新端盒价覆盖（price = IFNULL(#{price}, price)）——
     * 和商品那一路「后一次加购定这一行的价」同一个口径：运营中途改了端盒价，先端那一盒也跟着新价结。
     */
    @Override
    public boolean tryAddBox(Long userId, Long boxId, BigDecimal boxPrice) {
        if (userId == null || boxId == null || boxPrice == null) {
            return false;
        }
        ShopConsumerCart cart = new ShopConsumerCart();
        cart.setUserId(userId);
        cart.setSkuId(ShopConsumerCart.SKU_ID_WHOLE_BOX);
        cart.setQuantity(1);
        cart.setPrice(boxPrice);
        cart.setSelected(ENABLED);
        cart.setSourceType(ShopConsumerCart.SOURCE_BOX);
        cart.setSourceId(boxId);
        cart.setCategoryId(0L);
        if (shopConsumerCartMapper.increaseQuantityBySku(userId, cart.getSkuId(), cart.getSourceType(),
                boxId, 1, boxPrice, userId) > 0) {
            return true;
        }
        if (shopConsumerCartMapper.reviveBySku(userId, cart.getSkuId(), cart.getSourceType(), boxId,
                1, boxPrice, cart.getCategoryId(), null, null, userId) > 0) {
            return true;
        }
        try {
            shopConsumerCartMapper.insert(cart);
        } catch (DuplicateKeyException e) {
            // 两个标签页各端一次同一盒：后那一下撞在刚建好的那一行上，盒数 +1 就对了
            shopConsumerCartMapper.increaseQuantityBySku(userId, cart.getSkuId(), cart.getSourceType(),
                    boxId, 1, boxPrice, userId);
        }
        return true;
    }
}
