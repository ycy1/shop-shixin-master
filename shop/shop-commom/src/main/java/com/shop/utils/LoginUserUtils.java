package com.shop.utils;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.Constants;
import com.shop.dto.user.LoginUserInfo;
import com.shop.exception.ServiceException;

/**
 * 读当前登录账号的登录信息（AuthServiceImpl 登录时写进 Sa-Token session 的那份 LoginUserInfo 快照）。
 * 服务层用它做数据范围判断，避免每个列表各自抄一遍取值和类型比较。
 */
public class LoginUserUtils {

    /** 账号类型：门店（字典 account_type） */
    public static final int ACCOUNT_TYPE_STORE = 1;

    /** 账号类型：送货员（字典 account_type） */
    public static final int ACCOUNT_TYPE_RIDER = 2;

    /** 账号类型：消费者（字典 account_type） */
    public static final int ACCOUNT_TYPE_CONSUMER = 3;

    /** 账号类型：供应商（字典 account_type） */
    public static final int ACCOUNT_TYPE_SUPPLIER = 4;

    /**
     * 账号类型：ERP 操作员（进销存管理，字典 account_type）
     * 和 0 平台用户同性质，是"内部员工"身份而不是门店/消费者那样的业务身份。
     * 进销存这几张按 user_id 认领自己那几条：shop_erp_purchase（负责的操作员）、
     * shop_erp_warehouse（负责的仓库）、shop_erp_purchase_in（入库操作人），见 isErp()。
     * 其余列表（供应商、供货关系、门店等）目前仍按"其它账号"看全部。
     */
    public static final int ACCOUNT_TYPE_ERP = 5;

    private LoginUserUtils() {
    }

    /**
     * 取不到就当没有：未登录、或 session 里存的不是 LoginUserInfo（老的快照结构）时返回 null，
     * 数据范围一律按"不收窄"处理，别让一个读接口的上下文问题变成 500。
     */
    public static LoginUserInfo getLoginUser() {
        try {
            Object obj = StpUtil.getSession().get(Constants.CURRENT_USER);
            return obj instanceof LoginUserInfo ? (LoginUserInfo) obj : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Integer getAccountType() {
        LoginUserInfo user = getLoginUser();
        return user == null ? null : user.getAccountType();
    }

    /**
     * 是否门店账号。account_type 是后加的列，上线前登录的 session 快照里它是 null，
     * 这种只会在重新登录后消失，所以按"不是门店"处理，不做兜底查库。
     */
    public static boolean isStore() {
        return Integer.valueOf(ACCOUNT_TYPE_STORE).equals(getAccountType());
    }

    public static boolean isRider() {
        return Integer.valueOf(ACCOUNT_TYPE_RIDER).equals(getAccountType());
    }

    /** 是否消费者账号（购物车按 user_id 收窄用） */
    public static boolean isConsumer() {
        return Integer.valueOf(ACCOUNT_TYPE_CONSUMER).equals(getAccountType());
    }

    /** 是否供应商账号（供应商管理按 user_id 收窄用） */
    public static boolean isSupplier() {
        return Integer.valueOf(ACCOUNT_TYPE_SUPPLIER).equals(getAccountType());
    }

    /** 是否 ERP 操作员账号（采购单 / 仓库 / 入库单按 user_id 收窄用） */
    public static boolean isErp() {
        return Integer.valueOf(ACCOUNT_TYPE_ERP).equals(getAccountType());
    }

    /**
     * 写入类的接口要的是"必须有登录态"，取不到就当场拒掉，别让调用方各自抄那三行判空。
     *
     * @param then 报错文案的后半截，带上前缀就是完整一句，例如 "后再下单" → "请先登录后再下单"
     */
    public static LoginUserInfo requireLoginUser(String then) {
        LoginUserInfo user = getLoginUser();
        if (user == null || user.getId() == null) {
            throw new ServiceException("请先登录" + then);
        }
        return user;
    }

    /**
     * 这一条数据（ownerId = 那行的 user_id）在不在当前账号的数据范围内：消费者账号只管自己名下的，
     * 其它账号（平台 / 门店 / ERP / 送货员）是后台代客操作，不按归属收窄。
     * 只回答判据、不报错：每一路要说清的是"只能退自己名下的订单"还是"只能确认自己名下的订单"，
     * 文案留给调用方拼。
     */
    public static boolean inOwnScope(Long ownerId) {
        if (!isConsumer()) {
            return true;
        }
        LoginUserInfo user = getLoginUser();
        return user != null && user.getId() != null && ownerId != null
                && ownerId == user.getId().longValue();
    }
}
