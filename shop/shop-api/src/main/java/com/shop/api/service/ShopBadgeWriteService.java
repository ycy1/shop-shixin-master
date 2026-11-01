package com.shop.api.service;

/**
 * 用户徽章 写入服务（前台「个人中心 → 个人信息 → 徽章」每行那颗「展示 / 收起」）
 *
 * 只改 is_show 这一列：这一格说的是「这一枚要不要挂在账号卡下面给人看」，不是账 ——
 * 名称/文字/图标三格快照、有效期、状态都不碰，也不收积分、不收券。
 * 后台「获取记录」弹窗里同一格的开关在 shop-admin 那一份 service 里（{@code ShopActivityBadgeUserService#updateIsShow}），
 * 那一条按账号类型放行（平台档能替别人改），这一条没有那种身份。
 *
 * 只认登录 id：入参里没有 userId 这一格，改的就是 token 背后那一个人名下的那一行，
 * 不验归属就等于拿别人的徽章行 id 打这个接口能替别人收徽章。
 */
public interface ShopBadgeWriteService {

    /**
     * 把自己名下一枚徽章收成「不展示」或放回来。
     *
     * @param id     用户徽章行的 id（shop_activity_badge_user.id，前端从「我的徽章」那一列表里带的）
     * @param isShow 1 展示、0 隐藏，两档之外一律拒
     */
    void setShow(Long id, Integer isShow);
}
