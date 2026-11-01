package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopTrendy;
import com.shop.entity.ShopTrendyBox;
import com.shop.entity.ShopTrendyBoxItem;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopTrendyBoxItemMapper;
import com.shop.mapper.ShopTrendyBoxMapper;
import com.shop.mapper.ShopTrendyMapper;
import com.shop.service.ShopTrendyBoxItemService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopTrendyBoxItemServiceImpl extends ServiceImpl<ShopTrendyBoxItemMapper, ShopTrendyBoxItem> implements ShopTrendyBoxItemService {

    /** 概率上限：同一只盒里启用中的款式概率合计不能超过 1（=100%） */
    private static final BigDecimal PROBABILITY_TOTAL_LIMIT = BigDecimal.ONE;

    private final ShopTrendyBoxMapper boxMapper;

    private final ShopProductSkuMapper skuMapper;

    private final ShopProductMapper productMapper;

    private final ShopTrendyMapper trendyMapper;

    @Override
    public List<ShopTrendyBoxItem> listByBox(Long boxId) {
        List<ShopTrendyBoxItem> items = list(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .eq(ShopTrendyBoxItem::getBoxId, boxId)
                // 隐藏款排最前（它是这套盒的卖点），同组内按 sort、id
                .orderByDesc(ShopTrendyBoxItem::getIsHidden)
                .orderByAsc(ShopTrendyBoxItem::getSort)
                .orderByDesc(ShopTrendyBoxItem::getId));
        fillSku(items);
        return items;
    }

    @Override
    public void insert(ShopTrendyBoxItem item) {
        validate(item, null);
        applyDefaults(item);
        save(item);
    }

    @Override
    public void update(ShopTrendyBoxItem item) {
        if (item.getId() == null) {
            throw new ServiceException("款式ID不能为空");
        }
        ShopTrendyBoxItem exists = getById(item.getId());
        if (exists == null) {
            throw new ServiceException("款式不存在");
        }
        // box_id 是 NEVER（款式不跨盒挪），所以判据按库里那行，不认请求体带的那格
        item.setBoxId(exists.getBoxId());
        validate(item, item.getId());
        applyDefaults(item);
        updateById(item);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    /**
     * 按 sku_id 回填「规格文本 + 商品名」，给款式列表那一列显示（一次 in 查询，不联表）。
     * SKU 后来被删掉就查不到，这两格留空 —— 表单里那格下拉自己会兜底显示 id。
     */
    private void fillSku(List<ShopTrendyBoxItem> items) {
        Set<Long> skuIds = new HashSet<>();
        for (ShopTrendyBoxItem item : items) {
            if (item.getSkuId() != null) {
                skuIds.add(item.getSkuId());
            }
        }
        if (skuIds.isEmpty()) {
            return;
        }
        Map<Long, ShopProductSku> skuById = new HashMap<>();
        // 一次 select 两列以上：只 select 一列时 MP 会把整个对象塞成 null 元素，遍历就 NPE
        for (ShopProductSku sku : skuMapper.selectList(new LambdaQueryWrapper<ShopProductSku>()
                .select(ShopProductSku::getId, ShopProductSku::getProductId, ShopProductSku::getSpecText)
                .in(ShopProductSku::getId, skuIds))) {
            skuById.put(sku.getId(), sku);
        }
        Set<Long> productIds = new HashSet<>();
        for (ShopProductSku sku : skuById.values()) {
            if (sku.getProductId() != null) {
                productIds.add(sku.getProductId());
            }
        }
        Map<Long, String> productNameById = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (ShopProduct product : productMapper.selectList(new LambdaQueryWrapper<ShopProduct>()
                    .select(ShopProduct::getId, ShopProduct::getProductName)
                    .in(ShopProduct::getId, productIds))) {
                productNameById.put(product.getId(), product.getProductName());
            }
        }
        for (ShopTrendyBoxItem item : items) {
            ShopProductSku sku = skuById.get(item.getSkuId());
            if (sku != null) {
                item.setSkuText(sku.getSpecText());
                item.setProductName(productNameById.get(sku.getProductId()));
            }
        }
    }

    private void validate(ShopTrendyBoxItem item, Long excludeId) {
        if (item.getBoxId() == null) {
            throw new ServiceException("盲盒ID不能为空");
        }
        ShopTrendyBox box = boxMapper.selectById(item.getBoxId());
        if (box == null) {
            throw new ServiceException("盲盒不存在");
        }
        if (StringUtils.isBlank(item.getStyleName())) {
            throw new ServiceException("款式名称不能为空");
        }
        if (item.getProbability() == null) {
            throw new ServiceException("概率不能为空");
        }
        if (item.getProbability().compareTo(BigDecimal.ZERO) <= 0
                || item.getProbability().compareTo(PROBABILITY_TOTAL_LIMIT) > 0) {
            throw new ServiceException("概率要在 0 到 1 之间（0.0139 表示 1.39%）");
        }
        // 挂的规格必填（页面那格也钉了 required）：不挂 SKU 的款式抽中了没法发货，抽取记录里也没 sku 可对。
        // 表上这一列仍是可空 —— 老数据/直接改库的行不至于报错，规则只在这一层拦
        if (item.getSkuId() == null) {
            throw new ServiceException("请选择要发的规格");
        }
        ShopProductSku sku = skuMapper.selectById(item.getSkuId());
        if (sku == null) {
            throw new ServiceException("所选商品规格不存在");
        }
        assertSkuMatchesTrendy(box, sku);
        assertProbabilityFits(item, excludeId);
    }

    /**
     * 盒挂了潮玩系列，款式就只能挂这个系列下的商品规格：否则一套盒里会混进别的系列、甚至非潮玩商品的货，
     * 对外那句「本系列盲盒」就成了假宣传。没挂系列的盒不收紧这一条。
     * 页面那格下拉已经按系列筛过（正常点选进不到这一层），这里是给直接调接口 / 系列后来被改过留的兜底。
     */
    private void assertSkuMatchesTrendy(ShopTrendyBox box, ShopProductSku sku) {
        if (box.getTrendyId() == null) {
            return;
        }
        ShopProduct product = sku.getProductId() == null ? null : productMapper.selectById(sku.getProductId());
        if (product != null && box.getTrendyId().equals(product.getSectionId())) {
            return;
        }
        ShopTrendy trendy = trendyMapper.selectById(box.getTrendyId());
        // 系列名本身就带「系列」两个字（如「HACIPUPU 动物朋友系列」），这里不再往后面补一个
        throw new ServiceException("这只盲盒归属"
                + (trendy == null ? "的潮玩系列" : "「" + trendy.getTrendyName() + "」")
                + "，款式只能挂该系列下的商品规格");
    }

    /**
     * 同一只盒里启用中的款式概率合计不能超过 100%（公示概率是合规要求，超了就是假公示）。
     * 只拦「超过」这一头：不到 100% 是正常情形（剩下的比例留给没单列出来的普通款）。
     */
    private void assertProbabilityFits(ShopTrendyBoxItem item, Long excludeId) {
        List<ShopTrendyBoxItem> others = list(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .select(ShopTrendyBoxItem::getId, ShopTrendyBoxItem::getProbability, ShopTrendyBoxItem::getStatus)
                .eq(ShopTrendyBoxItem::getBoxId, item.getBoxId())
                .eq(ShopTrendyBoxItem::getStatus, 1)
                .ne(excludeId != null, ShopTrendyBoxItem::getId, excludeId));
        BigDecimal sum = item.getProbability();
        int othersCount = 0;
        for (ShopTrendyBoxItem other : others) {
            if (other == null || other.getProbability() == null) {
                continue;
            }
            sum = sum.add(other.getProbability());
            othersCount++;
        }
        if (sum.compareTo(PROBABILITY_TOTAL_LIMIT) > 0) {
            throw new ServiceException("这套盒启用中的 " + (othersCount + 1) + " 个款式概率合计已达 " + toPercent(sum)
                    + "，超过 100% 了（当前这条是 " + toPercent(item.getProbability()) + "）");
        }
    }

    private String toPercent(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * 补默认值，并把「剩余数量」按 总数-已抽 刷一遍：这一列是派生值，不刷就会停在旧数上骗人。
     */
    private void applyDefaults(ShopTrendyBoxItem item) {
        if (item.getIsHidden() == null) {
            item.setIsHidden(0);
        }
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        if (item.getSort() == null) {
            item.setSort(0);
        }
        if (item.getTotalCount() == null) {
            item.setTotalCount(0);
        }
        if (item.getDrawnCount() == null) {
            item.setDrawnCount(0);
        }
        // total_count 为 0 表示「不限数量」，这一格没有「还剩几个」的说法，给 0
        item.setRemainCount(item.getTotalCount() == 0 ? 0 : Math.max(0, item.getTotalCount() - item.getDrawnCount()));
    }
}
