package com.shop.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopProduct;
import com.shop.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 优惠券的业务口径：这张券命中哪几行、命中那些行值多少钱、每一档券各自怎么折（判据不在这儿，见 com.shop.rules.coupon）。
 *
 * 为什么和 DeliveryFeeUtils 一样住在 shop-commom：动这些数的是两条互相看不见的路 ——
 * 后台「优惠券」那一页只发行、只把面额写进模板和用户券的快照，统一下单在 shop-api 才算钱，
 * 而「折扣率 0.9 就是九折」「满/减」这些口径只要抄成两份，用户手上的券和这一单收的钱就会对不上。
 * 钱是下单那一刻跟货款一起收走的，事后发现算错只能退款，所以这一段必须只有一份。
 *
 * 这里只做纯算式和纯筛选（给数就出数），一次查库都没有：分类倒排表、每条规格挂在哪个商品/哪个分类下，都由调用方读好传进来。
 *
 * 另有两条「发一张券给用户」的口径（{@link #couponCode}、{@link #expireTimeOf}）：
 * 发券的写入方不止后台「优惠券」那一页的「发放」—— 徽章兑现也要发券，而两边都在券码和过期时间上留一份，
 * 就会出现同一个模板发出去的券有两套编号规则、两份有效期。这两条因此也收在这儿，只有这一份。
 * 规则类只管"这一档券过不过"：怎么筛行、每一档怎么折全在本类，一条算式都不在规则里重写。
 * "哪一档用哪条算式"就在各档规则认领自己档位的那一次调用上 —— 所以加一档券类型是加一个规则类，不用改算式。
 *
 * 传进来的 face_value / threshold_amount / max_discount 一律取**用户券上的快照**，不是模板的：
 * 券发到手上之后运营把模板改成什么，都不该回头改已经到用户手上的那一张。
 */
public class CouponUtils {

    /** 金额统一保留两位，和库里的 decimal(10,2) 对齐 */
    private static final int MONEY_SCALE = 2;

    /** 券类型：1 满减券（face_value 是减免金额，满 threshold_amount 可用） */
    public static final int TYPE_FULL_REDUCE = 1;

    /** 券类型：2 折扣券（face_value 是折扣率，0.9 = 9 折） */
    public static final int TYPE_DISCOUNT = 2;

    /** 券类型：3 免邮券（抵的是配送费，不占商品金额） */
    public static final int TYPE_FREE_SHIPPING = 3;

    /** 券类型：4 代金券（face_value 是抵扣金额，无门槛就是它直接抵） */
    public static final int TYPE_VOUCHER = 4;

    /** 适用范围：1 全场 */
    public static final int SCOPE_ALL = 1;

    /** 适用范围：2 指定商品（scope_value 里存 productIds；券圈到商品这一层，同一件商品的哪个规格都抵得着） */
    public static final int SCOPE_PRODUCT = 2;

    /** 适用范围：3 指定分类（scope_value 里存 categoryIds，判命中时**含下级分类**） */
    public static final int SCOPE_CATEGORY = 3;

    /** 适用范围：4 指定门店（生效的是模板上的 store_id，scope_value 不存东西） */
    public static final int SCOPE_STORE = 4;

    /** 用户券状态：0 未使用 —— 只有这一档能用 */
    public static final int USER_STATUS_UNUSED = 0;

    /** 用户券状态：1 已使用 */
    public static final int USER_STATUS_USED = 1;

    /** 用户券状态：2 已过期（这一期没有定时任务往里写，过期靠 expire_time 现判，这一档只是给人工作废留的） */
    public static final int USER_STATUS_EXPIRED = 2;

    /** 用户券状态：3 已作废 */
    public static final int USER_STATUS_VOID = 3;

    /** 模板状态：4 已取消 —— 活动被人撤了，发到用户手上的那一批也不再认（3 已结束 不拦：那只是"不再发了"，手上的券按自己的有效期走完） */
    public static final int TEMPLATE_STATUS_CANCELED = 4;

    /** 有效期类型：2 领取后 N 天（这一档之外的值都按 1 固定时间段处理，同后台那一页） */
    private static final int VALID_AFTER_DAYS = 2;

    /** scope_value 那段 json 里的两个键名（后台写方向 ShopActivityCouponServiceImpl 用的是同一套，改要一起改） */
    private static final String KEY_PRODUCT_IDS = "productIds";
    private static final String KEY_CATEGORY_IDS = "categoryIds";

    private CouponUtils() {
    }

    /**
     * 用户券码：CP + 模板ID + _ + 这一张是该模板发出去的第几张（六位补零），本表唯一键。
     *
     * 序号必须由调用方传进来，而且传的是**占量那条原子 UPDATE 之后**回读的 issued_count 推出来的那一个：
     * 拿时间戳或自拼序号在并发发放里必撞（同一毫秒发两张就重码）。
     */
    public static String couponCode(Long couponId, int seq) {
        return "CP" + couponId + String.format("_%06d", seq);
    }

    /**
     * 这一张券什么时候过期（发出去那一刻就定死，落进用户券的 expire_time，那一列 NOT NULL）。
     *
     * 1 固定时间段取 valid_end，2 领取后 N 天取 现在 + valid_days。
     * 定不出来一律抛：没填天数、没填结束时间、或者结束时间已经过了 —— 发出去就是废券，还白占一张发行额度。
     */
    public static LocalDateTime expireTimeOf(ShopActivityCoupon coupon) {
        Integer validType = coupon.getValidType();
        if (validType != null && validType == VALID_AFTER_DAYS) {
            if (coupon.getValidDays() == null || coupon.getValidDays() <= 0) {
                throw new ServiceException("这张券没定领取后有效天数，发出去算不出过期时间");
            }
            return LocalDateTime.now().plusDays(coupon.getValidDays());
        }
        // 其余档（含库里那两档之外的脏值）都按 1 固定时间段走，判的是 valid_end 有没有值
        if (coupon.getValidEnd() == null) {
            throw new ServiceException("这张券没定有效期结束时间，发出去算不出过期时间");
        }
        if (coupon.getValidEnd().isBefore(LocalDateTime.now())) {
            throw new ServiceException("这张券的有效期已经过了，发出去就是废券");
        }
        return coupon.getValidEnd();
    }

    /** 券名称快照没记上就退回券码，报错里总得说清楚是哪一张 */
    public static String couponName(ShopActivityCouponUser coupon) {
        return StringUtils.defaultIfBlank(coupon.getCouponName(), coupon.getCouponCode());
    }

    /** 明细行的金额都是服务端算好落库的，正常不会是 null；这里兜一下是防止上面哪一步漏了赋值时把整单的钱算崩 */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 这一张是免邮券吗：它抵的是配送费那一笔，不折商品的钱 */
    public static boolean isFreeShipping(Integer couponType) {
        return couponType != null && couponType == TYPE_FREE_SHIPPING;
    }

    /** 这一张的适用范围认得出吗（库里 scope_type 只有 1/2/3/4 四档，tinyint + 字典；空值按 1 全场） */
    public static boolean isKnownScope(Integer scopeType) {
        return scopeType == null || scopeType == SCOPE_ALL || scopeType == SCOPE_PRODUCT
                || scopeType == SCOPE_CATEGORY || scopeType == SCOPE_STORE;
    }

    /**
     * 这些行的小计合计（原价口径，不减已经摊上去的优惠）：满减门槛和它比，折扣券的折扣基数也是它。
     */
    public static BigDecimal baseOf(List<ShopOrderItem> lines) {
        return lines.stream().map(line -> money(line.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 这张券命中这一单里的哪几行，按适用范围分叉。
     *
     * 范围类型看用户券上的快照（发到手上那一刻定格的），名单（哪些商品/哪些分类）只能回模板上取：
     * 用户券表没有 scope_value 那一格。1 全场 每行都算；2 指定商品 拿明细行那条 SKU 的**所属商品**去比
     * 模板 scope_value 里的 productIds（券只圈到商品这一层：这件商品的哪个规格都抵得着，
     * 圈到规格会出现"券看着没坏、就是这一单用不了"）；
     * 3 指定分类 按 categoryIds、**含这些分类的全部下级**（券发在"男装"上，则男装下"衬衫/T恤"那几行都能抵）；
     * 4 指定门店 拿模板的 store_id 比行上 store_id（模板那格是 NULL 就等于没限定，按全场处理）。
     *
     * 名单为空（那格 NULL、或者 json 拆不出这一档）就是什么都没命中：
     * 一张本该只管几件商品的券，绝不能在名单读不出来的时候退化成全场券，那是白送钱。
     * scope_type 认不出来 或 模板读不到 也返回空表 —— 出不出货、报哪句话由规则判，本类不掺文案。
     *
     * @param productBySku 这一单涉及的那几条规格各自挂在哪件商品下（skuId → 那条 SKU 的 shop_product 那一行）：
     *                     明细行上只有 skuId，而商品 id 和分类都记在 SPU 那一侧，
     *                     判"指定商品"和"指定分类"两档都要它，由调用方读好传进来（本类不查库）
     */
    public static List<ShopOrderItem> matchLines(ShopActivityCouponUser coupon, ShopActivityCoupon template,
                                                 List<ShopOrderItem> items, Map<Long, ShopProduct> productBySku,
                                                 Map<Long, List<Long>> categoryIndex) {
        if (template == null) {
            return new ArrayList<>();
        }
        Integer scopeType = coupon.getScopeType();
        if (scopeType == null || scopeType == SCOPE_ALL) {
            return new ArrayList<>(items);
        }
        if (scopeType == SCOPE_PRODUCT) {
            Set<Long> productIds = new HashSet<>(scopeIds(template.getScopeValue(), SCOPE_PRODUCT));
            return matched(items, line -> productIds.contains(productIdOf(line, productBySku)));
        }
        if (scopeType == SCOPE_CATEGORY) {
            Set<Long> categoryIds = withDescendants(categoryIndex, scopeIds(template.getScopeValue(), SCOPE_CATEGORY));
            return matched(items, line -> categoryIds.contains(categoryOf(line, productBySku)));
        }
        if (scopeType == SCOPE_STORE) {
            Long storeId = template.getStoreId();
            return storeId == null ? new ArrayList<>(items)
                    : matched(items, line -> storeId.equals(line.getStoreId()));
        }
        return new ArrayList<>();
    }

    /**
     * 金额型抵扣（1 满减 / 4 代金）：抵的就是面额那一格，受 max_discount 封顶。
     */
    public static BigDecimal amountDiscount(BigDecimal faceValue, BigDecimal maxDiscount) {
        return cap(faceValue == null ? BigDecimal.ZERO : faceValue, maxDiscount);
    }

    /**
     * 折扣型抵扣（2 折扣券）：命中金额 ×（1 − 折扣率）。face_value 存的是折扣率，所以九折券抵的是这一段的 10%。
     *
     * 脏数据里折扣率大于 1 会算出负数，和面额缺失一样归 0：0 意味着这张券一分钱都不抵，
     * 规则会按"这张券抵不出钱"拒掉，而不是留一个负数下去把金额算成加价。
     */
    public static BigDecimal rateDiscount(BigDecimal hitAmount, BigDecimal faceValue, BigDecimal maxDiscount) {
        if (faceValue == null) {
            return zero();
        }
        BigDecimal cap = cap(money(hitAmount).multiply(BigDecimal.ONE.subtract(faceValue)), maxDiscount);
        return cap;
    }

    /**
     * 免邮型抵扣（3 免邮券）：抵的就是那一单刚算出来的配送费，同样受 max_discount 封顶（这一格没填就是把这趟运费全免）。
     *
     * 运费要等建配送单那一步才算得出来，所以这一式由调用方在那一拍里调（规则只挂 freeShipping、不算钱），
     * 见 ShopOrderCouponServiceImpl#offsetDeliveryFee。
     */
    public static BigDecimal shippingDiscount(BigDecimal deliveryFee, BigDecimal maxDiscount) {
        return cap(money(deliveryFee), maxDiscount);
    }

    /**
     * 把模板 scope_value（json 列，驱动按字符串带出来）拆成 id 列表，指定商品取 productIds、指定分类取 categoryIds。
     *
     * 1 全场 / 4 指定门店 这两档这一格本来就是 NULL，返回空列表；
     * json 拆不动（脏数据）也返回空列表，不抛：一段脏的范围值不该让整个下单打不开堆栈，
     * 顶多是这张券按范围筛不出商品，由规则报"这一单里没有这张券能抵的商品"。
     */
    private static List<Long> scopeIds(String scopeValue, Integer scopeType) {
        List<Long> ids = new ArrayList<>();
        if (scopeType == null || (scopeType != SCOPE_PRODUCT && scopeType != SCOPE_CATEGORY)
                || scopeValue == null || scopeValue.isBlank()) {
            return ids;
        }
        String key = scopeType == SCOPE_PRODUCT ? KEY_PRODUCT_IDS : KEY_CATEGORY_IDS;
        try {
            JSONObject json = JSONUtil.parseObj(scopeValue);
            JSONArray array = json.getJSONArray(key);
            if (array == null) {
                return ids;
            }
            // JSONArray 本身是个 List，直接遍历（它的 stream 元素类型各 hutool 版本不一致，不依赖）
            for (Object item : array) {
                if (item == null) {
                    continue;
                }
                try {
                    Long id = Long.valueOf(String.valueOf(item).trim());
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                } catch (NumberFormatException e) {
                    // 范围值里混进非数字：跳过这一格，其余照旧生效
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return ids;
    }

    /** 这一行所属的商品（判"指定商品"用）：明细行上只有 skuId，商品 id 在 SPU 那一侧，所以读调用方查好的那一份 */
    private static Long productIdOf(ShopOrderItem line, Map<Long, ShopProduct> productBySku) {
        ShopProduct product = productBySku == null ? null : productBySku.get(line.getSkuId());
        return product == null ? null : product.getId();
    }

    /** 这一行挂的分类（判"指定分类"用）：同上，分类也记在商品（SPU）那一行上 */
    private static Long categoryOf(ShopOrderItem line, Map<Long, ShopProduct> productBySku) {
        ShopProduct product = productBySku == null ? null : productBySku.get(line.getSkuId());
        return product == null ? null : product.getCategoryId();
    }

    /**
     * 从名单里那几个分类出发，把它们自己和全部下级都收进来（券发在"男装"上，男装下"衬衫/T恤"那几行都能抵）。
     *
     * visited 同时当结果和防环的闸门：库里 parent_id 理论上不会成环（新增/修改都在服务层判过层级），
     * 但这段是循环取数，万一有脏数据成环，宁可少收几个分类也不能在这儿转不完。
     */
    private static Set<Long> withDescendants(Map<Long, List<Long>> index, Collection<Long> seeds) {
        Set<Long> collected = new LinkedHashSet<>();
        List<Long> frontier = new ArrayList<>();
        for (Long seed : seeds) {
            if (seed != null && collected.add(seed)) {
                frontier.add(seed);
            }
        }
        while (!frontier.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (Long parentId : frontier) {
                for (Long child : index.getOrDefault(parentId, List.of())) {
                    if (collected.add(child)) {
                        next.add(child);
                    }
                }
            }
            frontier = next;
        }
        return collected;
    }

    private static List<ShopOrderItem> matched(List<ShopOrderItem> items, Predicate<ShopOrderItem> lineMatches) {
        return items.stream().filter(lineMatches).collect(Collectors.toList());
    }

    /** 几档折算共用的收尾：最高抵扣封顶（这一格有值就是"这张券最多抵这么多"，NULL 表示不封顶）、抹平负数、留两位 */
    private static BigDecimal cap(BigDecimal discount, BigDecimal maxDiscount) {
        BigDecimal value = maxDiscount != null && discount.compareTo(maxDiscount) > 0 ? maxDiscount : discount;
        return value.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
