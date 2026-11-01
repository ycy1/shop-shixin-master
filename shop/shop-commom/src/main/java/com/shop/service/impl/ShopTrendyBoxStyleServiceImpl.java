package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.entity.ShopTrendyBoxItem;
import com.shop.mapper.ShopTrendyBoxItemMapper;
import com.shop.service.ShopTrendyBoxStyleService;
import com.shop.vo.cart.ShopCartBoxItemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按盒 id 现查那份款式清单（口径见接口那一份）
 *
 * 只认启用中的款式（status=1）：被停掉的款式已经不发人了，一整盒里不该还带着它。
 * 款式没挂规格的那些（sku_id 为 NULL，空奖、只展示）也筛掉 —— 那一件没有可卖的规格，
 * 落进明细里等于让下单拆出一条不知道锁谁库存的行。
 */
@Service
@RequiredArgsConstructor
public class ShopTrendyBoxStyleServiceImpl implements ShopTrendyBoxStyleService {

    private final ShopTrendyBoxItemMapper itemMapper;

    @Override
    public List<ShopCartBoxItemVo> stylesOf(Long boxId) {
        if (boxId == null) {
            return new ArrayList<>();
        }
        List<ShopTrendyBoxItem> styles = itemMapper.selectList(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .eq(ShopTrendyBoxItem::getBoxId, boxId)
                .eq(ShopTrendyBoxItem::getStatus, 1)
                .isNotNull(ShopTrendyBoxItem::getSkuId)
                .orderByAsc(ShopTrendyBoxItem::getSort)
                .orderByAsc(ShopTrendyBoxItem::getId));
        List<ShopCartBoxItemVo> out = new ArrayList<>(styles.size());
        for (ShopTrendyBoxItem style : styles) {
            ShopCartBoxItemVo one = new ShopCartBoxItemVo();
            one.setSkuId(style.getSkuId());
            one.setStyleName(style.getStyleName());
            one.setStyleImage(style.getStyleImage());
            // 一整盒 = 这台盒当前配置的款式各一件
            one.setQuantity(1);
            out.add(one);
        }
        return out;
    }
}
