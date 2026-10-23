package com.shop.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.activity.ShopActivityCouponGiveDto;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopActivityCouponService;
import com.shop.utils.CouponUtils;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 优惠券模板 服务实现（写法照活动 ShopActivityServiceImpl：必填校验、可空列靠 IGNORED 清空、状态整行提交）
 *
 * 和数据范围：这一页和活动、门店培训、门店招商一样不按账号类型收窄（平台看全部）—— 券是平台发的活动物资。
 * 行上那两格「适用门店」「负责的运营」是这张券给谁用、谁经手，不是这一行的可见范围，所以都挂 IGNORED（清空合法）。
 *
 * 数量那三格里 total_count 是表单能改的额度，issued_count/used_count 不是：
 * 「发放」占额度走的是 increaseIssuedCount 那条带总量校验的原子 UPDATE，用券核销走 increaseUsedCount
 *（在 shop-api 的统一下单那一路，ShopOrderCouponService#redeem），实体上这两格钉 NEVER，
 * 请求体里带什么都不进 UPDATE。所以这一页存的是额度、活动那一页存的是流水，两路口径相反。
 *
 * scope_value 是 json 列，手填一段不是 JSON 的文本进去 MySQL 直接报 3140，
 * 所以表单只收逗号分隔的 id（scopeIds），由这一份负责双向翻译。
 */
@Service
@RequiredArgsConstructor
public class ShopActivityCouponServiceImpl extends ServiceImpl<ShopActivityCouponMapper, ShopActivityCoupon>
        implements ShopActivityCouponService {

    /** 状态：1 未开始 —— 新增一律落这一档；这一档也允许发放（先把券发到用户手上、有效期还没到是常态） */
    private static final int STATUS_NOT_START = 1;

    /** 状态：3 已结束 —— 这一档起就不再发券 */
    private static final int STATUS_FINISHED = 3;

    /** 状态：4 已取消 */
    private static final int STATUS_CANCELED = 4;

    /** 有效期类型：1 固定时间段（过期时间取 valid_end） */
    private static final int VALID_FIXED = 1;

    /** 有效期类型：2 领取后 N 天（过期时间 = 领取时间 + valid_days） */
    private static final int VALID_AFTER_DAYS = 2;

    /** 券类型：2 折扣券 —— 这一档 face_value 存的是折扣率（0.9=9折），不是金额 */
    private static final int TYPE_DISCOUNT = 2;

    /** 券类型：3 免邮券 —— 这一档抵的是配送费，face_value 留 0 是对的 */
    private static final int TYPE_FREE_SHIPPING = 3;

    /** 适用范围：2 指定商品 / 3 指定分类 —— 只有这两档 scope_value 里存 id 列表 */
    private static final int SCOPE_PRODUCT = 2;
    private static final int SCOPE_CATEGORY = 3;

    /** json 里那两个键名（前端约定同这份，读的那一份在 CouponUtils，改要三处一起改） */
    private static final String KEY_PRODUCT_IDS = "productIds";
    private static final String KEY_CATEGORY_IDS = "categoryIds";

    /** 用户券状态：0 未使用 —— 发出去就落这一档（也是库里的默认值） */
    private static final int USER_STATUS_UNUSED = 0;

    /** 领取方式：2 系统发放 —— 后台这颗按钮发出去的那一批都落这一档，和前台用户自己领（1）区分开 */
    private static final int GET_WAY_SYSTEM = 2;

    /** 只用来校验「适用门店」那一格选中的店还在（注入 mapper 而不是 service，避免两边互相依赖） */
    private final ShopStoreMapper shopStoreMapper;

    /** 校验「负责的运营」和发放时那批持券用户是否真有其人 */
    private final SysUserMapper sysUserMapper;

    /** 发放要往这张表插行、删除要按这张表判"发出去过没有"，所以直接注 mapper */
    private final ShopActivityCouponUserMapper shopActivityCouponUserMapper;

    @Override
    public IPage<ShopActivityCoupon> selectPage(ShopActivityCoupon query) {
        if (query == null) {
            query = new ShopActivityCoupon();
        }
        // 关键字跨券名/券码两列搜 + 有效期开始区间，还要联门店和运营，条件写 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        IPage<ShopActivityCoupon> page = baseMapper.selectCouponPage(PageUtil.getPage(), query);
        page.getRecords().forEach(this::readScopeIds);
        return page;
    }

    @Override
    public ShopActivityCoupon selectCouponById(Long id) {
        ShopActivityCoupon coupon = baseMapper.selectCouponById(id);
        if (coupon == null) {
            throw new ServiceException("优惠券不存在");
        }
        readScopeIds(coupon);
        return coupon;
    }

    @Override
    public void insert(ShopActivityCoupon coupon) {
        // 新增时表单那格是禁用的、不会传券码，和供应商/物流公司编码一样由系统生成
        if (StringUtils.isBlank(coupon.getCouponCode())) {
            coupon.setCouponCode(GeneratorNoUtils.getCouponNo());
        }
        validate(coupon, null);
        normalize(coupon);
        // 新增一律落 1 未开始：进 2 发放中是「修改」里把状态选上去那一步，发放本身不推状态
        coupon.setStatus(STATUS_NOT_START);
        save(coupon);
    }

    @Override
    public void update(ShopActivityCoupon coupon) {
        if (coupon.getId() == null) {
            throw new ServiceException("优惠券ID不能为空");
        }
        ShopActivityCoupon exists = getById(coupon.getId());
        if (exists == null) {
            throw new ServiceException("优惠券不存在");
        }
        // 券码允许不传（表单里那格是禁用的），不传时沿用原值参与查重（not_empty 策略下不传本来也不会写这一列）
        if (StringUtils.isBlank(coupon.getCouponCode())) {
            coupon.setCouponCode(exists.getCouponCode());
        }
        validate(coupon, coupon.getId());
        normalize(coupon);
        // 已发放/已使用那两格不在这儿管：实体上它们钉了 NEVER，永远进不了 UPDATE 的 SET，
        // 请求体里带什么都不动额度（要动只有「发放」那一条原子 UPDATE）
        updateById(coupon);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 只删还没发出去过的那几张：一旦发到用户手上，用户券表里就有行指着这张模板，
        // 删了那一头的 coupon_id 就成了指向不存在模板的孤儿（判据同"投放过的活动删不掉"、"入过货的单子删不掉"）
        List<ShopActivityCouponUser> issued = shopActivityCouponUserMapper.selectList(
                new LambdaQueryWrapper<ShopActivityCouponUser>()
                        .select(ShopActivityCouponUser::getCouponId)
                        .in(ShopActivityCouponUser::getCouponId, ids));
        if (!issued.isEmpty()) {
            Set<Long> issuedCouponIds = issued.stream()
                    .map(ShopActivityCouponUser::getCouponId)
                    .collect(Collectors.toCollection(HashSet::new));
            List<String> names = listByIds(ids).stream()
                    .filter(coupon -> issuedCouponIds.contains(coupon.getId()))
                    .map(ShopActivityCoupon::getCouponName)
                    .collect(Collectors.toList());
            throw new ServiceException("已经发出去过的券删不掉，这几张名下还有用户券：" + String.join("、", names));
        }
        removeByIds(ids);
    }

    /**
     * 发放：一次给一批用户发券，每人几张由参数上的 quantity 说（不传就是一人一张）。
     *
     * 顺序是"先占额度、再插行"，两步在同一个事务里 —— 占不到额度就整笔回滚，
     * 所以不会出现"券插进去了、额度没扣"或者反过来。占的额度是 人数 × 每人张数，一次校验，不分次扣。
     * 面额那几格发出去就定格成快照，之后模板改面额不会回头刷已经到用户手上的券。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int give(ShopActivityCouponGiveDto form) {
        if (form == null || form.getCouponId() == null) {
            throw new ServiceException("请指明发的是哪张优惠券");
        }
        // 同一批里重复选的账号去掉：多算一个人就是白占一份发行额度
        List<Long> userIds = form.getUserIds() == null ? Collections.emptyList()
                : form.getUserIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) {
            throw new ServiceException("至少选一个要发放的用户");
        }
        // 每人发几张：这一格乘在人数上才是本次要占的额度，上限交给下面那条带总量校验的原子 UPDATE
        int perUser = form.getQuantity() == null ? 1 : form.getQuantity();
        if (perUser <= 0) {
            throw new ServiceException("每人发放张数要大于 0");
        }

        ShopActivityCoupon coupon = getById(form.getCouponId());
        if (coupon == null) {
            throw new ServiceException("优惠券不存在");
        }
        Integer status = coupon.getStatus();
        if (status != null && (status == STATUS_FINISHED || status == STATUS_CANCELED)) {
            throw new ServiceException(status == STATUS_FINISHED
                    ? "这张券已经结束，不能再发放" : "这张券已经取消，不能再发放");
        }
        // 过期时间在发放这一刻就定死（这一列 NOT NULL，定不出来就别发）
        LocalDateTime expireTime = CouponUtils.expireTimeOf(coupon);

        // 账号真人再确认一遍：请求体里那些 id 是前端给的，账号没了就等于是发给了一个不存在的人。
        // 这里只看"人还在"，不看账号停用与否 —— 券发到手上是资产，账号被锁了也不该顺手把券没收
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        if (users.isEmpty()) {
            throw new ServiceException("所选用户都不存在");
        }
        if (users.size() < userIds.size()) {
            // sys_user.id 是 Integer，请求体里那串是 Long，比之前先归成同一种类型，否则 contains 永远不成立
            List<Long> selected = users.stream().map(u -> u.getId().longValue()).collect(Collectors.toList());
            List<String> missing = userIds.stream()
                    .filter(id -> !selected.contains(id))
                    .map(id -> "#" + id)
                    .collect(Collectors.toList());
            throw new ServiceException("这几个用户已经不存在：" + String.join("、", missing));
        }

        // 占额度：加数（人数 × 每人张数）和总量校验压在一条 UPDATE 里，返回 0 就是没占到（并发发放不会把券发超）
        int count = users.size() * perUser;
        if (baseMapper.increaseIssuedCount(coupon.getId(), count) == 0) {
            throw new ServiceException("可发放量不够：这张券总量 " + coupon.getTotalCount()
                    + "、已发放 " + coupon.getIssuedCount() + "，本次要发 " + count + " 张");
        }
        // 占完再读一次：这一行此刻被本事务锁着，读回来的 issued_count 就是"这批之后"的数，
        // 于是券码序号从 issued_count - count + 1 一直排到 issued_count，两批并发也拿不到同一个号
        int issuedAfter = getById(coupon.getId()).getIssuedCount();

        LocalDateTime receiveTime = LocalDateTime.now();
        String remark = StringUtils.trimToNull(form.getRemark());
        List<ShopActivityCouponUser> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ShopActivityCouponUser record = new ShopActivityCouponUser();
            record.setCouponId(coupon.getId());
            record.setCouponCode(CouponUtils.couponCode(coupon.getId(), issuedAfter - count + i + 1));
            // 同一个人连着拿 perUser 张，一行一张券：i / perUser 就是这批里的第几个人
            record.setUserId(users.get(i / perUser).getId().longValue());
            // 面额这六格是快照，全部从模板当场抄，不接受请求体里传任何东西（发放参数只有券id、用户、每人张数、备注四格）
            record.setCouponName(coupon.getCouponName());
            record.setCouponType(coupon.getCouponType());
            record.setFaceValue(coupon.getFaceValue());
            record.setThresholdAmount(coupon.getThresholdAmount());
            record.setMaxDiscount(coupon.getMaxDiscount());
            record.setScopeType(coupon.getScopeType());
            record.setStatus(USER_STATUS_UNUSED);
            record.setReceiveTime(receiveTime);
            record.setExpireTime(expireTime);
            // 领取方式记的是"这张券怎么到的用户手上"，后台这颗按钮就是 2 系统发放，不跟模板那一格
            record.setGetWay(GET_WAY_SYSTEM);
            record.setRemark(remark);
            // orderId/orderNo/useTime 这三格发出去时留 NULL：那是统一下单核销那一步（shop-api 的
            // ShopOrderCouponService#redeem）才写的，发放只管"到谁手上"
            shopActivityCouponUserMapper.insert(record);
            records.add(record);
        }
        return records.size();
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报「不能为空」。
     * issued_count / used_count 也在这一刀里补 0（插入要过得去），它们钉了 NEVER，之后任何一次修改都改不动
     */
    private void normalize(ShopActivityCoupon coupon) {
        if (coupon.getCouponType() == null) {
            coupon.setCouponType(1);
        }
        if (coupon.getFaceValue() == null) {
            coupon.setFaceValue(BigDecimal.ZERO);
        }
        if (coupon.getThresholdAmount() == null) {
            coupon.setThresholdAmount(BigDecimal.ZERO);
        }
        if (coupon.getTotalCount() == null) {
            coupon.setTotalCount(0);
        }
        if (coupon.getIssuedCount() == null) {
            coupon.setIssuedCount(0);
        }
        if (coupon.getUsedCount() == null) {
            coupon.setUsedCount(0);
        }
        if (coupon.getValidType() == null) {
            coupon.setValidType(VALID_FIXED);
        }
        if (coupon.getScopeType() == null) {
            coupon.setScopeType(1);
        }
        if (coupon.getGetWay() == null) {
            coupon.setGetWay(1);
        }
        applyScopeIds(coupon);
    }

    private void validate(ShopActivityCoupon coupon, Long excludeId) {
        if (StringUtils.isBlank(coupon.getCouponName())) {
            throw new ServiceException("券名称不能为空");
        }
        coupon.setCouponName(coupon.getCouponName().trim());
        if (StringUtils.isBlank(coupon.getCouponCode())) {
            throw new ServiceException("券码不能为空");
        }
        coupon.setCouponCode(coupon.getCouponCode().trim());

        // 适用门店可空（NULL = 全平台，同活动那一格），填了就要真有其店
        if (coupon.getStoreId() != null && shopStoreMapper.selectById(coupon.getStoreId()) == null) {
            throw new ServiceException("所选适用门店不存在");
        }
        // 负责的运营也照样可空（还没定人），填了就要真有其人，口径同门店培训的培训讲师
        if (coupon.getUserId() != null && sysUserMapper.selectById(coupon.getUserId()) == null) {
            throw new ServiceException("所选负责的运营不存在");
        }

        validateAmount(coupon);
        validateValidPeriod(coupon);

        // 这一张表只有券码是"要唯一"的（库上还没建 uk，所以这一道查重要担着），券名称故意不查重：
        // 同一档活动发几张同名券是常态，同活动名称、供应商名称不查重
        Long codeCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopActivityCoupon>()
                .eq(ShopActivityCoupon::getCouponCode, coupon.getCouponCode())
                .ne(excludeId != null, ShopActivityCoupon::getId, excludeId));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException("券码「" + coupon.getCouponCode() + "」已存在");
        }
    }

    /**
     * 面额那三格的判据按券类型分叉：DDL 写明折扣券的 face_value 是「折扣率如 0.9」，
     * 所以那一档填 9（把 9 折当 90%）就是口径搞混了，当场拦；
     * 满减券另有一道「减的不能比门槛还多」，满 20 减 50 那种是白送钱。
     */
    private void validateAmount(ShopActivityCoupon coupon) {
        BigDecimal faceValue = coupon.getFaceValue();
        BigDecimal threshold = coupon.getThresholdAmount();
        Integer type = coupon.getCouponType();

        if (faceValue != null && faceValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("面额/折扣值不能是负数");
        }
        if (threshold != null && threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("使用门槛不能是负数，无门槛就填 0");
        }
        if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("最高抵扣金额不能是负数，不封顶就别填这一格");
        }
        if (type == null) {
            return;
        }
        if (type == TYPE_DISCOUNT) {
            if (faceValue == null || faceValue.compareTo(BigDecimal.ZERO) <= 0
                    || faceValue.compareTo(BigDecimal.ONE) >= 0) {
                throw new ServiceException("折扣券的面额那一格填的是折扣率（0.9 就是 9 折），请填 0 到 1 之间的小数");
            }
        } else if (type != TYPE_FREE_SHIPPING && (faceValue == null || faceValue.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ServiceException("这张券的面额还没填，填 0 就等于不发任何东西（免邮券才允许留 0）");
        }
        // 门槛和面额谁大都判一次：0 门槛（无门槛）不参与这条判据
        if (threshold != null && threshold.compareTo(BigDecimal.ZERO) > 0
                && faceValue != null && type != TYPE_DISCOUNT && type != TYPE_FREE_SHIPPING
                && faceValue.compareTo(threshold) > 0) {
            throw new ServiceException("满减/代金券的「满」要不低于「减」，这张券是满 " + threshold + " 减 " + faceValue);
        }
    }

    /**
     * 有效期按 valid_type 分叉判必填：1 固定时间段得有 valid_end（发放时过期时间就取它，缺了就发不出去），
     * 2 领取后 N 天得有 valid_days。两头时间都填齐了才判先后，漏一头是「还没定」，不算错。
     */
    private void validateValidPeriod(ShopActivityCoupon coupon) {
        Integer validType = coupon.getValidType();
        if (validType != null && validType == VALID_AFTER_DAYS) {
            if (coupon.getValidDays() == null || coupon.getValidDays() <= 0) {
                throw new ServiceException("「领取后N天」这一档得填有效天数");
            }
            return;
        }
        if (coupon.getValidStart() != null && coupon.getValidEnd() != null
                && coupon.getValidEnd().isBefore(coupon.getValidStart())) {
            throw new ServiceException("有效期结束时间不能早于开始时间");
        }
        // 这里不拦"valid_end 还没填"：新建时先占个名额、排期后定是常态，
        // 真要发放时 CouponUtils.expireTimeOf 那一道会挡（库里 valid_start/valid_end 都是可空列）
    }

    /**
     * 写方向：把表单里那串逗号分隔的 id 拼成合法 JSON 再进 json 列（非数字当场报回去，别让它撞 MySQL 的 3140）。
     * 1 全场 / 4 指定门店 这两档整格留 NULL —— 4 那档真正生效的是行上的 store_id，两格别重复存
     */
    private void applyScopeIds(ShopActivityCoupon coupon) {
        Integer scopeType = coupon.getScopeType();
        if (scopeType == null || (scopeType != SCOPE_PRODUCT && scopeType != SCOPE_CATEGORY)) {
            coupon.setScopeValue(null);
            return;
        }
        String key = scopeType == SCOPE_PRODUCT ? KEY_PRODUCT_IDS : KEY_CATEGORY_IDS;
        List<Long> ids = parseIds(coupon.getScopeIds());
        if (ids.isEmpty()) {
            throw new ServiceException(scopeType == SCOPE_PRODUCT
                    ? "适用范围选了「指定商品」，范围值那一格得填商品ID"
                    : "适用范围选了「指定分类」，范围值那一格得填分类ID");
        }
        // 先用 Map 装好再交给 JSONUtil 序列化：hutool 各版本里 JSONObject.set(...) 的返回值不一样，
        // 那个链式写法（createObj().set(...).toString()）不敢依赖
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put(key, ids);
        coupon.setScopeValue(JSONUtil.toJsonStr(scope));
    }

    /** 读方向：把 json 列拆回逗号分隔的 id 串给表单和列表用；拆不动就原样给出去，别让一行脏数据毁掉整页 */
    private void readScopeIds(ShopActivityCoupon coupon) {
        if (StringUtils.isBlank(coupon.getScopeValue())) {
            coupon.setScopeIds(null);
            return;
        }
        try {
            JSONObject json = JSONUtil.parseObj(coupon.getScopeValue());
            String key = coupon.getScopeType() != null && coupon.getScopeType() == SCOPE_CATEGORY
                    ? KEY_CATEGORY_IDS : KEY_PRODUCT_IDS;
            JSONArray ids = json.getJSONArray(key);
            if (ids == null || ids.isEmpty()) {
                coupon.setScopeIds(null);
                return;
            }
            // JSONArray 本身是个 List，直接遍历拼串（它的 stream 元素类型各版本不一致，不依赖）
            StringBuilder text = new StringBuilder();
            for (Object id : ids) {
                if (text.length() > 0) {
                    text.append(',');
                }
                text.append(id);
            }
            coupon.setScopeIds(text.toString());
        } catch (Exception e) {
            coupon.setScopeIds(coupon.getScopeValue());
        }
    }

    /** 分隔符认英文逗号、中文逗号、顿号和空白；不是数字的直接报回去，别拼进 json 列 */
    private List<Long> parseIds(String text) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return ids;
        }
        // 分隔符认英文逗号、中文逗号（\uFF0C）、顿号（\u3001）和空白
        for (String part : text.split("[\uFF0C,\u3001\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(part));
            } catch (NumberFormatException e) {
                throw new ServiceException("范围值里「" + part + "」不是 ID，这一格只能填逗号分隔的数字");
            }
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }
}
