package com.shop.api.service;

import com.shop.entity.ShopOrderItem;
import com.shop.entity.SysOrder;
import com.shop.entity.SysRefund;
import com.shop.vo.refund.ShopRefundItemVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 下单这一路的用积分：抵扣校验 + 冻结（下单）/ 结转+送分（支付）/ 解冻（取消）/ 按比例退回（退款），写在 shop-api。
 *
 * 和统一下单、用券、取消那几份一样放在 shop-api：买家端摸不到 shop-admin 的 service，
 * 两边各写一套的话"这一单该占多少分"就会有两种说法，而那是买家账户上的资产。
 *
 * 四个动作跟着订单的四拍走，每一拍都在人家已有的那个事务里：
 * 1. {@link #apply} —— 算钱那一趟里判这一单能不能用这么多分（统一下单和下单试算共用），只改内存不落库
 * 2. {@link #freeze} —— 主单已经落库，把这一笔分从可用里冻进冻结（真下单才走，试算不走）
 * 3. {@link #settle} —— 支付回调：冻结的那笔真扣走，并按每一行选的规格带回来的积分送给他
 * 4. {@link #unfreeze} / {@link #giveBack} —— 单子没成交（取消）整笔解冻；成交之后退了货按退款件数比例
 *    把抵扣的分还回去，同时把当初为这批货送出去的分收回来
 *
 * 账户上那五个数的走法是这张表定死的口径（balance = total − used − frozen，由写入方在同一事务里保证）：
 * 冻结 = balance−p / frozen+p，解冻正好反过来，结转 = frozen−p / used+p（可用一分再动，
 * 所以结转那一拍不记流水），退抵扣 = used−p / balance+p，送分 = total+p / balance+p，
 * 收回送分 = total−p / balance−p（收不满就收到可用为 0，绝不扣成负数）。
 * 每一步都是带条件的原子 UPDATE（判据压在 WHERE 的 balance/frozen/used >= 这一笔 里），不读回来再改。
 *
 * 分和钱之间怎么折（1 元值多少分）在 ShopPointsSupport，只有那一份：
 * 行实付 = 小计 − 优惠 − 积分抵的钱 这一式子被退款、出库成本、配送费三处读着，折算抄两遍必对不上账。
 */
public interface ShopOrderPointsService {

    /**
     * 校验这一单用得了这么多分，并算出这些分抵掉多少钱（就地读 items 每行的 usedPoints，不落库）。
     *
     * 三道判据，一道不过就当场抛异常（宁可拒单，也不能"分扣了、钱没抵上"或者反过来）：
     * - 每行的 usedPoints 是个非负整数（不传就是不用分，0 也一样）
     * - 这一行折出来的钱不能超过它券后还剩的钱（小计 − 优惠券摊的优惠），抵不出负数实付
     * - 逐行相加的总分数不超过账户上的可用积分（没有账户行就是 0 分，用一分都拒）
     *
     * 顺序有讲究：这一拍必须走在优惠券之后、配送费之前 —— 配送费要看货值实付（"实付满多少免配送费"那一档），
     * 积分抵掉的那一份也是货值实付的一部分，先算完分再算运费才对得上。
     *
     * @param userId 下单人（sys_user.id）：分是这个人账户上的，代客下单时冻结的也是参数里那一个人的
     * @param items  已经建好、分完仓、摊完券的明细行（usedPoints 由请求体带进来）
     * @return 这一单用掉多少分、抵了多少钱（没用分就是 0 分 0 元，后两拍在上面走都是零开销）
     */
    Usage apply(Long userId, List<ShopOrderItem> items);

    /**
     * 冻结：把这一单要用的分从可用积分挪进冻结积分，并记一笔「8 抵扣」的流水（points 为负）。
     *
     * 必须在主单 insert 之后调（流水要落 biz_id / biz_no 是哪个订单）。和下单同事务：
     * 冻结失败就整单回滚，不会出现"订单落了、分没扣"或者反过来。
     * 抢不到那一行（并发下同一批分被别的订单占走）报回去让买家重新提交。
     */
    void freeze(Long userId, SysOrder order, Usage usage);

    /**
     * 支付回调那一拍：这一单冻住的分真扣走（frozen → used，可用余额不再动，所以不记流水），
     * 再按这一单买的东西送积分（Σ 件数 × shop_product_sku.gain_points，记一笔「2 购物」的正流水，
     * 过期时间由 sys_config 那一档 expire_day 算）。
     *
     * 送完分顺手把逐行那一份钉回 shop_order_item.gain_points（这一行送了几分 = 每件档位 × 这一行件数）：
     * 那是**当时**的档位快照，退款收回送分读的就是它，不再拿规格表现在的 gain_points 倒推。
     *
     * 唯一调用方是支付回调里"一张订单只建一张出库单"的那道闸后面（重复通知不会再走到这儿），
     * 所以送分不会被加两遍、那一格也不会被写两遍。规格没设 gain_points（0 或空）就是这件不分，整单都是 0 就一条流水都不插。
     */
    void settle(SysOrder order, List<ShopOrderItem> lines);

    /**
     * 取消订单那一拍：整笔解冻（frozen → balance），记一笔「8 抵扣」的正流水冲掉下单那一笔。
     *
     * 单没成交，抵掉的钱没走、分更不该花 —— 这一拍和退券、还库存是同一批要跟着取消一起还回去的东西。
     *
     * @return 解冻了多少分（这一单没用过分就是 0）
     */
    int unfreeze(SysOrder order, List<ShopOrderItem> lines);

    /**
     * 退款那一拍，一退一收两笔一起做（同一个事务，都在退款回调那一条链里）：
     *
     * ① 还抵扣：这一批退掉的货，把当初为它们扣掉的分按比例还给买家（used → balance），
     * 记一笔「8 抵扣」的正流水。
     * 比例按件数算、不按金额算，和退款钱那一头是同一个式子：这一行用掉的分 ×（这次退的件数 ÷ 购买件数）
     * 取整，再减掉以前那几批已经退过的部分。取整方式定的是"整行退完正好把这一行的分退干净"，
     * 所以分几批退完加起来不多不少就是 used_points 那一格。
     * 一行没用过分（这一列是 0 或老数据为空）就是这一行没什么可退的，跳过。
     * 这一腿是硬判据：要还的比账上累计消耗还多，说明这一单已经退过一遍，报错让整笔退款回滚。
     *
     * ② 收送分：反过来，把当初为这一批货送出去的分收回来（total 和 balance 一起减，见 Mapper 的
     * reclaimGainPoints），记一笔「2 购物」的**负**流水（档位和送分共用，靠正负分方向，同 8 抵扣那两头）。
     * 收回的量读明细行上那一格 gain_points（{@link #settle} 送分时钉下去的快照），走的是和①同一个摊法：
     * 这一行送的分 ×（这次退的件数 ÷ 购买件数）取整，再减掉以前那几批已经收过的部分 —— 几批退完正好收干净。
     * 商品后来改过送分档位也不影响：那一格记的是当初实际送了多少。
     * 不但不报错，还允许收不满：那些分多半已经被他花掉一部分，能收多少收多少、收到可用为 0 为止 ——
     * 钱该退还得退，退款不该因为收不回分而卡住，更不能把账户扣成负数。
     * 不收这一腿就是"买了送分 → 再退货"白得一笔分。
     * 这一列加上之前就已经支付过的老单，那一格是 0 ⇒ 按 0 收、不追缴。
     *
     * @param refund  这张退款单（退给谁、从谁那儿收看它的 user_id，那是建单时从订单上抄下来的下单人）
     * @param lineMap 这一单的明细行，按行 id 索引 —— 取的是回写之前的数量，本方法自己算这次退到第几件；
     *                抵扣和赠送那两格（used_points / gain_points）也从这几行上读
     * @param items   sys_refund.extra 那份行快照（退哪几行、各退几件）
     * @return 退回多少分（①那一腿；这批货里没掺抵扣过的行就是 0。收回的送分不在这个数里，看流水）
     */
    int giveBack(SysRefund refund, Map<Long, ShopOrderItem> lineMap, List<ShopRefundItemVo> items);

    /**
     * 这一单的用分账本：只在统一下单/试算那一个事务里活着，本身不落库。
     *
     * points 是要冻结的分（也是账户上要占住的量），money 是这些分折出来的钱 —— 已经逐行折好相加，
     * 所以它正好等于各行 discount 之外要再扣掉的那一份，主单和行两头才对得上同一笔钱。
     */
    record Usage(int points, BigDecimal money) {
    }
}
