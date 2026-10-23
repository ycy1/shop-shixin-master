package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopDelivery;
import com.shop.entity.ShopDeliveryFeeRule;
import com.shop.entity.ShopDeliveryRider;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpSaleOutboundMapper;
import com.shop.mapper.ShopDeliveryMapper;
import com.shop.mapper.ShopDeliveryRiderMapper;
import com.shop.service.ShopDeliveryFeeRuleService;
import com.shop.service.ShopDeliveryService;
import com.shop.utils.DeliveryTimeUtils;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopDeliveryServiceImpl extends ServiceImpl<ShopDeliveryMapper, ShopDelivery>
        implements ShopDeliveryService {

    private final ShopDeliveryRiderMapper shopDeliveryRiderMapper;

    /** 配送单绑的就是出库单：回填出库单号、判"这张单出满没出满"都从它取 */
    private final ShopErpSaleOutboundMapper shopErpSaleOutboundMapper;

    /** 计价规则不冗余存名字：详情和列表上那一格按 fee_rule_id 现查（算钱那一段已经挪到 commom 的 DeliveryFeeUtils） */
    private final ShopDeliveryFeeRuleService shopDeliveryFeeRuleService;

    /** 配送状态（字典 delivery_status）：0 待派单 —— 统一下单建出来就是这一档，它不进抢单池 */
    private static final int STATUS_WAIT_ASSIGN = 0;

    /** 配送状态（字典 delivery_status）：1 待接单 —— 点「确认」推到这里，送货员才看得见这一单 */
    private static final int STATUS_WAIT_ACCEPT = 1;

    /** 出库单状态（字典 sale_outbound_status）：2 已出库 —— 口径同 ShopErpSaleOutboundServiceImpl#outbound 里那个 STATUS_OUT */
    private static final int OUTBOUND_STATUS_OUT = 2;

    /** 经纬度值域：超出就别拿去算距离了（ST_Distance_Sphere 会直接抛库里的 range 异常） */
    private static final BigDecimal LNG_RANGE = new BigDecimal("180");

    private static final BigDecimal LAT_RANGE = new BigDecimal("90");

    @Override
    public IPage<ShopDelivery> selectPage(ShopDelivery query) {
        if (query == null) {
            query = new ShopDelivery();
        }
        // 只有账号类型=送货员那一档还收窄，而且口径是"派给我的 + 还没人接的那一批"（他在抢单池里得先看得见才谈得上挑）；
        // 建单人仍留在 create_by 里备查。门店账号那一档跟着 store_id 一起下线了（2026-09-21）：
        // 发货门店不再是这一单的属性，这一单从哪儿发只看单上那份 sender_* 快照
        if (LoginUserUtils.isRider()) {
            ShopDeliveryRider my = myRider();
            if (my == null) {
                return PageUtil.getEmptyPage();
            }
            query.setScopeRiderId(my.getId());
            // 常驻经纬度齐了才按"离我常驻地最近"排；只填一半的档案在送货员那侧的 validate 就拦住了
            if (my.getResidentLng() != null && my.getResidentLat() != null) {
                query.setResidentLng(my.getResidentLng());
                query.setResidentLat(my.getResidentLat());
            }
        }
        // 关键字要搜四个字段、还要卡时间区间和数据范围，所以走自定义 XML
        IPage<ShopDelivery> result = baseMapper.selectDeliveryPage(PageUtil.getPage(), query);
        fillNames(result.getRecords());
        return result;
    }

    /**
     * 当前登录账号对应的送货员档案，口径同送货员「我的信息」：按 user_id 命中，历史脏数据重复时取第一条。
     * 这里要整行不是只要 id：列表排序还得用他的常驻地点经纬度。
     */
    private ShopDeliveryRider myRider() {
        List<ShopDeliveryRider> riders = shopDeliveryRiderMapper.selectList(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getUserId, StpUtil.getLoginIdAsLong()));
        return riders.isEmpty() ? null : riders.get(0);
    }

    /**
     * 写操作的范围校验：账号类型=送货员的只能动**已经派给自己**的那一单。
     * 列表里那些"还没人接的"他看得见（不看见就挑不了），但没派给他之前动不着 ——
     * 把某一单变成自己那一单靠的是「接单」那一步（走的是公共接口 ShopRiderCommonController#acceptDelivery，
     * 不在这里的通用修改里），没接之前这一单他动不着。
     * 门店账号那一档已经跟着 store_id 一起下线，后台账号都能改任何一单。
     */
    private void assertWritable(List<ShopDelivery> deliveries) {
        if (LoginUserUtils.isRider()) {
            ShopDeliveryRider my = myRider();
            Long riderId = my == null ? null : my.getId();
            for (ShopDelivery delivery : deliveries) {
                if (riderId == null || !riderId.equals(delivery.getRiderId())) {
                    throw new ServiceException("只能修改或删除派给自己的配送单");
                }
            }
        }
    }

    @Override
    public ShopDelivery detail(Long id) {
        ShopDelivery delivery = getById(id);
        if (delivery != null) {
            fillNames(Collections.singletonList(delivery));
        }
        return delivery;
    }

    @Override
    public void insert(ShopDelivery delivery) {
        // 新增时表单那栏是禁用的、不会传单号，和门店编码一样由系统生成
        if (StringUtils.isBlank(delivery.getDeliveryNo())) {
            delivery.setDeliveryNo(GeneratorNoUtils.getDeliveryNo());
        }
        fillOutboundNo(delivery);
        validate(delivery, null);
        normalize(delivery);
        // 配送数据按两个地点算，不认表单传上来的那两个值（口径见 fillEstimate）
        fillEstimate(delivery);
        try {
            save(delivery);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_delivery_no，上面的前置查重（只查未删除的）看不到它。
            // 出库单那一侧这轮起没有唯一键了（那一列两种 id 混着装），撞不到这里
            throw new ServiceException("配送单号「" + delivery.getDeliveryNo() + "」已被占用");
        }
    }

    @Override
    public void update(ShopDelivery delivery) {
        if (delivery.getId() == null) {
            throw new ServiceException("配送单ID不能为空");
        }
        ShopDelivery exists = getById(delivery.getId());
        if (exists == null) {
            throw new ServiceException("配送单不存在");
        }
        assertWritable(Collections.singletonList(exists));
        // 送货员可空、也不再要求"是自己名下门店的人"（表上没这一列了）：换派给谁都行，
        // 填了的那一个存不存在由 validate 判
        fillOutboundNo(delivery);
        validate(delivery, delivery.getId());
        normalize(delivery);
        syncFeeRule(delivery, exists);
        // 挪过发货/收件任一个点，这两格就跟着重算；两点不齐则置空（见 fillEstimate）
        fillEstimate(delivery);
        try {
            updateById(delivery);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("配送单号「" + delivery.getDeliveryNo() + "」已被占用");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        assertWritable(listByIds(ids));
        removeByIds(ids);
    }

    /**
     * 「商品出库」列表那颗「生成配送单」弹框里那颗「确认」：把这张出库单绑着的那一条配送单从 0 待派单 推成 1 待接单
     * （判据和给前端的提示都在 {@link ShopDeliveryService#confirmDelivery} 的注释里）。
     *
     * 这一颗按钮不再建单：配送单在统一下单那一刻就建好了（重量、距离、配送费全在那一次定死，
     * 买家付的钱里已经含着它），支付回调把它换绑到这张出库单上（shop-api 的 ShopOrderDeliveryService）。
     * 所以这里只翻状态 + 记派单方式和时间，钱和货的那几格一概不动。
     * 推成 1 之后它才进送货员的抢单池（池子的口径是 status = 1，见 ShopDeliveryMapper.xml 那一段）。
     */
    @Override
    public void confirmDelivery(Long outboundId) {
        if (outboundId == null) {
            throw new ServiceException("请先选择要确认的出库单");
        }
        ShopErpSaleOutbound outbound = shopErpSaleOutboundMapper.selectById(outboundId);
        if (outbound == null) {
            throw new ServiceException("出库单不存在，无法确认配送");
        }
        String label = "出库单「" + outbound.getOutboundNo() + "」";
        // 和列表上那颗按钮的显示条件是同一句判据：没出满就不给确认（一批货分几次出时，
        // 前几次出完就派车等于让送货员跑两趟）
        if (outbound.getStatus() == null || outbound.getStatus() != OUTBOUND_STATUS_OUT) {
            throw new ServiceException(label + "还没有全部出库完成，出满之后才能确认配送");
        }
        ShopDelivery delivery = getOne(new LambdaQueryWrapper<ShopDelivery>()
                // id + 号两个一起比：这一槽在还没换绑的那一条里装的是订单的那两个值，
                // 只比 id 会把同号的订单配送单错认成这张出库单的（口径同 shop-api 的 findByBinding）
                .eq(ShopDelivery::getOutboundId, outboundId)
                .eq(ShopDelivery::getOutboundNo, outbound.getOutboundNo())
                .orderByAsc(ShopDelivery::getId)
                .last("LIMIT 1"));
        if (delivery == null) {
            // 只有线上订单的出库单才有那么一条（来源是它才在下单时建、支付回调时绑）；
            // 手工建的出库单和采购退货单本来就没有，要人工配送去「配送管理」建
            throw new ServiceException(label + "没有绑定的配送单（线上订单支付成功后会自动建好并绑上），"
                    + "需要人工配送请到「配送管理」手工新增");
        }
        if (delivery.getStatus() == null || delivery.getStatus() != STATUS_WAIT_ASSIGN) {
            throw new ServiceException("配送单「" + delivery.getDeliveryNo() + "」已经不是「待派单」，"
                    + "无需重复确认（状态：" + delivery.getStatus() + "）");
        }
        delivery.setStatus(STATUS_WAIT_ACCEPT);
        // 派单方式不在这里覆盖：下单建这一条时已经写成「2 抢单」了（它本来就没有指名的人，
        // 「确认」干的正是"放它进抢单池"，写成 1 系统派单反而把事实改反了）。
        // 手工新增时人填过的那一格也照原样留着 —— 这一颗只推状态 + 记进池子的时刻。
        delivery.setAssignTime(LocalDateTime.now());
        updateById(delivery);
    }


    /**
     * 只算配送距离：distance = 发货点到收件点的球面直线距离（km，保留 2 位）。
     * 两个点缺任何一边（发货仓库没填经纬度、买家没选收货地址也没档案坐标）这一格就是空 ——
     * 宁可为空也不留上一次算完的旧值，那比没有更误导人。
     *
     * 这一路（「配送管理」手工新增/修改）在这里就停，不补预计时长：
     * 时长要按**接单那个送货员的交通工具**才算，而按出库单自动建的那一条压根还不知道会派给谁
     * （2026-09-21 他改的口径："生成配送单时 预计时长不需要生成，待送货员接单时按送货员的交通工具进行估算"）。
     * 那一格做在接单接口里：ShopDeliveryAcceptServiceImpl 读 shop_delivery_rider.vehicle_type，
     * 用 DeliveryTimeUtils 把这一单的 distance 折成分钟数写 estimatedTime，再推 expectTime。
     * 统一下单建配送单那一路（shop-api）同样只算到这里，它连配送费都是在那一刻按规则定死的。
     */
    private void fillDistance(ShopDelivery delivery) {
        BigDecimal senderLng = delivery.getSenderLng();
        BigDecimal senderLat = delivery.getSenderLat();
        BigDecimal receiverLng = delivery.getReceiverLng();
        BigDecimal receiverLat = delivery.getReceiverLat();
        if (senderLng == null || senderLat == null || receiverLng == null || receiverLat == null) {
            delivery.setDistance(null);
            delivery.setEstimatedTime(null);
            return;
        }
        // 超出值域的话 ST_Distance_Sphere 会直接抛 MySQL 的 range 异常，那样报错的人看不懂是哪一格填错了
        assertCoordinateRange("发货", senderLng, senderLat);
        assertCoordinateRange("收件", receiverLng, receiverLat);
        delivery.setDistance(baseMapper.selectDistanceKm(senderLng, senderLat, receiverLng, receiverLat));
    }

    /**
     * 「配送管理」里手工新增/修改那一路：在距离之上再把预计时长和预计送达补上。
     * estimatedTime 按**这一单上那位送货员的交通工具**折算（速度表在 DeliveryTimeUtils，和接单那一路共用一份）；
     * 表单上还没填送货员时按电动车那一档兜底 —— 这一路是手工建单，人是可以在修改弹窗里补的，
     * 空着比给个数更容易被当成"漏填"。
     * expectTime 只在没人填的时候按"现在 + 预计时长"补一个，表单里填过的保留。
     */
    private void fillEstimate(ShopDelivery delivery) {
        fillDistance(delivery);
        Integer minutes = DeliveryTimeUtils.estimateMinutes(
                delivery.getDistance(), vehicleTypeOf(delivery.getRiderId()));
        delivery.setEstimatedTime(minutes);
        if (minutes != null && delivery.getExpectTime() == null) {
            delivery.setExpectTime(LocalDateTime.now().plusMinutes(minutes));
        }
    }

    /** 这一单的送货员用什么交通工具；没填或查不到都返回 null，交给 DeliveryTimeUtils 按电动车兜底 */
    private Integer vehicleTypeOf(Long riderId) {
        if (riderId == null) {
            return null;
        }
        ShopDeliveryRider rider = shopDeliveryRiderMapper.selectById(riderId);
        return rider == null ? null : rider.getVehicleType();
    }


    /**
     * 表单改单时 fee_rule_id 由服务端说了算（口径同 outbound_no：这一列前端传什么都不认）：
     * 人工把「配送费」改过，那笔钱就不再是规则的产物了，id 清成 NULL；
     * 只改了收件人、重量这些别的格子，就把库里那份原样带回去 —— 修改表单上没有这一格，
     * 不显式回填的话一次普通保存就会把追溯抹掉。
     */
    private void syncFeeRule(ShopDelivery delivery, ShopDelivery exists) {
        BigDecimal oldFee = exists.getDeliveryFee() == null ? BigDecimal.ZERO : exists.getDeliveryFee();
        boolean feeEdited = delivery.getDeliveryFee() != null && delivery.getDeliveryFee().compareTo(oldFee) != 0;
        delivery.setFeeRuleId(feeEdited ? null : exists.getFeeRuleId());
        // 名称不是列（@TableField(exist = false)），读的时候由 fillNames 按 id 现查
        delivery.setFeeRuleName(null);
    }


    /** 经纬度值域：经度 ±180、纬度 ±90。超范围就别拿去算距离了 */
    private void assertCoordinateRange(String label, BigDecimal lng, BigDecimal lat) {
        if (lng.abs().compareTo(LNG_RANGE) > 0 || lat.abs().compareTo(LAT_RANGE) > 0) {
            throw new ServiceException(label + "经纬度超出范围（经度 -180~180、纬度 -90~90），算不出配送距离");
        }
    }


    /**
     * outbound_no 是冗余列，一律以 outboundId 查到的出库单为准覆盖写入：
     * 表单里那一格只读，改了关联出库单这个号也会跟着换，不会留下对不上的旧号。
     */
    private void fillOutboundNo(ShopDelivery delivery) {
        if (delivery.getOutboundId() == null) {
            throw new ServiceException("请选择关联出库单");
        }
        ShopErpSaleOutbound outbound = shopErpSaleOutboundMapper.selectById(delivery.getOutboundId());
        if (outbound == null) {
            throw new ServiceException("所选出库单不存在");
        }
        delivery.setOutboundNo(outbound.getOutboundNo());
    }

    /**
     * 按 rider_id / fee_rule_id 回填送货员姓名和计价规则名（写法同门店列表回填店长昵称）。
     * 一次 selectBatchIds 拿下当页出现过的 id，不超过页大小。
     */
    private void fillNames(List<ShopDelivery> deliveries) {
        Set<Long> riderIds = new HashSet<>();
        Set<Long> feeRuleIds = new HashSet<>();
        for (ShopDelivery delivery : deliveries) {
            if (delivery.getRiderId() != null) {
                riderIds.add(delivery.getRiderId());
            }
            if (delivery.getFeeRuleId() != null) {
                feeRuleIds.add(delivery.getFeeRuleId());
            }
        }

        Map<Long, String> riderNameById = new HashMap<>();
        if (!riderIds.isEmpty()) {
            for (ShopDeliveryRider rider : shopDeliveryRiderMapper.selectBatchIds(riderIds)) {
                riderNameById.put(rider.getId(), rider.getRiderName());
            }
        }

        // 规则名不冗余存列，按 fee_rule_id 现查（同送货员姓名那一格）。
        // 规则被删了就是查不到 —— 前端按"有 id 没名字"显示成"规则已删除"，那笔钱本身不受影响
        Map<Long, String> ruleNameById = new HashMap<>();
        if (!feeRuleIds.isEmpty()) {
            for (ShopDeliveryFeeRule rule : shopDeliveryFeeRuleService.listByIds(feeRuleIds)) {
                ruleNameById.put(rule.getId(), rule.getRuleName());
            }
        }

        for (ShopDelivery delivery : deliveries) {
            delivery.setRiderName(riderNameById.get(delivery.getRiderId()));
            delivery.setFeeRuleName(ruleNameById.get(delivery.getFeeRuleId()));
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传（表单没填或被 JSON 丢掉 undefined）时补上库里的默认值，
     * 否则 INSERT 会显式带上 NULL 而报"不能为空"。
     */
    private void normalize(ShopDelivery delivery) {
        if (delivery.getDeliveryType() == null) {
            delivery.setDeliveryType(1);
        }
        if (delivery.getStatus() == null) {
            delivery.setStatus(0);
        }
        if (delivery.getDeliveryFee() == null) {
            delivery.setDeliveryFee(BigDecimal.ZERO);
        }
        if (delivery.getTipFee() == null) {
            delivery.setTipFee(BigDecimal.ZERO);
        }
    }

    private void validate(ShopDelivery delivery, Long excludeId) {
        if (StringUtils.isBlank(delivery.getDeliveryNo())) {
            throw new ServiceException("配送单号不能为空");
        }
        // 发货那一组不再有"必须属于某家门店"这一层（store_id 已经删了）：填了就算，没填就当这一单还没定发货地点，
        // 反正算不出距离时 fillDistance 会把 distance 置空，配送费退化成只按重量和金额那一档算
        // 送货员可空（还没派单），填了就必须存在
        if (delivery.getRiderId() != null && shopDeliveryRiderMapper.selectById(delivery.getRiderId()) == null) {
            throw new ServiceException("所选送货员不存在");
        }
        if (StringUtils.isBlank(delivery.getReceiverName())) {
            throw new ServiceException("收件人姓名不能为空");
        }
        if (StringUtils.isBlank(delivery.getReceiverPhone())) {
            throw new ServiceException("收件人电话不能为空");
        }
        if (StringUtils.isBlank(delivery.getReceiverAddress())) {
            throw new ServiceException("收件地址不能为空");
        }
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDelivery>()
                .eq(ShopDelivery::getDeliveryNo, delivery.getDeliveryNo())
                .ne(excludeId != null, ShopDelivery::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("配送单号「" + delivery.getDeliveryNo() + "」已存在");
        }
        // 一出库单一配送：库里没有唯一键兜着了（outbound_id 那一列现在两种 id 混着装，唯一键会误伤下单），
        // 所以这一句是唯一的闸。id + 号两个一起比，理由同上
        Long outboundCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDelivery>()
                .eq(ShopDelivery::getOutboundId, delivery.getOutboundId())
                .eq(ShopDelivery::getOutboundNo, delivery.getOutboundNo())
                .ne(excludeId != null, ShopDelivery::getId, excludeId));
        if (outboundCount != null && outboundCount > 0) {
            throw new ServiceException("出库单「" + delivery.getOutboundNo() + "」已有配送单，请直接修改那一条");
        }
    }
}
