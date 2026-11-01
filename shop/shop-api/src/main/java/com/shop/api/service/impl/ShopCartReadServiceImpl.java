package com.shop.api.service.impl;

import com.shop.api.service.ShopCartReadService;
import com.shop.entity.ShopConsumerCart;
import com.shop.mapper.ShopConsumerCartMapper;
import com.shop.service.ShopTrendyBoxStyleService;
import com.shop.utils.LoginUserUtils;
import com.shop.vo.cart.ShopCartBoxItemVo;
import com.shop.vo.cart.ShopCartVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车 前台读取实现（一行是一个规格，名称/单价/规格文本/主图/可用库存全是 XML 联出来的）
 *
 * 这一层做的事：把登录 id 钉进查询，再给整盒那一行补上盒内款式（按盒 id 现查，库里那一行不存快照）。
 * 列表那一条交给 shop_consumer_cart 现成的那串联表（cartColumns / cartJoins，后台列表走的同一条）
 * —— 所以两条读法看到的商品名、单价口径必然一致，不会各拼一套 JOIN。
 * 自定义 XML 不走全局逻辑删除，c.is_deleted = 0 在那条 SQL 的 WHERE 上手写着。
 */
@Service
@RequiredArgsConstructor
public class ShopCartReadServiceImpl implements ShopCartReadService {

    private final ShopConsumerCartMapper shopConsumerCartMapper;

    private final ShopTrendyBoxStyleService boxStyleService;

    /**
     * 没登录直接回「请先登录后再查看购物车」：SaTokenConfigure 把 /api/** 整体排在登录拦截之外，
     * 所以「要登录」这条不能指望拦截器，由这一句当场拒（口径同个人中心那几块列表）。
     * 车是空的就回一个空数组，前端画「购物车还是空的」那一格，不报错。
     */
    @Override
    public List<ShopCartVo> myCart() {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再查看购物车").getId();
        List<ShopCartVo> lines = shopConsumerCartMapper.selectCartListByUser(loginUserId);
        // 一整盒那一行只记「哪台盒端了几盒」，盒里有哪几款各几件按盒 id 现查那台盒配的款式清单
        //（和下单拆明细读的是同一份，见 ShopTrendyBoxStyleService）；同一台盒只查一次
        Map<Long, List<ShopCartBoxItemVo>> stylesByBox = new HashMap<>();
        for (ShopCartVo line : lines) {
            if (!ShopConsumerCart.SKU_ID_WHOLE_BOX.equals(line.getSkuId()) || line.getSourceId() == null) {
                continue;
            }
            line.setBoxItemList(stylesByBox.computeIfAbsent(line.getSourceId(), boxStyleService::stylesOf));
        }
        return lines;
    }
}
