package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.api.service.ShopSearchReadService;
import com.shop.entity.ShopActivity;
import com.shop.entity.ShopBrand;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopStore;
import com.shop.entity.SysNotice;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityMapper;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysNoticeMapper;
import com.shop.utils.PageUtil;
import com.shop.vo.activity.ShopActivityDetailWebVo;
import com.shop.vo.activity.ShopActivityWebVo;
import com.shop.vo.brand.ShopBrandWebVo;
import com.shop.vo.category.ShopCategoryWebVo;
import com.shop.vo.notice.NoticeDetailWebVo;
import com.shop.vo.store.ShopStoreWebVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店 / 品牌 / 分类 / 活动前台读（Web 搜索结果页 + 门店/品牌/活动详情页 + 首页筛选栏与那一块活动轮播）
 *
 * 走 BaseMapper 而不是自定义 XML：这一层不联表（前台这两个 tab 只要本表那几格），
 * 用 BaseMapper 才自动带得上全局逻辑删除条件，也不必为一条 like 再开一份 XML。
 * 分页同样从请求串里取 pageNum/pageSize（PageUtil），和后台列表、前台商品列表一套写法。
 */
@Service
@RequiredArgsConstructor
public class ShopSearchReadServiceImpl implements ShopSearchReadService {

    private final ShopStoreMapper shopStoreMapper;

    private final ShopBrandMapper shopBrandMapper;

    private final ShopCategoryMapper shopCategoryMapper;

    private final ShopActivityMapper shopActivityMapper;

    private final SysNoticeMapper sysNoticeMapper;

    @Override
    public IPage<ShopStoreWebVo> pageStores(String keyword) {
        LambdaQueryWrapper<ShopStore> wrapper = new LambdaQueryWrapper<ShopStore>()
                // 列清单钉死：没有的列不进 SELECT，也就不会因为哪天给 VO 加了格而顺带把实体别的列捞出来
                .select(ShopStore::getId, ShopStore::getStoreName, ShopStore::getStoreType,
                        ShopStore::getLogo, ShopStore::getAreaZh, ShopStore::getAddress,
                        ShopStore::getContactPhone, ShopStore::getBusinessHours,
                        ShopStore::getBusinessStatus, ShopStore::getIsDelivery)
                .eq(ShopStore::getStatus, 1)
                // 命中口径照后台门店列表（名称/编码），前台多给一条地址：消费者搜店搜的就是「我家附近那条街」
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(ShopStore::getStoreName, keyword)
                        .or().like(ShopStore::getStoreCode, keyword)
                        .or().like(ShopStore::getAddress, keyword))
                .orderByAsc(ShopStore::getSort)
                .orderByDesc(ShopStore::getId);
        return toVoPage(shopStoreMapper.selectPage(PageUtil.getPage(), wrapper), ShopSearchReadServiceImpl::toStoreVo);
    }

    @Override
    public ShopStoreWebVo storeDetail(Long id) {
        // 判据和列表那条一样（只看 status=1、软删由 MP 的谓词兜住），只有列清单多一格 coverImages：
        // 列表一行挂一串图片 JSON 是白给十几 KB，详情页才真要它，所以两份清单分开钉。
        ShopStore store = id == null ? null : shopStoreMapper.selectOne(new LambdaQueryWrapper<ShopStore>()
                .select(ShopStore::getId, ShopStore::getStoreName, ShopStore::getStoreType,
                        ShopStore::getLogo, ShopStore::getCoverImages, ShopStore::getAreaZh,
                        ShopStore::getAddress, ShopStore::getContactPhone, ShopStore::getBusinessHours,
                        ShopStore::getBusinessStatus, ShopStore::getIsDelivery)
                .eq(ShopStore::getId, id)
                .eq(ShopStore::getStatus, 1));
        if (store == null) {
            // 不存在、已删、已停用回同一句话（和商品详情那条同口径），不让调用方拿错误信息试出哪个 id 存在过
            throw new ServiceException("门店不存在或已停用");
        }
        return toStoreVo(store);
    }

    @Override
    public IPage<ShopBrandWebVo> pageBrands(String keyword) {
        LambdaQueryWrapper<ShopBrand> wrapper = new LambdaQueryWrapper<ShopBrand>()
                .select(ShopBrand::getId, ShopBrand::getBrandName, ShopBrand::getLogo, ShopBrand::getDescription)
                .eq(ShopBrand::getStatus, 1)
                .like(StringUtils.isNotBlank(keyword), ShopBrand::getBrandName, keyword)
                .orderByAsc(ShopBrand::getSort)
                .orderByDesc(ShopBrand::getId);
        return toVoPage(shopBrandMapper.selectPage(PageUtil.getPage(), wrapper), ShopSearchReadServiceImpl::toBrandVo);
    }

    @Override
    public ShopBrandWebVo brandDetail(Long id) {
        // 判据和列表那条一样（只看 status=1、软删由 MP 的谓词兜住），只有列清单多一格 brandSpokesperson：
        // 搜索结果页那张卡片不渲染代言人，一行挂一串图片地址是白给（同门店 coverImages 那一条理由）。
        ShopBrand brand = id == null ? null : shopBrandMapper.selectOne(new LambdaQueryWrapper<ShopBrand>()
                .select(ShopBrand::getId, ShopBrand::getBrandName, ShopBrand::getLogo,
                        ShopBrand::getDescription, ShopBrand::getBrandSpokesperson)
                .eq(ShopBrand::getId, id)
                .eq(ShopBrand::getStatus, 1));
        if (brand == null) {
            // 不存在、已删、已停用回同一句话（和门店详情那条同口径），不让调用方拿错误信息试出哪个 id 存在过
            throw new ServiceException("品牌不存在或已停用");
        }
        return toBrandVo(brand);
    }

    @Override
    public List<ShopStoreWebVo> brandStores(Long brandId) {
        if (brandId == null) {
            return Collections.emptyList();
        }
        // 「品牌门店」读的是 shop_store.brand_id（这家店自己挂的品牌，门店表单里那一格），
        // 不再绕商品表数「哪个店在卖这个品牌的货」—— 那一层含义和这一列不是一回事，
        // 而且没绑品牌的店照样可能在卖某品牌的货，两条线迟早对不上。
        // 一条 Wrapper 查询就够，不用联表；列清单和门店列表那条同一份（卡片只有 logo + 名称 + 地址）。
        // 品牌不存在/没绑任何门店都回空列表，不另分一档错误：这一块的空白本来就是合法状态。
        return shopStoreMapper.selectList(new LambdaQueryWrapper<ShopStore>()
                        .select(ShopStore::getId, ShopStore::getStoreName, ShopStore::getStoreType,
                                ShopStore::getLogo, ShopStore::getAreaZh, ShopStore::getAddress,
                                ShopStore::getContactPhone, ShopStore::getBusinessHours,
                                ShopStore::getBusinessStatus, ShopStore::getIsDelivery)
                        .eq(ShopStore::getBrandId, brandId)
                        // 同门店列表那条判据：只给启用中的店
                        .eq(ShopStore::getStatus, 1)
                        .orderByAsc(ShopStore::getSort)
                        .orderByDesc(ShopStore::getId))
                .stream()
                .map(ShopSearchReadServiceImpl::toStoreVo)
                .collect(Collectors.toList());
    }

    @Override
    public List<ShopCategoryWebVo> listCategories() {
        // 一次把启用中的分类捞全（不分页、不联表），下面在内存里排成「父在前、下级紧跟」
        List<ShopCategory> enabled = shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategory>()
                .select(ShopCategory::getId, ShopCategory::getParentId,
                        ShopCategory::getCategoryName, ShopCategory::getLevel)
                .eq(ShopCategory::getStatus, 1)
                .orderByAsc(ShopCategory::getSort)
                .orderByAsc(ShopCategory::getId));

        Set<Long> enabledIds = enabled.stream().map(ShopCategory::getId).collect(Collectors.toSet());
        List<ShopCategory> tops = new ArrayList<>();
        Map<Long, List<ShopCategory>> childrenOf = new LinkedHashMap<>();
        for (ShopCategory one : enabled) {
            Long parentId = one.getParentId();
            // 父不在这批候选里（根、或父已禁用/已删）就当顶层接住，否则那一支的下级会整条从下拉框里消失
            if (parentId == null || parentId == 0L || !enabledIds.contains(parentId)) {
                tops.add(one);
            } else {
                childrenOf.computeIfAbsent(parentId, k -> new ArrayList<>()).add(one);
            }
        }

        List<ShopCategoryWebVo> flat = new ArrayList<>(enabled.size());
        appendBranch(tops, childrenOf, flat);
        return flat;
    }

    @Override
    public List<ShopActivityWebVo> listHomeActivities() {
        // 三道判据都在注释里（位置=首页轮播、状态=投放中、必须有图），排序照后台列表那套
        return shopActivityMapper.selectList(new LambdaQueryWrapper<ShopActivity>()
                        .select(ShopActivity::getId, ShopActivity::getActivityName, ShopActivity::getTitle,
                                ShopActivity::getImage, ShopActivity::getLinkType, ShopActivity::getLinkValue)
                        .eq(ShopActivity::getPositionType, 1)
                        .eq(ShopActivity::getStatus, 2)
                        .isNotNull(ShopActivity::getImage)
                        .ne(ShopActivity::getImage, "")
                        .orderByDesc(ShopActivity::getWeight)
                        .orderByDesc(ShopActivity::getId))
                .stream()
                .map(ShopSearchReadServiceImpl::toActivityVo)
                .collect(Collectors.toList());
    }

    @Override
    public IPage<ShopActivityWebVo> pageActivities(String keyword) {
        // 判据只差一道（不限活动位、也不要求配了图），其余和首页轮播那条同源，理由见接口上的注释。
        // 列清单里比轮播那条多两格投放起止 —— 列表卡片上那一行「10-01 ~ 10-31」的说法就是它俩
        LambdaQueryWrapper<ShopActivity> wrapper = new LambdaQueryWrapper<ShopActivity>()
                .select(ShopActivity::getId, ShopActivity::getActivityName, ShopActivity::getTitle,
                        ShopActivity::getImage, ShopActivity::getLinkType, ShopActivity::getLinkValue,
                        ShopActivity::getStartTime, ShopActivity::getEndTime)
                .eq(ShopActivity::getStatus, 2)
                // 名称或标题命中都行：搜索页那个关键字框不知道运营把词写进哪一格，挑一档就会漏
                .and(StringUtils.isNotBlank(keyword), w -> w.like(ShopActivity::getActivityName, keyword)
                        .or()
                        .like(ShopActivity::getTitle, keyword))
                .orderByDesc(ShopActivity::getWeight)
                .orderByDesc(ShopActivity::getId);
        return toVoPage(shopActivityMapper.selectPage(PageUtil.getPage(), wrapper),
                ShopSearchReadServiceImpl::toActivityVo);
    }

    @Override
    public ShopActivityDetailWebVo activityDetail(Long id) {
        // 判据同列表那条（只认 status=2），列清单多一份 content —— 前台那一页要渲染它，也只有这一条给
        ShopActivity one = id == null ? null : shopActivityMapper.selectOne(new LambdaQueryWrapper<ShopActivity>()
                .select(ShopActivity::getId, ShopActivity::getActivityName, ShopActivity::getTitle,
                        ShopActivity::getImage, ShopActivity::getVideoUrl, ShopActivity::getContent,
                        ShopActivity::getLinkType, ShopActivity::getLinkValue,
                        ShopActivity::getStartTime, ShopActivity::getEndTime)
                .eq(ShopActivity::getId, id)
                .eq(ShopActivity::getStatus, 2));
        if (one == null) {
            // 不存在、已删、已撤下（状态离开 2）回同一句话，同门店/品牌详情那条口径
            throw new ServiceException("活动不存在或没在投放中");
        }
        ShopActivityDetailWebVo vo = new ShopActivityDetailWebVo();
        BeanUtils.copyProperties(one, vo);
        return vo;
    }

    @Override
    public NoticeDetailWebVo noticeDetail(Long id) {
        // 公告没有 status 那一档，上下线就是 is_show 这颗开关（后台 /sys/notice/show 改的就是它），判据只认这一条。
        // noticePush 不 select 出来：那是「这条发给谁」的推送范围，不该从不要登录的端点漏出去
        SysNotice one = id == null ? null : sysNoticeMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
                .select(SysNotice::getId, SysNotice::getTitle, SysNotice::getContent, SysNotice::getCreateTime)
                .eq(SysNotice::getId, id)
                .eq(SysNotice::getIsShow, 1));
        if (one == null) {
            // 不存在、已删、已下线回同一句话，同活动/门店/品牌详情那条口径
            throw new ServiceException("公告不存在或已停止展示");
        }
        NoticeDetailWebVo vo = new NoticeDetailWebVo();
        BeanUtils.copyProperties(one, vo);
        return vo;
    }

    /** 深度优先把一层层拼进 flat：进来这批已经按 sort 排好，所以同层顺序就是后台列表那套 */
    private static void appendBranch(List<ShopCategory> level, Map<Long, List<ShopCategory>> childrenOf,
                                     List<ShopCategoryWebVo> flat) {
        for (ShopCategory one : level) {
            flat.add(toCategoryVo(one));
            List<ShopCategory> children = childrenOf.get(one.getId());
            if (children != null) {
                appendBranch(children, childrenOf, flat);
            }
        }
    }

    private static ShopStoreWebVo toStoreVo(ShopStore store) {
        ShopStoreWebVo vo = new ShopStoreWebVo();
        BeanUtils.copyProperties(store, vo);
        return vo;
    }

    private static ShopBrandWebVo toBrandVo(ShopBrand brand) {
        ShopBrandWebVo vo = new ShopBrandWebVo();
        BeanUtils.copyProperties(brand, vo);
        return vo;
    }

    private static ShopCategoryWebVo toCategoryVo(ShopCategory category) {
        ShopCategoryWebVo vo = new ShopCategoryWebVo();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }

    private static ShopActivityWebVo toActivityVo(ShopActivity activity) {
        ShopActivityWebVo vo = new ShopActivityWebVo();
        BeanUtils.copyProperties(activity, vo);
        return vo;
    }

    /**
     * 实体分页换成 VO 分页：total/current/pages 要照原样搬过去，否则前端那套「还有没有下一页」的判断就废了。
     */
    private static <E, V> IPage<V> toVoPage(IPage<E> source, Function<E, V> mapper) {
        Page<V> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setPages(source.getPages());
        target.setRecords(source.getRecords().stream().map(mapper).collect(Collectors.toList()));
        return target;
    }
}
