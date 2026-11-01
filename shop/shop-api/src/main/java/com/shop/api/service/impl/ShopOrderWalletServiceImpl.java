package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopOrderWalletService;
import com.shop.entity.ShopConsumerWallet;
import com.shop.entity.ShopConsumerWalletLog;
import com.shop.entity.SysOrder;
import com.shop.entity.SysRefund;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerWalletLogMapper;
import com.shop.mapper.ShopConsumerWalletMapper;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 余额支付 服务实现（四拍走法和账户上那几个数怎么动见接口 {@link ShopOrderWalletService}）
 *
 * 两块分工，和用分那一份是同一套形状：
 * - 账户怎么占、怎么还都在 ShopConsumerWalletMapper 那四条条件增量里，判据压在 WHERE 的
 *   balance / frozen_balance >= 这一笔 上，返回 0 行就是这一笔占不着，报出去让整单回滚 ——
 *   绝不做"读回来减一下再写回去"那种两步动作（钱是 decimal，中间那道缝正好够两笔并发互相覆盖一次）
 * - 这里只留两件事：把判据跑完（说清是账户被冻了还是余额不够）、把每一笔变动记成流水
 *
 * 这一本账**不由这里建**：积分账户可以由第一次送分带出来，钱包不行 —— 冻结那一条 SQL 压着
 * balance >= 这一笔，没有账户行就是 0 元、占不到任何东西，所以没有余额的人走到 apply 那一拍就被拦下。
 * 账行由第一次真收钱的动作带出来，那一条是 ShopWalletService 的充值那颗（前台「我的钱包」）；
 * 后台的「调整余额」那颗本期还没有。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderWalletServiceImpl implements ShopOrderWalletService {

    private final ShopConsumerWalletMapper shopConsumerWalletMapper;

    /** 每一笔账变动都要跟着记一行流水，同事务（只插不改不删，所以只有 insert） */
    private final ShopConsumerWalletLogMapper shopConsumerWalletLogMapper;

    /** 支付方式（sys_order.pay_type，字典 pay_type）：4 余额 —— 只有这一档占的是钱包里的钱 */
    private static final int PAY_TYPE_BALANCE = 4;

    /** 变动类型（字典 wallet_biz_type）：6 冻结 —— 下单用余额支付时占住的那一笔，可用余额少了它 */
    private static final int BIZ_TYPE_FREEZE = 6;

    /** 变动类型：7 解冻 —— 取消订单把占住的那一笔整个还回可用 */
    private static final int BIZ_TYPE_UNFREEZE = 7;

    /** 变动类型：3 退款 —— 余额支付的那一单退了货，钱退回可用余额 */
    private static final int BIZ_TYPE_REFUND = 3;

    /** 账户状态（字典 wallet_status）：1 正常 —— 被冻住的账户占不到钱 */
    private static final int STATUS_NORMAL = 1;

    private static final int MONEY_SCALE = 2;

    @Override
    public BigDecimal apply(Long userId, Integer payType, BigDecimal payAmount) {
        if (!isBalance(payType) || payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return zero();
        }
        BigDecimal money = payAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        // 用余额是要写账户表和流水表的，事务没开就别算：算完不落库，钱没冻住、单却落了
        TransactionUtils.requireActive("余额支付");
        ShopConsumerWallet account = account(userId);
        if (account != null && !Integer.valueOf(STATUS_NORMAL).equals(account.getStatus())) {
            throw new ServiceException("钱包账户已被冻结，不能用余额支付，换一种支付方式或者联系客服");
        }
        BigDecimal balance = balanceOf(account);
        if (balance.compareTo(money) < 0) {
            throw new ServiceException("余额不够：这一单要付 " + money + " 元，钱包里只有 " + balance
                    + " 元，换一种支付方式或者先充值");
        }
        return money;
    }

    @Override
    public void freeze(Long userId, SysOrder order, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        TransactionUtils.requireActive("冻结余额");
        // balance >= 这一笔 压在 WHERE 里就是并发那道闸：同一笔钱被两单同时抢，只有一趟改得动
        if (shopConsumerWalletMapper.freezeForPay(userId, amount) == 0) {
            throw new ServiceException("钱包里的 " + amount + " 元刚被别的订单用掉了（或者账户刚被冻住），请重新提交");
        }
        log(userId, amount.negate(), BIZ_TYPE_FREEZE, order.getId(), order.getOrderNo(),
                "下单用余额支付，占住 " + amount + " 元");
    }

    @Override
    public void settle(SysOrder order, Integer notifiedPayType) {
        // 反方向也要对一次：这一单下单时没占住钱，通知里却说收的是余额 —— 放过去主单就落成 pay_type=4，
        // 而钱包上根本没冻过这一笔，结转那条 SQL 走不到，账上就成了"这一单用余额付过了"但钱一分没减
        //（「我的订单」里接着付那一颗最容易撞上：那里的单是下单时没选余额的那一批）
        if (isBalance(notifiedPayType) && !isBalance(order.getPayType())) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」下单时没选余额支付，改不了余额 —— "
                    + "余额是在下单那一刻占住的，要换这种支付方式请先取消这一单，重新下一单时选它");
        }
        BigDecimal amount = frozenOf(order);
        if (amount == null) {
            return;
        }
        // 主单上那一格马上要被通知里的渠道覆盖掉，而 pay_type = 4 是"这一单冻着钱包的钱"唯一的记号：
        // 两头不是同一个，一覆盖就没人再来把这"占着"改成"花掉"，那一笔钱永远留在 frozen_balance 里
        if (!isBalance(notifiedPayType)) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」下单时选的是余额支付，"
                    + "支付通知里报的却是别的渠道，已拒绝处理");
        }
        TransactionUtils.requireActive("结转余额");
        // frozen_balance >= 这一笔 挡的是"这一笔冻结已经被谁处理过了"（重复回调走不到这儿，走到就是账被人工动过）
        if (shopConsumerWalletMapper.settleFrozen(order.getUserId(), amount) == 0) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」冻结的 " + amount
                    + " 元对不上（账户上冻着的不是这个数），已中止处理");
        }
    }

    @Override
    public BigDecimal unfreeze(SysOrder order) {
        BigDecimal amount = frozenOf(order);
        if (amount == null) {
            return zero();
        }
        TransactionUtils.requireActive("解冻余额");
        if (shopConsumerWalletMapper.unfreezeForCancel(order.getUserId(), amount) == 0) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」下单占住的 " + amount
                    + " 元退不回来（账户上冻着的不是这个数，这一笔大概已经被处理过了），已中止取消");
        }
        log(order.getUserId(), amount, BIZ_TYPE_UNFREEZE, order.getId(), order.getOrderNo(),
                "订单取消，余额支付的 " + amount + " 元退回钱包");
        return amount;
    }

    @Override
    public BigDecimal giveBack(SysOrder order, SysRefund refund, BigDecimal amount) {
        if (!isBalance(order.getPayType()) || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return zero();
        }
        TransactionUtils.requireActive("退回余额");
        // 这一条没有闸门，所以 0 行不报错：这张表是这一轮才建起来的，钱包上线之前支付的老单压根没有账户行，
        // 卡住的话那些单子就退不了款 —— 钱该退还是得退
        if (shopConsumerWalletMapper.refundToBalance(order.getUserId(), amount) == 0) {
            return zero();
        }
        log(order.getUserId(), amount, BIZ_TYPE_REFUND, refund.getOrderId(), refund.getOrderNo(),
                "退货退回余额 " + amount + " 元（退款单 " + refund.getRefundNo() + "）");
        return amount;
    }

    /**
     * 这一单在钱包上占着多少钱：支付方式是余额、而且确实有钱要付才给数，其余一律返回 null（三拍都跳过）。
     *
     * 读的是主单上那两格，不另存"这一单冻了多少钱"的列 —— 余额付的就是这一单的实付，
     * sys_order.pay_amount 那一个数从下单到支付回调都不回改（券和分都在下单那一刻摊完）。
     */
    private BigDecimal frozenOf(SysOrder order) {
        if (order == null || !isBalance(order.getPayType())) {
            return null;
        }
        BigDecimal amount = order.getPayAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean isBalance(Integer payType) {
        return Integer.valueOf(PAY_TYPE_BALANCE).equals(payType);
    }

    /** 读这一个人的账户行（没有就是还没开账，调用方按余额 0 元处理；状态那一格只有 apply 要用） */
    private ShopConsumerWallet account(Long userId) {
        if (userId == null) {
            return null;
        }
        return shopConsumerWalletMapper.selectOne(new LambdaQueryWrapper<ShopConsumerWallet>()
                .select(ShopConsumerWallet::getBalance, ShopConsumerWallet::getStatus)
                .eq(ShopConsumerWallet::getUserId, userId)
                .last("LIMIT 1"));
    }

    /** 账户上的可用余额（没有账户行、或者那一格是脏的空值都按 0 元说） */
    private BigDecimal balanceOf(ShopConsumerWallet account) {
        return account == null || account.getBalance() == null
                ? zero() : account.getBalance().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 记一笔流水：amount 带符号（退回为正、占住为负），balance 记的是**这一笔之后**账户上的可用余额。
     *
     * 那一个 balance 不自己算，读回来才和账户表是同一个数（同一事务里刚改过的那一版）。
     * 读不到行属于账被删了，那种情况宁可记 0 也别留 NULL —— 这一列 NOT NULL，插不进去会让整单回滚，
     * 而流水页要的是"这笔变动之后他还有多少钱"，账没了就是 0 元。
     */
    private void log(Long userId, BigDecimal amount, int bizType, Long bizId, String bizNo, String description) {
        ShopConsumerWalletLog row = new ShopConsumerWalletLog();
        row.setUserId(userId);
        row.setAmount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        row.setBalance(balanceOf(account(userId)));
        row.setBizType(bizType);
        row.setBizId(bizId);
        row.setBizNo(bizNo);
        row.setDescription(description);
        shopConsumerWalletLogMapper.insert(row);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
