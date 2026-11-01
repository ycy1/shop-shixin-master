package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopConsumerWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 钱包账户 Mapper接口
 *
 * 读的是钱包管理那一页的列表（下面那一条自定义 SQL）。
 * 写的一律是条件增量 UPDATE，不走 updateById：这一本的 balance 要被"占"
 *（下单选余额支付先冻进 frozen_balance，支付才真扣走、取消退回来），
 * 读-改-写之间正好有缝，两个并发动作会互相覆盖；把判据压在 WHERE 里，数据库只会放行其中一个，
 * 另一个返回 0 行由调用方报错回滚（口径同积分账户那七条、库存 lockStock 那两条）。
 * 每一笔账变动都必须和 shop_consumer_wallet_log 那一行流水在同一个事务里写完。
 *
 * 这一本没有"新增账户"的入口：一行账由第一次真收钱的动作带出来（现在就是 {@link #rechargeBalance} 那一条），
 * uk_user 钉住一人一行。
 */
@Mapper
public interface ShopConsumerWalletMapper extends BaseMapper<ShopConsumerWallet> {

    /**
     * 分页查钱包账户（联 sys_user 带出昵称和手机号，可按关键字筛，正常状态在前、可用余额多的排前面）
     */
    IPage<ShopConsumerWallet> selectWalletPage(IPage<ShopConsumerWallet> page,
                                              @Param("q") ShopConsumerWallet query);

    /**
     * 下单冻结：可用余额减掉这一笔、冻结余额加上同一笔（累计充值/消费/提现都不动）。
     *
     * WHERE 压着 balance >= 这一笔，就是"一个人同时下两单、钱只够一单"那道闸：抢不到的那一趟返回 0 行，
     * 由调用方报错让整单回滚 —— 绝不能把 balance 减成负数，这本账上任何一个负数都没法解释。
     * 还压着 status = 1：账户被冻住的人不该能用余额付钱。
     *
     * @return 影响行数，0 = 可用余额不够、账户被冻、或者压根没有这一行账
     */
    int freezeForPay(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 取消订单解冻：{@link #freezeForPay} 的原路退回（冻结减、可用加），未支付的单被取消时走这一条。
     *
     * WHERE 压着 frozen_balance >= 这一笔：还的比冻的多说明这一笔已经被别的动作处理过了，返回 0 行由调用方报错。
     * 这里**不判 status**：账户中途被冻住，之前占着的钱照样得还回来。
     */
    int unfreezeForCancel(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 支付结转：冻结的余额真扣走（冻结减、累计消费加），支付成功那一刻走这一条。
     *
     * 可用余额一分不动：下单冻结那一步已经把这一笔从 balance 里减掉了，这一条只是把"暂时占着"
     * 改成"已经花掉"。也因此这一拍**不写流水** —— 流水记的是余额变动（口径同积分的 consumeFrozenPoints）。
     */
    int settleFrozen(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 退款退回余额：累计消费减、可用余额加（钱从这一单退回到本来的地方）。
     *
     * 只有支付方式为余额的那一路走这一条，退的是这一张退款单的实退金额，所以一笔单可能分几次退。
     * 这里刻意**没有** {@code total_consume >= 这一笔} 那道闸：钱包这张表是这一轮才建起来的，
     * 在那之前支付掉的单子账上没有这笔消耗（甚至压根没有账户行），卡住的话老单就退不了款了。
     * 所以改成 GREATEST 夹住：累计消费最多减到 0，不退超也不留负数。
     * 返回 0 行 = 这个人没有账户行（老单），由调用方按"这一单没走余额付过钱"跳过。
     */
    int refundToBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 充值入账：可用余额和累计充值一起加同一笔（恒等式两头同加，所以这一条不碰其余四格）。
     *
     * 这里**不判 status**：账户被冻住说的是"这一本的钱不能被动用"，不是"钱不能进来"，
     * 拦掉充值反而让解冻之后该补的那一笔没地方补。
     * 没有账户行时返回 0 行（第一次真收钱），由调用方把第一行账建出来 —— 这一条不写 INSERT，
     * 免得 create_by 那一格留痕绕过 MetaObjectHandler。
     */
    int rechargeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 提现：可用余额减、累计提现加同一笔（钱从这一本整个挪出去，恒等式还在）。
     *
     * 两道闸压在 WHERE 里：status = 1（被冻住的账户不能把钱取走）、{@code balance >= 这一笔}
     *（不能提手上没有的钱；下单占着的那一部分在 frozen_balance 上，这一条动不到它）。
     * 返回 0 行 = 没账、被冻、或者同一刻这一笔刚被别的动作（下单冻结、另一趟提现）用掉，
     * 由调用方报错回滚，流水也不留。
     */
    int withdrawBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
