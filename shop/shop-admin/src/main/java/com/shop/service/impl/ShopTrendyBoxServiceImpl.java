package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopStore;
import com.shop.entity.ShopTrendy;
import com.shop.entity.ShopTrendyBox;
import com.shop.entity.ShopTrendyBoxItem;
import com.shop.entity.ShopTrendyBoxUser;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.ShopTrendyBoxItemMapper;
import com.shop.mapper.ShopTrendyBoxMapper;
import com.shop.mapper.ShopTrendyBoxUserMapper;
import com.shop.mapper.ShopTrendyMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopTrendyBoxService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopTrendyBoxServiceImpl extends ServiceImpl<ShopTrendyBoxMapper, ShopTrendyBox> implements ShopTrendyBoxService {

    /** 只用来看「所属系列/门店/运营存不存在」这三道判据，和回填列表那两格名字 */
    private final ShopTrendyMapper trendyMapper;

    private final ShopStoreMapper storeMapper;

    private final SysUserMapper sysUserMapper;

    /** 只用来看名下有几款（列表那一列）和删除时连带软删款式 */
    private final ShopTrendyBoxItemMapper itemMapper;

    /** 只用来看「抽取记录」那一屏 */
    private final ShopTrendyBoxUserMapper recordMapper;

    /** 改系列时核名下款式挂的规格用：规格 → 商品 → 商品所属系列 */
    private final ShopProductSkuMapper skuMapper;

    private final ShopProductMapper productMapper;

    @Override
    public IPage<ShopTrendyBox> selectPage(ShopTrendyBox query) {
        if (query == null) {
            query = new ShopTrendyBox();
        }
        IPage<ShopTrendyBox> page = page(PageUtil.getPage(), new LambdaQueryWrapper<ShopTrendyBox>()
                .like(StringUtils.isNotBlank(query.getBoxName()), ShopTrendyBox::getBoxName, query.getBoxName())
                .eq(query.getTrendyId() != null, ShopTrendyBox::getTrendyId, query.getTrendyId())
                .eq(query.getStoreId() != null, ShopTrendyBox::getStoreId, query.getStoreId())
                .eq(query.getBoxType() != null, ShopTrendyBox::getBoxType, query.getBoxType())
                .eq(query.getStatus() != null, ShopTrendyBox::getStatus, query.getStatus())
                // 这里不按 status 打头：这一列是 1未开始/2进行中/3已结束/4已取消 的生命周期档，
                // 不是「启用在前」那种两态开关，按它排会把已取消的顶到最上面。同状态内仍按 sort、id
                .orderByAsc(ShopTrendyBox::getSort)
                .orderByDesc(ShopTrendyBox::getId));
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public void insert(ShopTrendyBox box) {
        validate(box);
        applyDefaults(box);
        box.setBoxNo(GeneratorNoUtils.getTrendyBoxNo());
        try {
            save(box);
        } catch (DuplicateKeyException e) {
            // 编号是「时间戳+3位随机数」，同一秒连着建就可能撞 uk_box_no
            throw new ServiceException("盲盒编号生成撞车，请重新提交");
        }
    }

    @Override
    public void update(ShopTrendyBox box) {
        if (box.getId() == null) {
            throw new ServiceException("盲盒ID不能为空");
        }
        ShopTrendyBox old = getById(box.getId());
        if (old == null) {
            throw new ServiceException("盲盒不存在");
        }
        validate(box);
        // 只在系列真的换了（新挂上、从一个系列换到另一个）时核名下款式：系列没动，就不该拿老数据拦一次无关的保存
        if (!Objects.equals(old.getTrendyId(), box.getTrendyId())) {
            assertItemsMatchTrendy(box.getId(), box.getTrendyId());
        }
        applyDefaults(box);
        updateById(box);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 款式是这只盒的组成部分（box_id 必填、离开这只盒没有意义），所以跟着一起软删，不拦
        itemMapper.delete(new LambdaQueryWrapper<ShopTrendyBoxItem>().in(ShopTrendyBoxItem::getBoxId, ids));
        removeByIds(ids);
    }

    @Override
    public IPage<ShopTrendyBoxUser> pageRecords(ShopTrendyBoxUser query) {
        if (query == null) {
            query = new ShopTrendyBoxUser();
        }
        IPage<ShopTrendyBoxUser> page = recordMapper.selectPage(PageUtil.getPage(),
                new LambdaQueryWrapper<ShopTrendyBoxUser>()
                        .eq(query.getBoxId() != null, ShopTrendyBoxUser::getBoxId, query.getBoxId())
                        .eq(query.getUserId() != null, ShopTrendyBoxUser::getUserId, query.getUserId())
                        .eq(query.getIsHidden() != null, ShopTrendyBoxUser::getIsHidden, query.getIsHidden())
                        // 流水按时间倒序看最新的；id 就是插入顺序，比 create_time 更稳（同一秒里多条也能定序）
                        .orderByDesc(ShopTrendyBoxUser::getId));
        fillRecordNames(page.getRecords());
        return page;
    }

    /**
     * 抽取记录里那两格名字：谁抽的、哪个盒。都是按当页 id 一次查回来，不联表。
     * 记录本身带的款式三格是快照，不用回查款式表。
     */
    private void fillRecordNames(List<ShopTrendyBoxUser> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Integer> userIds = new HashSet<>();
        Set<Long> boxIds = new HashSet<>();
        for (ShopTrendyBoxUser row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId().intValue());
            }
            if (row.getBoxId() != null) {
                boxIds.add(row.getBoxId());
            }
        }
        Map<Long, String> userNameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNameById.put(user.getId().longValue(),
                        StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
            }
        }
        Map<Long, String> boxNameById = new HashMap<>();
        if (!boxIds.isEmpty()) {
            for (ShopTrendyBox box : list(new LambdaQueryWrapper<ShopTrendyBox>()
                    .select(ShopTrendyBox::getId, ShopTrendyBox::getBoxName).in(ShopTrendyBox::getId, boxIds))) {
                boxNameById.put(box.getId(), box.getBoxName());
            }
        }
        for (ShopTrendyBoxUser row : rows) {
            row.setUserName(userNameById.get(row.getUserId()));
            row.setBoxName(boxNameById.get(row.getBoxId()));
        }
    }

    /**
     * 回填列表那三格：系列名、门店名、名下款式数。
     * 都按当页出现过的 id 一次查回来，不在 XML 里联表（形状同 ShopStoreServiceImpl 的 fillBrand/fillManagerName）。
     */
    private void fillNames(List<ShopTrendyBox> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(rows.size());
        Set<Long> trendyIds = new HashSet<>();
        Set<Long> storeIds = new HashSet<>();
        Set<Integer> userIds = new HashSet<>();
        for (ShopTrendyBox row : rows) {
            ids.add(row.getId());
            if (row.getTrendyId() != null) {
                trendyIds.add(row.getTrendyId());
            }
            if (row.getStoreId() != null) {
                storeIds.add(row.getStoreId());
            }
            if (row.getUserId() != null) {
                // sys_user.id 是 Integer，按 id 批量查就要用 Integer 集合（口径同 ShopStoreServiceImpl#fillManagerName）
                userIds.add(row.getUserId().intValue());
            }
        }
        Map<Long, String> trendyNameById = new HashMap<>();
        if (!trendyIds.isEmpty()) {
            for (ShopTrendy trendy : trendyMapper.selectList(new LambdaQueryWrapper<ShopTrendy>()
                    .select(ShopTrendy::getId, ShopTrendy::getTrendyName).in(ShopTrendy::getId, trendyIds))) {
                trendyNameById.put(trendy.getId(), trendy.getTrendyName());
            }
        }
        Map<Long, String> storeNameById = new HashMap<>();
        if (!storeIds.isEmpty()) {
            for (ShopStore store : storeMapper.selectList(new LambdaQueryWrapper<ShopStore>()
                    .select(ShopStore::getId, ShopStore::getStoreName).in(ShopStore::getId, storeIds))) {
                storeNameById.put(store.getId(), store.getStoreName());
            }
        }
        Map<Long, String> userNameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNameById.put(user.getId().longValue(),
                        StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
            }
        }
        Map<Long, Integer> itemCountByBox = new HashMap<>();
        for (Map<String, Object> one : itemMapper.selectMaps(new QueryWrapper<ShopTrendyBoxItem>()
                .select("box_id AS boxId", "COUNT(1) AS cnt").in("box_id", ids).groupBy("box_id"))) {
            Object boxId = one.get("boxId");
            Object cnt = one.get("cnt");
            if (boxId != null && cnt != null) {
                itemCountByBox.put(((Number) boxId).longValue(), ((Number) cnt).intValue());
            }
        }
        for (ShopTrendyBox row : rows) {
            row.setTrendyName(trendyNameById.get(row.getTrendyId()));
            row.setStoreName(storeNameById.get(row.getStoreId()));
            row.setUserName(userNameById.get(row.getUserId()));
            row.setItemCount(itemCountByBox.getOrDefault(row.getId(), 0));
        }
    }

    /**
     * 新增和修改共用的判据：名称、价格、三格外键、时间窗
     */
    private void validate(ShopTrendyBox box) {
        if (StringUtils.isBlank(box.getBoxName())) {
            throw new ServiceException("盲盒名称不能为空");
        }
        if (box.getPrice() == null) {
            throw new ServiceException("单抽价格不能为空");
        }
        if (box.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("单抽价格要大于 0");
        }
        if (box.getEndBoxPrice() == null) {
            throw new ServiceException("端盒价格不能为空");
        }
        if (box.getEndBoxPrice().compareTo(box.getPrice()) < 0) {
            throw new ServiceException("端盒价格不能低于单抽价格");
        }
        if (box.getTrendyId() != null && trendyMapper.selectById(box.getTrendyId()) == null) {
            throw new ServiceException("所选潮玩系列不存在");
        }
        if (box.getStoreId() != null && storeMapper.selectById(box.getStoreId()) == null) {
            throw new ServiceException("所选门店不存在");
        }
        if (box.getUserId() != null && sysUserMapper.selectById(box.getUserId()) == null) {
            throw new ServiceException("所选负责运营不存在");
        }
        if (box.getStartTime() != null && box.getEndTime() != null && box.getEndTime().isBefore(box.getStartTime())) {
            throw new ServiceException("结束时间不能早于开始时间");
        }
    }

    /**
     * 改系列时回头核一遍名下款式：款式挂的规格必须仍属于新选的这个系列，
     * 否则改完就留下一批「不在本系列里」的规格，对外那句「本系列盲盒」名不副实。
     * 系列改成空（不归属某个系列）不拦 —— 这是收窄方向的规则，放宽时不适用。
     * 没挂规格的老款式也不拦：那一条本来由款式自己的保存去管，这里只比已有规格归属。
     */
    private void assertItemsMatchTrendy(Long boxId, Long trendyId) {
        if (trendyId == null) {
            return;
        }
        List<ShopTrendyBoxItem> items = itemMapper.selectList(new LambdaQueryWrapper<ShopTrendyBoxItem>()
                .select(ShopTrendyBoxItem::getId, ShopTrendyBoxItem::getStyleName, ShopTrendyBoxItem::getSkuId)
                .eq(ShopTrendyBoxItem::getBoxId, boxId)
                .isNotNull(ShopTrendyBoxItem::getSkuId));
        if (items.isEmpty()) {
            return;
        }
        Set<Long> skuIds = new HashSet<>();
        for (ShopTrendyBoxItem item : items) {
            skuIds.add(item.getSkuId());
        }
        Map<Long, Long> productIdBySku = new HashMap<>();
        for (ShopProductSku sku : skuMapper.selectList(new LambdaQueryWrapper<ShopProductSku>()
                .select(ShopProductSku::getId, ShopProductSku::getProductId)
                .in(ShopProductSku::getId, skuIds))) {
            productIdBySku.put(sku.getId(), sku.getProductId());
        }
        Set<Long> productIds = new HashSet<>();
        for (Long productId : productIdBySku.values()) {
            if (productId != null) {
                productIds.add(productId);
            }
        }
        Map<Long, Long> sectionIdByProduct = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (ShopProduct product : productMapper.selectList(new LambdaQueryWrapper<ShopProduct>()
                    .select(ShopProduct::getId, ShopProduct::getSectionId)
                    .in(ShopProduct::getId, productIds))) {
                sectionIdByProduct.put(product.getId(), product.getSectionId());
            }
        }
        List<String> unmatched = new ArrayList<>();
        for (ShopTrendyBoxItem item : items) {
            Long productId = productIdBySku.get(item.getSkuId());
            if (productId != null && trendyId.equals(sectionIdByProduct.get(productId))) {
                continue;
            }
            unmatched.add(StringUtils.isBlank(item.getStyleName()) ? "款式 #" + item.getId() : item.getStyleName());
        }
        if (unmatched.isEmpty()) {
            return;
        }
        ShopTrendy trendy = trendyMapper.selectById(trendyId);
        // 名字只列前三个：一场端盒能挂十几个款，全列出来这句提示就没法读了
        String names = unmatched.size() > 3
                ? String.join("、", unmatched.subList(0, 3)) + " 等 " + unmatched.size() + " 款"
                : String.join("、", unmatched);
        throw new ServiceException("换成" + (trendy == null ? "该系列" : "「" + trendy.getTrendyName() + "」")
                + "后，" + names + " 挂的规格不属于这个系列，请先在「盲盒商品」里换掉规格");
    }

    /**
     * 页面没给的几格补默认值（和建表默认一致）。
     * drawnCount 不在这里碰：新增靠库的默认 0，修改时它是 NEVER、表单带什么都不算。
     */
    private void applyDefaults(ShopTrendyBox box) {
        if (box.getBoxType() == null) {
            box.setBoxType(1);
        }
        if (box.getStatus() == null) {
            box.setStatus(1);
        }
        if (box.getDrawCount() == null) {
            box.setDrawCount(1);
        }
        if (box.getIsProbabilityShow() == null) {
            box.setIsProbabilityShow(1);
        }
        if (box.getTotalCount() == null) {
            box.setTotalCount(0);
        }
        if (box.getSort() == null) {
            box.setSort(0);
        }
    }
}
