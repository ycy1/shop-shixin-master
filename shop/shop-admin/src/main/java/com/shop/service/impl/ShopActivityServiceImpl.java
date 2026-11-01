package com.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopActivity;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.service.ShopActivityService;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 活动 服务实现（写法照门店培训 ShopStoreTrainingServiceImpl：必填校验、可空列靠 IGNORED 清空）
 *
 * 和数据范围：这一页和门店培训、门店招商一样不按账号类型收窄（平台看全部）—— 活动是平台排期给各家店看的。
 * 行上那一格「适用门店」是这条活动的投放范围（NULL = 全平台），不是它的可见范围，
 * 所以它清空是合法操作（专场改回全平台），实体上挂的是 IGNORED，和培训那个必填的所属门店不是一回事。
 *
 * 曝光和点击的流水都写在 shop_activity_click（一行一次事件，靠 event_type 分档），写入方是前台那条
 * 埋点接口 POST /api/comm/activity/track，这一份只管后台的读和改单，
 * 所以统计那三格是查询时按那张表现算带出来的 —— 活动表里连这三列都没有（实体上它们是 exist=false 的别名承载），
 * 改单自然改不动它们。
 */
@Service
@RequiredArgsConstructor
public class ShopActivityServiceImpl extends ServiceImpl<ShopActivityMapper, ShopActivity>
        implements ShopActivityService {

    /** 状态：1 未开始 —— 新建默认的，也是"还没投放过"那一档（判能不能删看 launch_time 有没有值，不看这一格） */
    private static final int STATUS_NOT_START = 1;

    /** 状态：2 投放中 —— 第一次进这一档时服务端盖投放时间 */
    private static final int STATUS_LAUNCHING = 2;

    /** 跳转类型：6 无跳转 —— 这一档跳转值留空是正常的，其余档都指向某个东西 */
    private static final int LINK_NONE = 6;

    /** 只用来校验「适用门店」那一格选中的店还在（注入 mapper 而不是 service，避免两边互相依赖） */
    private final ShopStoreMapper shopStoreMapper;

    @Override
    public IPage<ShopActivity> selectPage(ShopActivity query) {
        if (query == null) {
            query = new ShopActivity();
        }
        // 关键字跨名称/标题两列搜 + 投放开始时间区间，还要联门店和现算点击量，条件写 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectActivityPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopActivity selectActivityById(Long id) {
        ShopActivity activity = baseMapper.selectActivityById(id);
        if (activity == null) {
            throw new ServiceException("活动不存在");
        }
        return activity;
    }

    @Override
    public void insert(ShopActivity activity) {
        validate(activity);
        normalize(activity);
        // 新增一律落 1 未开始：开始投放是「修改」里把状态选成 2 那一步，所以这一格此刻还没有投放时间
        activity.setStatus(STATUS_NOT_START);
        activity.setLaunchTime(null);
        // 统计那三格不用在这里挡：活动表没有这三列，实体上它们是 exist=false，请求体带什么都不进 SQL
        save(activity);
    }

    @Override
    public void update(ShopActivity activity) {
        if (activity.getId() == null) {
            throw new ServiceException("活动ID不能为空");
        }
        ShopActivity exists = getById(activity.getId());
        if (exists == null) {
            throw new ServiceException("活动不存在");
        }
        validate(activity);
        normalize(activity);
        fillLaunchTime(activity, exists);
        updateById(activity);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 只删从没投放过的（launch_time 还是空）：一旦投放过就有点击记录指着这条活动，
        // 删了那一头的 activity_id 就成了指向不存在的活动的孤儿（判据同"入过货的单子删不掉"）。
        // 判的是库里那行的投放时间，不是请求体里那格状态 —— 把状态改回 1 未开始也照样删不掉
        List<ShopActivity> activities = listByIds(ids);
        List<String> launched = activities.stream()
                .filter(activity -> activity.getLaunchTime() != null)
                .map(ShopActivity::getActivityName)
                .collect(Collectors.toList());
        if (!launched.isEmpty()) {
            throw new ServiceException("只有还没投放过的活动能删，这几条已经投放过了：" + String.join("、", launched));
        }
        removeByIds(ids);
    }

    /**
     * 投放时间按 DDL 的注释来：状态第一次流转到 2 投放中时服务端盖一次，之后不再覆盖。
     * 表单里没这一格，但「修改」是整行提交、会把库里的旧值原样带回请求体，
     * 所以这里一律不认请求体，只按查出来的那一行往下走：沿用旧值，只有"从没投放过"的才当场盖时间。
     */
    private void fillLaunchTime(ShopActivity activity, ShopActivity exists) {
        activity.setLaunchTime(exists.getLaunchTime());
        if (activity.getStatus() != null && activity.getStatus() == STATUS_LAUNCHING && exists.getLaunchTime() == null) {
            activity.setLaunchTime(LocalDateTime.now());
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报"不能为空"
     */
    private void normalize(ShopActivity activity) {
        if (activity.getPositionType() == null) {
            activity.setPositionType(1);
        }
        if (activity.getActivityType() == null) {
            activity.setActivityType(1);
        }
        if (activity.getLinkType() == null) {
            activity.setLinkType(1);
        }
        if (activity.getWeight() == null) {
            activity.setWeight(0);
        }
    }

    private void validate(ShopActivity activity) {
        if (StringUtils.isBlank(activity.getActivityName())) {
            throw new ServiceException("活动名称不能为空");
        }
        activity.setActivityName(activity.getActivityName().trim());
        // 适用门店可空（NULL = 全平台，这一档和培训那格的必填不一样），填了就要真有其店
        if (activity.getStoreId() != null && shopStoreMapper.selectById(activity.getStoreId()) == null) {
            throw new ServiceException("所选适用门店不存在");
        }
        // 跳转类型和跳转值只在"无跳转"那一档允许留空，其余档这一格是指向某个东西的，空着前台点了没反应。
        // 前端已经换成商品/分类选择器（存的是 id），但这里故意不校验那一格存的是 id 还是 URL、指向的东西在不在
        if (activity.getLinkType() != null && activity.getLinkType() != LINK_NONE && StringUtils.isBlank(activity.getLinkValue())) {
            throw new ServiceException("跳转值为空，这条活动点了跳不过去");
        }
        // 两头时间都填齐了才判先后，漏一头是"还没定"，不算错
        if (activity.getStartTime() != null && activity.getEndTime() != null
                && activity.getEndTime().isBefore(activity.getStartTime())) {
            throw new ServiceException("投放结束时间不能早于开始时间");
        }
        // 这张表没有编号列、也没有唯一键，活动名称故意不查重（同一张主视觉投到几个位是常态，同培训名称不查重）
    }
}
