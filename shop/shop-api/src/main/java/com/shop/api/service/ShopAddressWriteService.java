package com.shop.api.service;

import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.vo.address.ShopAddressVo;

/**
 * 收货地址 写入服务（前台「个人中心 → 收货地址」那三颗：添加地址 / 编辑 / 删除）
 *
 * 为什么写在 shop-api 而不是复用后台那一份：shop-admin 依赖 shop-api（反过来不通），
 * 而这一张表的判据（同一账号下 is_default 互斥、第一条直接当默认、三列 NOT NULL 提前拦）
 * 两边必须一套 —— 所以判据在这一份里照后台抄一次，后台那份仍是它自己的入口
 *（它多的是「按账号类型决定 userId」这一层，前台没有身份这一说，userId 只认登录 id）。
 *
 * 三颗都只认登录 id：新增时参数里的 userId 不认（传了也被覆盖成自己），
 * 修改和删除的归属拿库里那一行的 user_id 比，比不上就按「不存在」处理 —— 都点不到别人的地址簿。
 */
public interface ShopAddressWriteService {

    /**
     * 新增一条收货地址：userId 一律钉成登录 id，参数里传什么都不认。
     *
     * @return 落库的那一条（按 id 回捞，nickname/username 那两格联表带出）
     */
    ShopAddressVo create(ShopAddressSaveDto form);

    /**
     * 修改自己名下的一条：整条覆盖式提交（可空列没填就是清成空，口径同后台那份 update）。
     *
     * 这一条不给参数里的 userId 任何入口 —— 归属只认库里那一行，翻不到别人的名下。
     *
     * @return 改完的那一条（回捞同一份形状，前端直接换掉列表里那一行）
     */
    ShopAddressVo update(ShopAddressSaveDto form);

    /**
     * 删自己名下的一条（个人中心列表上那颗「删除」）。
     *
     * 逻辑删除：只把 is_deleted 置 1。删掉的正好是默认那条不自动补选 ——
     * 下单时没有默认地址就让用户自己挑，比在服务层猜一个更安全（后台同一条判据）。
     */
    void delete(Long id);
}
