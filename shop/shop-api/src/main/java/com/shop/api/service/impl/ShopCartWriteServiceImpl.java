package com.shop.api.service.impl;

import com.shop.api.service.ShopCartWriteService;
import com.shop.dto.cart.ShopCartSaveDto;
import com.shop.entity.ShopConsumerCart;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerCartMapper;
import com.shop.service.ShopCartAddService;
import com.shop.utils.LoginUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车 前台写入实现（判据抄后台 ShopConsumerCartServiceImpl，三处按前台的身份重写）
 *
 * 一、归属无条件比。后台那一份是「只有消费者账号才逐条比 user_id」（平台/门店是代客操作），
 * 前台这一路根本没有后台身份，四条写第一句都是 requireLoginUser，userId 一律钉成登录 id。
 * 二、加购是叠数量，不是「撞了就报错」。后台新增那一路查重命中会回「请直接修改那条记录的数量」——
 * 那一页先看待选列表再填表单，重复是用户点错了；详情页那颗「加入购物车」点一次叠一件，重复是正常使用，
 * 所以「先叠 → 再复活 → 最后插」那三条路（连同下架/没分类那两道拒单）在 ShopCartAddService 里，
 * 抽中实物进车走的也是同一份，这里只负责把登录 id 和参数收口。
 * 三、不给改 sku_id。一行买的是哪个规格就是这一行的身份，后台能改（代客纠错），
 * 前台页面上的画法就是删一条重新加，所以这一份的 update 只认数量和勾选两格。
 *
 * 四条写都不挂 @Transactional（口径同收藏那一份）：这一层自己一句都不落库，加购那一条交下去最多两句
 *（叠数量没命中就建第一条，撞键再退回叠一次），其余三条各一句。
 * 数量上下都不过库存闸：可用库存只在下单那一刻才真的拦（同后台那份 update）。
 */
@Service
@RequiredArgsConstructor
public class ShopCartWriteServiceImpl implements ShopCartWriteService {

    private final ShopConsumerCartMapper shopConsumerCartMapper;

    private final ShopCartAddService shopCartAddService;

    @Override
    public void add(ShopCartSaveDto form) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再加入购物车").getId();
        // 不传数量按一件（详情页那颗按钮就是「加一件」），拒单文案都由下沉那一份说
        shopCartAddService.add(loginUserId, form.getSkuId(), form.getQuantity() == null ? 1 : form.getQuantity());
    }

    @Override
    public void update(ShopCartSaveDto form) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再修改购物车").getId();
        if (form.getId() == null) {
            throw new ServiceException("购物车记录ID不能为空");
        }
        assertOwn(form.getId(), loginUserId);

        ShopConsumerCart cart = new ShopConsumerCart();
        cart.setId(form.getId());
        boolean changed = false;
        if (form.getQuantity() != null) {
            if (form.getQuantity() < 1) {
                throw new ServiceException("数量要大于 0");
            }
            cart.setQuantity(form.getQuantity());
            changed = true;
        }
        if (form.getSelected() != null) {
            assertSelected(form.getSelected());
            cart.setSelected(form.getSelected());
            changed = true;
        }
        // 两格都没传就是没什么要改的，一句空 UPDATE 都不发
        if (!changed) {
            return;
        }
        // updateById 走全局 field-strategy=not_empty：只有上面赋了值的两格进 SET，
        // update_by / update_time 由 MyMetaObjectHandler 顺手盖上，WHERE 自动带 is_deleted = 0
        shopConsumerCartMapper.updateById(cart);
    }

    @Override
    public void selectAll(Integer selected) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再修改购物车").getId();
        assertSelected(selected);
        shopConsumerCartMapper.updateSelectedByUser(loginUserId, selected, loginUserId);
    }

    @Override
    public void delete(List<Long> ids) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再删除购物车商品").getId();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 整批先看归属再删，不做部分成功：删到一半才发现有一条不是自己的，那半截已经收不回来了
        for (Long id : ids) {
            assertOwn(id, loginUserId);
        }
        // deleteBatchIds 走全局逻辑删除，只把 is_deleted 置 1；这一行占着的 uk_user_sku_source 由 add 的复活那条路接手
        shopConsumerCartMapper.deleteBatchIds(ids);
    }

    /**
     * 归属：别人的、已删的、没这个 id 三种都回同一句「购物车记录不存在」，不分档
     *（selectById 自动带 is_deleted = 0，所以已删的那一条天然查不到；分档就等于让人拿 id 试出哪一条挂在谁名下）。
     */
    private void assertOwn(Long id, Long loginUserId) {
        if (id == null) {
            throw new ServiceException("购物车记录不存在");
        }
        ShopConsumerCart exists = shopConsumerCartMapper.selectById(id);
        if (exists == null || !loginUserId.equals(exists.getUserId())) {
            throw new ServiceException("购物车记录不存在");
        }
    }

    private void assertSelected(Integer selected) {
        if (selected == null || (selected != 0 && selected != 1)) {
            throw new ServiceException("选中状态只能是 1选中 或 0未选中");
        }
    }
}
