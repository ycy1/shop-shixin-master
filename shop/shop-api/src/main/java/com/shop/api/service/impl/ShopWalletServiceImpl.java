package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopWalletService;
import com.shop.dto.wallet.ShopWalletChangeDto;
import com.shop.entity.ShopConsumerWallet;
import com.shop.entity.ShopConsumerWalletLog;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerWalletLogMapper;
import com.shop.mapper.ShopConsumerWalletMapper;
import com.shop.utils.LoginUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 钱包充值 / 提现 服务实现（两拍走法和账户上那几个数怎么动见接口 {@link ShopWalletService}）
 *
 * 和用余额那一份（{@link ShopOrderWalletServiceImpl}）同一套形状：
 * - 账户怎么加怎么减都在 ShopConsumerWalletMapper 那六条条件增量里，判据压在 WHERE 的
 *   status / balance >= 这一笔 上，返回 0 行就是这一笔动不了，报出去让整趟回滚；
 *   绝不做"读回来加一下再写回去"那种两步动作（钱是 decimal，中间那道缝正好够两笔并发互相覆盖一次）
 * - 这里只留三件事：把金额那一格验干净、把没开账的第一行账建出来、把每一笔变动记成流水
 *
 * 事务由这两颗自己开（下单那四拍是嵌在别人的事务里的，这里没有别人在事务里调它）：
 * 改账和写流水必须一起成、一起败，不然流水页上「变动后余额」那一列就接不上。
 */
@Service
@RequiredArgsConstructor
public class ShopWalletServiceImpl implements ShopWalletService {

    private final ShopConsumerWalletMapper shopConsumerWalletMapper;

    /** 每一笔账变动都要跟着记一行流水，同事务（只插不改不删，所以只有 insert） */
    private final ShopConsumerWalletLogMapper shopConsumerWalletLogMapper;

    /** 变动类型（字典 wallet_biz_type）：1 充值 —— 钱进这一本，可用余额和累计充值一起加 */
    private static final int BIZ_TYPE_RECHARGE = 1;

    /** 变动类型：4 提现 —— 钱从这一本整个挪出去，可用余额减、累计提现加 */
    private static final int BIZ_TYPE_WITHDRAW = 4;

    /** 账户状态（字典 wallet_status）：1 正常 —— 被冻住的账户不能把钱提走（充值不判这一格） */
    private static final int STATUS_NORMAL = 1;

    private static final int MONEY_SCALE = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopConsumerWallet recharge(ShopWalletChangeDto form) {
        Long userId = LoginUserUtils.requireLoginUser("后再充值余额").getId();
        BigDecimal money = money(form, "充值");

        ShopConsumerWallet account = selectByUserId(userId);
        if (account == null) {
            // 这张表第一行账的来路：之前没有余额的人走到下单那一拍就被冻结那条 SQL 拦下，
            // 现在他先充值，账在这儿建出来（其余那五格都给 0 —— 有数才能守住那条恒等式，别留 null）
            ShopConsumerWallet created = new ShopConsumerWallet();
            created.setUserId(userId);
            created.setBalance(money);
            created.setFrozenBalance(zero());
            created.setTotalRecharge(money);
            created.setTotalConsume(zero());
            created.setTotalWithdraw(zero());
            created.setGiftBalance(zero());
            created.setStatus(STATUS_NORMAL);
            try {
                shopConsumerWalletMapper.insert(created);
            } catch (DuplicateKeyException e) {
                // 同一刻两个动作第一次给这个人开账：uk_user 只放行一行，输的那一趟改走增量那条
                if (shopConsumerWalletMapper.rechargeBalance(userId, money) == 0) {
                    throw new ServiceException("钱包账户刚被删掉，这一笔 " + money + " 元没落进来，请刷新页面重试");
                }
            }
        } else if (shopConsumerWalletMapper.rechargeBalance(userId, money) == 0) {
            throw new ServiceException("钱包账户刚被删掉，这一笔 " + money + " 元没落进来，请刷新页面重试");
        }

        log(userId, money, BIZ_TYPE_RECHARGE, description(form, "余额充值"));
        return selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopConsumerWallet withdraw(ShopWalletChangeDto form) {
        Long userId = LoginUserUtils.requireLoginUser("后再提现余额").getId();
        BigDecimal money = money(form, "提现");

        // 三条判据在这里先跑一遍，只为把话说明白（哪一种不够说哪一种）；真闸门在 SQL 的 WHERE 里
        ShopConsumerWallet account = selectByUserId(userId);
        if (account == null) {
            throw new ServiceException("钱包里还没有余额，先充值再提现");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(account.getStatus())) {
            throw new ServiceException("钱包账户已被冻结，暂时不能提现，请联系客服");
        }
        BigDecimal balance = zeroIfNull(account.getBalance());
        if (balance.compareTo(money) < 0) {
            throw new ServiceException("能提的余额只有 " + balance + " 元，这一笔要提 " + money
                    + " 元（下单没付的单子占着的那一部分在冻结余额里，那一部分提不走）");
        }
        if (shopConsumerWalletMapper.withdrawBalance(userId, money) == 0) {
            throw new ServiceException("这 " + money + " 元刚被别的动作用掉了（下单用余额支付、或者另一趟提现），请重新提交");
        }

        log(userId, money.negate(), BIZ_TYPE_WITHDRAW, description(form, "余额提现"));
        return selectByUserId(userId);
    }

    /**
     * 金额那一格：必填、大于 0、最多两位小数（表的列是 decimal(14,2)）。
     *
     * 小数位多于两位不当场四舍五入 —— 页面显示充 10.004 元、账上落 10.00 元，
     * 这一笔和流水那一行就对不上了，宁可退回让输入的人自己改。
     */
    private BigDecimal money(ShopWalletChangeDto form, String action) {
        BigDecimal raw = form == null ? null : form.getAmount();
        if (raw == null) {
            throw new ServiceException("请填写要" + action + "的金额");
        }
        if (raw.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("金额要大于 0 元，" + action + " 不能是 " + raw + " 元");
        }
        BigDecimal money = raw.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (money.compareTo(raw) != 0) {
            throw new ServiceException("金额最多两位小数，" + action + " 的 " + raw + " 元请改成 " + money + " 元");
        }
        return money;
    }

    /** 说明那一格：填了就原样进流水（这是这一笔唯一的留痕处），没填按动作补一句默认的 */
    private String description(ShopWalletChangeDto form, String fallback) {
        String text = form == null ? null : form.getDescription();
        return text == null || text.trim().isEmpty() ? fallback : text.trim();
    }

    /** 读这一个人的账户行（没开账就是 null；一人一行，LIMIT 1 只是防脏数据炸成一页） */
    private ShopConsumerWallet selectByUserId(Long userId) {
        return shopConsumerWalletMapper.selectOne(new LambdaQueryWrapper<ShopConsumerWallet>()
                .eq(ShopConsumerWallet::getUserId, userId)
                .last("LIMIT 1"));
    }

    /**
     * 记一笔流水：amount 带符号（充值为正、提现为负），biz_id / bizNo 留空 ——
     * 这一笔本期不挂任何单据（充值那条真收款的链、提现单都还没接），
     * 谁动的记在 create_by、为什么动记在说明里，同管理员调整积分那一颗。
     *
     * balance 不自己算，改完读回来才和账户表是同一个数（同一事务里刚改过的那一版）。
     */
    private void log(Long userId, BigDecimal amount, int bizType, String description) {
        BigDecimal after = zeroIfNull(selectByUserId(userId).getBalance());
        ShopConsumerWalletLog row = new ShopConsumerWalletLog();
        row.setUserId(userId);
        row.setAmount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        row.setBalance(after);
        row.setBizType(bizType);
        row.setDescription(description);
        shopConsumerWalletLogMapper.insert(row);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 没有账户行、或者那一格是脏的空值都按 0 元说（流水那一列 NOT NULL，插不进去会让整趟回滚） */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? zero() : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
