package com.shop.utils;

import com.shop.exception.ServiceException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务那道的系统级判据：写多张表的服务方法都得靠 @Transactional 真的开起来才有意义。
 */
public class TransactionUtils {

    private TransactionUtils() {
    }

    /**
     * 事务没生效就当场停下 —— 注解被同类自调用绕过、或者这个方法被别处不带事务地调进来时，
     * 走到这里就是"只写了一半的账"（主单成了、明细没了；钱退了、货没退回仓）。
     * 这些服务方法都是入口方法，正常调用一定在事务里，这一句挡的是改动之后不小心绕开代理的那一路。
     *
     * @param doing 这一步在做什么，拼成"下单事务未生效，已中止处理"
     */
    public static void requireActive(String doing) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ServiceException(doing + "事务未生效，已中止处理");
        }
    }
}
