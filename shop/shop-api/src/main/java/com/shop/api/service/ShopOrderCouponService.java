package com.shop.api.service;

import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 下单这一路的用券：校验 + 算钱 + 摊到明细行 + 核销（统一下单专用，写在 shop-api）。
 *
 * 和统一下单、统一接单那几份一样放在 shop-api 而不是后台那套 service 里：买家端摸不到 shop-admin 的 service，
 * 两边各写一套的话，"这张券能抵多少"就会有两种说法 —— 而它是真金白银。
 *
 * 三拍，按统一下单里的调用顺序走（中间那一拍要等配送费算出来才有数，所以拆不开）：
 * 1. {@link #apply} —— 把货值券（满减/折扣/代金）判一遍并摊到明细行的 discount_amount 上；免邮券只校验、先压在账上
 * 2. {@link #offsetDeliveryFee} —— 配送费是服务端刚算出来的那一笔，免邮券抵的就是它
 * 3. {@link #redeem} —— 主单已经落库，把真正用掉的那几张券核销掉
 *
 * 还有反过来的一拍：{@link #giveBack} —— 单子没成交（取消未支付的单），按订单把核销掉的券退回去。
 * 它不在上面这条顺序里，因为那是"下单"这一趟的事；取消是另一趟请求，唯一的凭据是券上落的 order_id。
 *
 * 下单试算（ShopOrderCreateService#preview）走前两拍、**不走第三拍**（它不落库，也就没什么可核销）：
 * 试算给买家看的钱必须和真提交时一字不差，所以两边共用这一份判断，别在试算那头另写一套。
 *
 * 为什么必须摊到行上：这一路只有一份钱的口径 —— 行实付 = shop_order_item.total_amount − discount_amount
 *（见 ShopOrderSupport#paidAmount），退款按它退钱、出库单按它结成本。优惠只记在主单上而不动行，
 * 那"逐行实付相加 = 这一单收到的钱"就不成立了，退款会退出不存在的钱。
 *
 * 入参那一串是**用户券** id（shop_activity_coupon_user.id，发到某人手上那一张），不是模板 id：
 * 一张模板可以发成几千张券，能用的只有"在这个人名下的那一张"。
 */
public interface ShopOrderCouponService {

    /**
     * 校验这一批券 + 把货值券的抵扣摊到明细行上（就地改 items 每行的 discountAmount，不落库）。
     *
     * 下面这几段是**口径**。判据在规则组 com.shop.rules.coupon（一条与券类型无关的判据管归属/状态/过期/活动/适用范围/门槛，
     * 后面四条规则按券类型各认领一档、把折出来的抵额挂回事实，按 @Rule 的 priority 依次跑），规则只往事实上写一个拒绝码（CouponRejectEnum）——
     * 它既不算钱（筛命中行、按券类型折算都在 CouponUtils，实现先算好再塞进事实），也不说话
     *（那句拒用理由由实现按码拼，见 ShopOrderCouponServiceImpl#rejectText）。
     *
     * 每张券要过的四道：
     * - 存在、且 user_id 就是下单人（别人的券拿 id 也能打到这里，那样等于白捡钱）
     * - status = 0 未使用（1 已使用 / 2 已过期 / 3 已作废 都拒）
     * - expire_time 还没过（这一期没有定时任务把过期的券推成 2 已过期，所以过期只能在这一道现判）
     * - 适用范围命中这一单里的行 + 够得上使用门槛
     * - 折算出得来正数抵额（面额空着、或抵不出钱的，拒掉这一张而不是悄悄按 0 走）
     *
     * 适用范围（scope_type）：1 全场=每一行都算；2 指定商品=按模板 scope_value 里的 productIds 比行上那条规格的所属商品
     *（券只圈到商品这一层，这件商品的哪个规格都抵得着）；
     * 3 指定分类=按 categoryIds，**含这些分类的全部下级**（券发在"男装"上，则男装下"衬衫/T恤"那几行都能抵，2026-09-29 他点头的口径）；
     * 4 指定门店=拿模板上的 store_id 比行上 store_id（模板那格是 NULL 就等于没限定，按全场处理）。
     *
     * 使用门槛比的是**这张券命中范围内那些行的小计合计**，不是整单货值：
     * 券只管它范围内的货，"满 200 减 50"指的是范围内凑够 200。
     *
     * 摊法：一张券抵下来的钱按**命中各行的小计占命中行小计合计的比例**摊到行上，
     * 比例分必留的分厘尾差落在最后一行，所以 Σ 行优惠正好等于这一张抵的钱（见 ShopOrderCouponServiceImpl#spread）。
     *
     * 多张券**逐张独立判定后相加**（2026-09-29 他定的口径）：各判各的范围和门槛，一张券不会因为另一张已经抵过而作废；
     * 但同一行被两张券覆盖时，后一张最多把这行剩余可抵（小计 − 已经摊掉的优惠）抵到 0，抵不出负数实付。
     *
     * 任何一张券判不过都当场抛 ServiceException：宁可拒单，也不能"券没收掉、钱也没给抵"，
     * 更不能悄悄跳过那一张把剩下的钱收走 —— 前端显示给买家的就是这一单的应付金额。
     *
     * @param userId     下单人（sys_user.id），券必须在这个人名下
     * @param couponIds  请求体里那串用户券 id（可空；顺序就是逐张抵扣的顺序）
     * @param items      已经建好并分完仓的明细行（totalAmount 已定，discountAmount 由本方法写入）
     * @param productBySku 这批行涉及的那几条规格各自挂在哪件商品下（skuId → 它的 shop_product 那一行），
     *                     判"指定商品"和"指定分类"两档都用它：商品 id 和分类都记在 SPU 那一侧，
     *                     明细行上只有 skuId，所以由调用方读好传进来
     * @return 这一单的用券账本（{@link Usage}）；没用券就是一个空账本，后两拍在上面走都是零开销
     */
    Usage apply(Long userId, List<Long> couponIds, List<ShopOrderItem> items, Map<Long, ShopProduct> productBySku);

    /**
     * 免邮券抵配送费：抵的是服务端按规则刚算出来的那一笔，但**不超过这张券的 max_discount**（这一格空着就是把这趟运费全免），
     * 抵出来的钱不落进任何明细行（明细行的优惠只算货，出库/退款都只看货）。
     *
     * 一张订单只有一个收货地址、一趟配送，所以**最多用掉一张免邮券**：
     * 请求体里塞了两张，只有第一张真被用掉，另一张留在用户手上不动（不核销）。
     * 配送费算出来就是 0（没配启用规则、或命中了"实付满多少免配送费"那一档）、或这张券的上限填成了 0
     * 而抵不出钱时也不核销 —— 没有东西可抵就别把券花掉，那是买家的资产。
     *
     * @param deliveryFee 服务端算出来的那一笔配送费
     * @return 实际抵掉的金额（配送费和这张券 max_discount 里小的那一个，抵不出就是 0），调用方拿它加进主单的优惠里
     */
    BigDecimal offsetDeliveryFee(Usage usage, BigDecimal deliveryFee);

    /**
     * 核销：把这一单真正用掉的券置成 1 已使用，写上落在哪个订单和使用时间，并按模板把 used_count 加上去。
     *
     * 必须在主单 insert 之后调（要拿 orderId/orderNo）。和下单同事务：券核销失败就整单回滚，
     * 不会出现"订单落了、券没扣"或者反过来。
     *
     * 两道都是带条件的原子 UPDATE，不靠"先查再改"：
     * - 用户券那张 WHERE 压着 status = 0，抢不到就是这张券刚被别的订单用掉，报回去让买家重新提交
     * - 模板 used_count 按券模板汇总加一次，WHERE 压着不超过 issued_count
     */
    void redeem(Long orderId, String orderNo, Usage usage);

    /**
     * 退券：把这一单核销掉的券原路退回用户手上（订单被取消时调，见 ShopOrderCancelService）。
     *
     * 它是第四拍，但**不吃 {@link Usage}**：Usage 只在下单/试算那一个事务里活着，取消是另一趟请求、
     * 隔了几分钟甚至几小时，那时候唯一的凭据就是核销时写在券上的 order_id。所以这一头按订单去捞
     *（{@code WHERE order_id = ? AND status = 1}），而不是拿调用方的账本。
     *
     * 退的是**这张券本身**，不是这一单的钱：钱由 sys_order 那一档状态说了算，
     * 明细行上摊掉的 discount_amount 不回改（那张单已经不存在了，留着是为了对账看得清当初怎么算的）。
     *
     * 和 {@link #redeem} 对称，两道 UPDATE 也都是带条件的原子操作：
     * - 用户券：status 1 → 0，order_id / order_no / use_time 一起清空（WHERE 压着 status = 1 和这个 orderId）
     * - 模板 used_count 按券模板汇总减回去（WHERE 压着 used_count >= 这一批）
     *
     * 一张单没用过券就是零开销；没捞到券就安静返回 0，不报错——取消订单不该被"当初压根没用券"卡住。
     *
     * @param orderId sys_order.id，只退核销在这一张订单上的券
     * @return 退回了几张券（给调用方回显和记日志用）
     */
    int giveBack(Long orderId);

    /**
     * 一次下单的用券账本：只在统一下单/试算那一个事务里活着，本身不落库。
     *
     * 分两堆是因为免邮券什么时候算出钱来不一样：货值券在 apply 里就抵完了（直接进 entries），
     * 免邮券要等配送费出来才知道"这一单有没有东西给我抵"，先在 pendingFreeShipping 里压着。
     *
     * entries 上带的是**每张券抵了多少钱**：核销只数得清"用掉哪几张"，而试算要把每一张抵了多少
     * 单独报给买家（他还想知道那张免邮券这一单到底生没生效）。
     */
    class Usage {

        /** 货值券摊到明细行上的抵扣合计（这些钱已经写进各行 discount_amount，主单金额直接加它） */
        private BigDecimal goodsDiscount = BigDecimal.ZERO;

        /** 已经决定要用掉的券和各自抵的金额（货值券在 apply 时就进来，免邮券在 offsetDeliveryFee 里进来），核销和试算都按这一串做 */
        private final List<Entry> entries = new ArrayList<>();

        /** 校验过了、还没决定用不用的免邮券（按请求体里的顺序压着，只有第一张有机会真被用掉） */
        private final List<ShopActivityCouponUser> pendingFreeShipping = new ArrayList<>();

        public BigDecimal getGoodsDiscount() {
            return goodsDiscount;
        }

        public List<Entry> getEntries() {
            return entries;
        }

        public List<ShopActivityCouponUser> getPendingFreeShipping() {
            return pendingFreeShipping;
        }

        public void addGoodsDiscount(BigDecimal amount) {
            goodsDiscount = goodsDiscount.add(amount);
        }

        public void addUsed(ShopActivityCouponUser coupon, BigDecimal discount) {
            entries.add(new Entry(coupon, discount));
        }

        /** 一张券在这一单里抵了多少：券是那张用户券（名称/类型/面额都在它身上），金额是服务端算出来的 */
        public record Entry(ShopActivityCouponUser coupon, BigDecimal discount) {
        }
    }
}
