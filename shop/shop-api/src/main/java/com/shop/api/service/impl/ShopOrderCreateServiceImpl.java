package com.shop.api.service.impl;

import com.shop.api.service.ShopOrderCouponService;
import com.shop.api.service.ShopOrderCreateService;
import com.shop.api.service.ShopOrderDeliveryService;
import com.shop.api.service.ShopOrderPointsService;
import com.shop.api.service.ShopOrderWalletService;
import com.shop.dto.order.ShopOrderCreateDto;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopConsumerAddress;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopProductSku;
import com.shop.entity.ShopDelivery;
import com.shop.entity.SysOrder;
import com.shop.entity.SysUser;
import com.shop.dto.user.LoginUserInfo;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.ShopErpInventoryMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.mapper.SysOrderMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.CouponUtils;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.order.ShopOrderCreateVo;
import com.shop.vo.order.ShopOrderPreviewVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一下单 服务实现
 *
 * 一次调用落三张表，全程一个事务：sys_order 一条主单 + shop_order_item 每行一条明细
 * + shop_delivery 一条「0 待派单」的配送单（2026-09-21 起配送单提前到下单就建，
 * 重量/距离/配送费全在这一次定下来，那一笔钱已经含在买家要付的金额里；
 * 出库单这时候还不存在，所以配送单先只绑订单，支付回调时才换绑上去，见 ShopOrderDeliveryService）。
 * 明细落库前还要给每一行定发货仓库，并把 shop_erp_inventory 那一行的 locked_stock 加上本行数量。
 * 请求体里带了 couponIds 的话，末尾还要多写两张券表（用户券核销 + 模板 used_count），也在同一个事务里。
 * 请求体里带了 usedPoints 的话，再多加一张积分表（账户冻结 + 流水），同样在这一个事务里。
 * 请求体里 payType 传的是 4 余额的话，再多一张钱包表（可用余额冻进冻结余额 + 流水），也在同一个事务里。
 *
 * 到"下单"这一步为止：不改支付状态（主单落 0 待支付）、不扣总库存（stock 一分不动，只锁可用量）、
 * 不真扣积分（只从可用冻进冻结，支付成功才结转）、不真花钱（余额同样只是占住）。
 * 支付和出库留给后面的接口按返回的 orderNo 接着做；买家没付就反悔的话走取消那一路
 *（ShopOrderCancelService：主单 3 已取消 + 这里锁的 locked_stock 逐行还回去 + 核销掉的券退回去 +
 * 冻住的积分整笔解冻 + 占住的那一笔余额退回钱包 + 那条配送单跟着置 5 已取消）。
 * 这一家仓只是"先替买家占住货"：支付回调生成出库单时还会按商品所属门店再改派一次（门店仓优先，
 * 就近那一档的起点用的就是落在 sys_order.address_id 上的这一条收货地址），
 * 改派会把锁定一起挪过去（ShopOrderPaidServiceImpl#routeWarehouse），所以这里的 warehouse_id 不是终局。
 * 改派之后配送单上那三个数会跟着刷一遍发货点和距离（钱不回改），见 ShopOrderDeliveryService#rebindToOutbound。
 *
 * 这个类还有第二个入口 {@link #preview}（下单试算）：它和 create 共用 {@link #quote} 那一段计算
 *（单价、行小计、就近选出的仓、每张券的范围与门槛、摊到行上的优惠、每行花的分、配送费、免邮券抵运费），
 * 只是不写任何一张表、不锁库存、不核销券、也不动积分账户。共用同一段不是图省事：结算页报给买家的钱
 * 和提交后真收的钱必须出自同一个式子，另写一份试算迟早会和下单对不上账。
 *
 * 凡是能算的都不认客户端传的：单价、规格文本、这一条规格的主图都取自 shop_product_sku，
 * 商品名/门店取自它所属的那件商品（shop_product），小计 = 单价 × 数量，主单的货值由明细行汇总、配送费由规则算，
 * 发货仓库由服务端按收货地坐标算（客户端传了也不认，坐标只认 addressId 背后那一条地址）。
 * 客户端只给"谁买、寄到哪个地址、买了什么、买几件、用了哪几张券、每一行花几分、用哪种方式付"。
 *
 * 优惠那一格 2026-09-29 起也不认客户端了：请求体里只给 couponIds（用户券 id），
 * 每张券抵多少钱、抵在哪几行上，由 ShopOrderCouponService 按券的面额 + 适用范围 + 使用门槛算，
 * 摊完再把用掉的券核销掉（status 0→1、写 order_id/order_no/use_time，模板 used_count 原子加）。
 * 判据放那么细的理由是这一笔钱要走三份账：行实付（小计 − 优惠）是退款金额和出库成本的唯一依据，
 * 货值实付又是"实付满多少免配送费"那一档的依据，所以优惠必须落在行上、还必须落在券范围内的行上。
 *
 * 积分是同一套做法的第二份（判据见 ShopOrderPointsService）：请求体里给的是"分"不是"钱"，
 * 可以逐行给（Item.usedPoints，买家自己一行一行指定），也可以整单给一个总数（payPoints，
 * 由服务端按各行还剩多少钱可抵摊到行上，见 allocatePayPoints），两份合起来落进同一个 used_points 格子；
 * 这几分抵多少钱由服务端按 sys_config 那一档汇率现折，客户端传上来的金额一个都不认。
 * 折出来的钱跟券的优惠分开记两格（discount_amount 只记券、used_points 记分），因为那是两种账：
 * 券是营销的账、退回来还是券；分是买家账户上的资产，退款要按件数把分退回去、不能退成现金。
 * 下单这一趟只把它冻住（可用 → 冻结），支付回调才真扣走并顺手按商品送分。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderCreateServiceImpl implements ShopOrderCreateService {

    private final SysOrderMapper sysOrderMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    private final ShopProductMapper shopProductMapper;

    private final ShopProductSkuMapper shopProductSkuMapper;

    private final SysUserMapper sysUserMapper;

    /** 下单时用户选用的收货地址：分仓的经纬度从这儿取（比 sys_user 上那份准） */
    private final ShopConsumerAddressMapper shopConsumerAddressMapper;

    private final ShopErpInventoryMapper shopErpInventoryMapper;

    /** 下单那一刻顺手把配送单也建出来（重量、距离、配送费都在它里面算），金额要含进主单，所以先问它要数 */
    private final ShopOrderDeliveryService shopOrderDeliveryService;

    /** 这一单用掉的优惠券：算钱、摊到明细行、末尾核销都走它（判据见 ShopOrderCouponService） */
    private final ShopOrderCouponService shopOrderCouponService;

    /** 这一单用掉的积分：校验够不够、折成多少钱、末尾冻结都走它（判据见 ShopOrderPointsService） */
    private final ShopOrderPointsService shopOrderPointsService;

    /** 这一单选没选余额支付：判余额够不够、末尾冻结都走它（判据见 ShopOrderWalletService） */
    private final ShopOrderWalletService shopOrderWalletService;

    /** 只用来把逐行的分折成钱报给试算页（同一个汇率、同一个式子，见 ShopPointsSupport） */
    private final ShopPointsSupport shopPointsSupport;

    /** 订单状态：待支付（sys_order.status，字典 order_status） */
    private static final int ORDER_STATUS_UNPAID = 0;

    /** 明细发货状态：待发货（shop_order_item.ship_status，字典 order_ship_status） */
    private static final int SHIP_STATUS_PENDING = 0;

    /** 业务类型：商品（sys_order.biz_type，字典 order_biz_type）。一单多商品，biz_id 不填 */
    private static final int BIZ_TYPE_PRODUCT = 1;

    /** 订单类型默认值：普通订单（字典 order_type） */
    private static final int DEFAULT_ORDER_TYPE = 1;

    /** 一次下单最多几行：这是公开接口的入口，给个上限防止一次塞进来几千行 */
    private static final int MAX_ITEM_COUNT = 100;

    /** 启用值：shop_product.status 1 上架（前台下单只认这一道；sale_enable 是 ERP 销售单那边的开关，不管前台） */
    private static final int ENABLED = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderCreateVo create(ShopOrderCreateDto form) {
        //主单和明细必须同生共死，事务压根没开就别往下写：不然明细插失败会留下没有明细的孤单
        TransactionUtils.requireActive("下单");
        // 钱那一段整个交给 quote（和「下单试算」共用同一次计算），这里只负责它算完之后开始写库；
        // 那个 true 是"选完仓要把货锁住"，只有真下单才锁
        Quote quote = quote(form, true, "下单");
        SysOrder order = quote.order();
        // 单号到这一步才生成：试算也走 quote，但它不落库，没必要为它排一个用不上的号
        order.setOrderNo(GeneratorNoUtils.getOrderNo());
        try {
            sysOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // uk_order_no：单号是时间戳 + 三位随机，撞上的概率低，但撞上了整单回滚重来比改号简单
            throw new ServiceException("订单号生成冲突，请重新提交");
        }

        // 券在主单落库之后才核销：要拿它的 id 和号写到用户券的 order_id/order_no 上。
        // 还在同一个事务里，所以不存在"券扣了、订单没了"或者反过来 —— 抢券抢输的那一笔是整单回滚，钱一分没付
        shopOrderCouponService.redeem(order.getId(), order.getOrderNo(), quote.coupons());
        // 积分跟券同一拍冻住（流水也要落 order_id/order_no，所以同样在主单之后）：
        // 只把这一笔从可用挪进冻结，真扣走等支付回调；买家没付就反悔的话由取消那一拍整笔解冻
        shopOrderPointsService.freeze(order.getUserId(), order, quote.points());
        // 余额和积分同一拍占住（流水同样要落 order_id/order_no，所以也在主单之后）：
        // 这一笔钱从可用余额冻进冻结余额，支付回调才真扣走；没付就反悔的话由取消那一拍整笔退回钱包
        shopOrderWalletService.freeze(order.getUserId(), order, quote.wallet());

        for (ShopOrderItem item : quote.items()) {
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            // 明细上的下单人跟着主单那一个（可能是参数里替别人下的单，两处必须同一个人）
            item.setUserId(order.getUserId());
            shopOrderItemMapper.insert(item);
        }
        // 配送单跟着主单一起落，状态 0 待派单：这时候货还没出库、钱还没收，
        // 所以它既不进送货员的抢单池（那一批要 status=1），也没法接单，
        // 等支付回调换绑出库单、商品出库页「生成配送单」弹框里点「确认」之后才轮到送货员看见它
        shopOrderDeliveryService.createOnOrder(order, quote.delivery());

        ShopOrderCreateVo vo = new ShopOrderCreateVo();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setDeliveryFee(order.getDeliveryFee());
        vo.setUsedPoints(quote.points().points());
        vo.setPointsAmount(scale(quote.points().money()));
        vo.setPayAmount(order.getPayAmount());
        vo.setItemCount(quote.items().size());
        return vo;
    }

    /**
     * 下单试算：结算页提交之前问一遍"这一单多少钱"，一行都不写。
     *
     * 计算那一段和 create 是同一个 quote（同一段代码，不是同一段逻辑），所以这里报多少、提交就收多少；
     * 唯一差别是它传 lockStock=false —— 试算不该替买家把货占住，那是别人下不进来的写库动作。
     * 下单会拒的单这里一样拒、报同一句话：买家在结算页就该看到"这张券够不上门槛"，
     * 而不是点了提交才弹出来。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderPreviewVo preview(ShopOrderCreateDto form) {
        // 这一路一个字都不落库，但事务还是要开：用券（ShopOrderCouponService#apply）、用分
        //（ShopOrderPointsService#apply）、用余额（ShopOrderWalletService#apply）那三拍都压着
        //「事务没开就别算钱」那道闸 —— 它们防的是"券没收掉、分没冻住、钱却抵了"，
        // 试算共用那一段就别为它开后门
        TransactionUtils.requireActive("下单试算");
        Quote quote = quote(form, false, "试算");
        SysOrder order = quote.order();

        ShopOrderPreviewVo vo = new ShopOrderPreviewVo();
        vo.setItemCount(quote.items().size());
        vo.setGoodsAmount(scale(quote.lineTotal()));
        vo.setDeliveryFee(scale(quote.deliveryFee()));
        vo.setGoodsDiscount(scale(quote.goodsDiscount()));
        vo.setShippingDiscount(scale(quote.shippingDiscount()));
        vo.setDiscountAmount(scale(order.getDiscountAmount()));
        vo.setUsedPoints(quote.points().points());
        vo.setPointsDiscount(scale(quote.points().money()));
        vo.setTotalAmount(scale(order.getTotalAmount()));
        vo.setPayAmount(scale(order.getPayAmount()));
        vo.setCoupons(previewCoupons(quote.coupons()));
        vo.setItems(previewItems(quote.items()));
        return vo;
    }

    /**
     * 这一单的钱，一次算完：建行 → 逐行分仓 → 货值券摊到行上 → 整单那份积分摊到行上 → 每行的积分折成钱 → 按货值实付算配送费 → 免邮券抵这笔运费 → 汇总主单 → 选了余额支付再判一次钱包够不够
     *
     * 统一下单和下单试算都走这一个方法，就是为了"结算页看到的数"和"提交后真收的钱"不可能有两种说法。
     * 除了 lockStock 那一道，这一段自己不写任何表：拿回来的 SysOrder 还是个没落库的壳
     *（buildDraft 要读它上面的下单人和收货地址，所以壳得先拼齐），id 和单号都还没有。
     *
     * @param lockStock 选完仓要不要把这家仓的可用库存锁住 —— 只有统一下单传 true
     * @param action    报错文案里的动作名（"下单" / "试算"），同一句"请先登录"在两个页面上要说清是哪一步
     */
    private Quote quote(ShopOrderCreateDto form, boolean lockStock, String action) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再" + action);
        // 参数里带了 userId 就按参数落（代客下单、后台补单那类"我替某人下单"的调用），
        // 没带还是认登录态
        Long userId = form != null && form.getUserId() != null ? form.getUserId() : login.getId().longValue();
        SysUser buyer = sysUserMapper.selectById(userId);
        if (buyer == null) {
            throw new ServiceException("下单用户不存在或已删除");
        }
        // 就近分仓的起点坐标：用户选用的收货地址优先（那才是这一单真正要寄去的地点），
        // 没传 addressId 才回落到 sys_user 上的经纬度
        Location location = resolveLocation(form, userId, buyer);

        List<ShopOrderCreateDto.Item> lines = form == null ? null : form.getItems();
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("请选择要下单的商品");
        }
        if (lines.size() > MAX_ITEM_COUNT) {
            throw new ServiceException("一次最多下单 " + MAX_ITEM_COUNT + " 种商品");
        }

        // 规格和它所属的商品各一次捞回来，别在循环里逐行查：单价/规格文本/重量在 SKU 上，
        // 名称/主图/门店/分类在那件商品上
        List<Long> skuIds = lines.stream()
                .map(ShopOrderCreateDto.Item::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ShopProductSku> skuMap = skuIds.isEmpty() ? Map.of()
                : shopProductSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ShopProductSku::getId, Function.identity()));
        List<Long> productIds = skuMap.values().stream().map(ShopProductSku::getProductId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ShopProduct> productMap = productIds.isEmpty() ? Map.of()
                : shopProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ShopProduct::getId, Function.identity()));

        List<ShopOrderItem> items = new ArrayList<>(lines.size());
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            ShopOrderItem item = buildItem(lines.get(i), i + 1, skuMap, productMap);
            // 逐行分仓（下单还要锁）：同一商品分成几行时，后一行看到的是前一行刚锁过的可用库存
            //（同一事务里自己的写对自己可见），所以两行不会抢同一批货；
            // 任意一行锁不到就抛异常，前面已经锁掉的和主单一起回滚
            allocate(item, location, i + 1, lockStock);
            items.add(item);
            totalAmount = totalAmount.add(item.getTotalAmount());
        }

        // 用券：这一单的优惠金额在这里由服务端算出来、并摊到各行上（请求体里那一串 couponIds 只说"用哪几张"）。
        // 夹在"分仓"和"算配送费"中间是必须的：配送费要看货值实付（"实付满多少免配送费"那一档），
        // 而免邮券抵的又是配送费，所以只能按 货值券 → 配送费 → 免邮券 这个顺序走一遍
        ShopOrderCouponService.Usage coupons =
                shopOrderCouponService.apply(userId, form.getCouponIds(), items, productsOfSkus(skuMap, productMap));
        BigDecimal goodsDiscount = coupons.getGoodsDiscount();

        // 整单那一份积分先摊到行上，再和逐行那份一起过校验：两者落的是同一个 used_points 格子，
        // 所以行实付、退款按比例还分、取消解冻那三处下游一分都不用改（摊法见 allocatePayPoints）
        allocatePayPoints(items, form.getPayPoints());
        // 用积分：夹在"摊完券"和"算配送费"中间，理由和券那条一样 —— 配送费看的是货值实付，
        // 而积分抵掉的那一份也是货值实付的一部分（这一拍只判够不够、折成多少钱，动账户是 create 末尾的 freeze）
        ShopOrderPointsService.Usage points = shopOrderPointsService.apply(userId, items);

        SysOrder order = new SysOrder();
        order.setOrderType(form.getOrderType() == null ? DEFAULT_ORDER_TYPE : form.getOrderType());
        // 支付方式跟着请求体落主单：其余三档只是记下买家选了什么（支付回调拿实际渠道覆盖），
        // 只有 4 余额要在这儿就定下来 —— 后面三拍（结转/解冻/退款）认"这一单冻着钱包的钱"读的是这一格
        order.setPayType(form.getPayType());
        order.setUserId(userId);
        // 用户名跟着上面的 userId 一起取：参数带了别人的 id 时，这两列才对得上同一个人
        order.setUserName(buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername());
        order.setMobile(buyer.getMobile());
        // 收货地址原样落到主单上：resolveLocation 已经校验过它存在、属于下单人、能用来分仓（有坐标或有地区），
        // 所以这里给个 id 就是"这一单确实寄到它那儿"。支付回调就近分仓、建配送单取收件人都读这一列，
        // 不然过几天买家把地址一改（或删了），这一单要发到哪儿去就没人说得清了
        order.setAddressId(form.getAddressId());
        order.setBizType(BIZ_TYPE_PRODUCT);
        // 配送那一单的数在这一刻定下来：重量按 Σ(商品重量 × 数量)、发货点按上面刚分好的那家仓、
        // 距离按这两个点、这两个点是否同城决定用哪一档规则（同城 1 / 异地 3），配送费按当时那一档里优先级最高的启用规则。
        // 先算再落主单，因为这一笔钱要含进总金额里 —— 买家随后支付的就是这个数，
        // 之后再改规则也不回头改这一单（要改只能退款重下），所以这一格和 sys_order 上那两列是同一份。
        // 货值实付 = Σ 行小计 − 券抵的货钱 − 积分抵的钱：这一格喂给配送费，也就是"实付满多少免配送费"看的那个数
        BigDecimal goodsAmount = totalAmount.subtract(goodsDiscount).subtract(points.money());
        ShopDelivery delivery = shopOrderDeliveryService.buildDraft(order, items, goodsAmount);
        BigDecimal deliveryFee = delivery.getDeliveryFee();
        // 免邮券抵的就是上面这一笔刚算出来的配送费（受这张券的 max_discount 封顶，那一格空着就是全免）：它不落进任何明细行（明细行的优惠只算货，
        // 出库单的金额和成本、退款的钱都只看货），只记在主单的优惠里、从应付金额中扣掉。
        // 配送单上那一格不回改：它记的是"这一趟按计价规则该收多少"，券抵掉的是营销账，两边各记各的
        BigDecimal shippingDiscount = shopOrderCouponService.offsetDeliveryFee(coupons, deliveryFee);
        BigDecimal discountAmount = goodsDiscount.add(shippingDiscount);
        order.setDeliveryFee(deliveryFee);
        order.setFeeRuleId(delivery.getFeeRuleId());
        // ⚠ 从这里起 totalAmount 是"货值 + 配送费"，所以它比 Σ 明细行小计大那一格配送费；
        // 支付回调建出来的出库单金额仍然只有货值（配送费不出货、不进成本）
        order.setTotalAmount(totalAmount.add(deliveryFee));
        // discount_amount 只记券（营销账），积分抵的那一份不进这一格：它记在各行 used_points 上，
        // 退款要按件数把分退回账户、不能退成现金，两笔账混在一格就分不开了
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(order.getTotalAmount().subtract(discountAmount).subtract(points.money()));
        order.setStatus(ORDER_STATUS_UNPAID);
        order.setRemark(form.getRemark());
        // 余额支付走最后这一拍：它吃的就是上面刚算完的实付（这一单要付多少 = 券和分都抵完剩下的那一份），
        // 只判钱包里够不够、要占多少钱，动账户是 create 末尾的 freeze —— 试算到这儿就停，一行都不写
        BigDecimal wallet = shopOrderWalletService.apply(userId, form.getPayType(), order.getPayAmount());
        return new Quote(order, items, delivery, coupons, points, wallet, totalAmount, deliveryFee,
                goodsDiscount, shippingDiscount);
    }

    /**
     * 这一单涉及的那几条规格各自挂在哪件商品下（skuId → 它的 shop_product 那一行），只有优惠券要用：
     * 商品 id 和分类都记在 SPU 那一侧，而订单明细行上只有 skuId，所以拿已经捞回来的两份 map 现拼一张
     *（CouponUtils 那边一条库都不查，范围名单和这张表都由调用方给）。
     * 整行给出去而不是只给 id：判"指定商品"要这一行的 id，判"指定分类"要它的 category_id，一份顶两份。
     */
    private Map<Long, ShopProduct> productsOfSkus(Map<Long, ShopProductSku> skuMap, Map<Long, ShopProduct> productMap) {
        Map<Long, ShopProduct> productBySku = new HashMap<>();
        skuMap.forEach((skuId, sku) -> {
            ShopProduct product = productMap.get(sku.getProductId());
            if (product != null) {
                productBySku.put(skuId, product);
            }
        });
        return productBySku;
    }

    /**
     * 把整单那一份积分（请求体里的 payPoints）摊到明细行上，和买家逐行自己填的那份（Item.usedPoints）合进同一个格子
     *
     * 为什么要合到行上：「行实付 = 小计 − 券优惠 − 积分抵的钱」是退款金额和出库成本的唯一依据，
     * 整单那一份不落到行上，退两件货就不知道该还买家几分（口径同券那一轮"优惠必须摊到行上"）。
     *
     * 摊法：先量每一行还承接得住多少分（剩余可抵的钱 = 小计 − 券摊的优惠 − 行级那份折的钱，换成分、向下取整，
     * 一行最多把自己抵到 0），再按这笔剩余的比例分一遍、向下取整并卡在各行上限内，
     * 凑不满一分的尾数从末行往前补满 —— 上限之和不够 payPoints 就当场拒单，绝不悄悄少冻几分。
     *
     * 这一趟只改内存里的明细行：够不够、折多少钱是它后面 apply 那一拍判的，动账户是 create 末尾的 freeze。
     */
    private void allocatePayPoints(List<ShopOrderItem> items, Integer payPoints) {
        if (payPoints == null || payPoints == 0) {
            return;
        }
        if (payPoints < 0) {
            throw new ServiceException("整单使用的积分不能是负数");
        }
        BigDecimal[] rooms = new BigDecimal[items.size()];
        int[] caps = new int[items.size()];
        BigDecimal roomTotal = BigDecimal.ZERO;
        int capacity = 0;
        for (int i = 0; i < items.size(); i++) {
            ShopOrderItem line = items.get(i);
            // 先把这一份负数拦下来：折算的时候 <=0 一律折成 0 元，这一行的上限于是按整格算，
            // 摊完合并成 given+负数 才过 apply 的"不能是负数"那一判 —— 负数会把整单那几分悄悄吃掉，
            // 买家少抵钱、真冻住的分比 payPoints 还少，而两处都不报错。不合并之前先拒掉才说得清是谁的问题
            Integer linePoints = line.getUsedPoints();
            if (linePoints != null && linePoints < 0) {
                throw new ServiceException("第 " + (i + 1) + " 行商品使用的积分不能是负数");
            }
            // 和 ShopOrderPointsService#apply 同一个式子减到底：那一拍随后拿的是"行级 + 这一趟摊的"合起来再判一次
            rooms[i] = CouponUtils.money(line.getTotalAmount())
                    .subtract(CouponUtils.money(line.getDiscountAmount()))
                    .subtract(shopPointsSupport.moneyOf(line.getUsedPoints()))
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            caps[i] = shopPointsSupport.pointsOf(rooms[i]);
            roomTotal = roomTotal.add(rooms[i]);
            capacity += caps[i];
        }
        if (payPoints > capacity) {
            throw new ServiceException("这一单最多只能用 " + capacity + " 积分抵钱（整单那 " + payPoints
                    + " 分没有商品行承接得住，多出来的那一份抵不出去）");
        }
        int[] given = new int[items.size()];
        int left = payPoints;
        if (roomTotal.compareTo(BigDecimal.ZERO) > 0) {
            for (int i = 0; i < items.size(); i++) {
                given[i] = Math.min(BigDecimal.valueOf(payPoints).multiply(rooms[i])
                        .divide(roomTotal, 0, RoundingMode.FLOOR).intValue(), caps[i]);
                left -= given[i];
            }
        }
        for (int i = items.size() - 1; i >= 0 && left > 0; i--) {
            int add = Math.min(caps[i] - given[i], left);
            if (add > 0) {
                given[i] += add;
                left -= add;
            }
        }
        for (int i = 0; i < items.size(); i++) {
            ShopOrderItem line = items.get(i);
            if (given[i] > 0) {
                line.setUsedPoints((line.getUsedPoints() == null ? 0 : line.getUsedPoints()) + given[i]);
            }
        }
    }

    /** 试算要逐张报出去的券：真被用掉的（带各自抵了多少）+ 压在账上这一单没消掉的免邮券（抵 0、也不核销） */
    private List<ShopOrderPreviewVo.Coupon> previewCoupons(ShopOrderCouponService.Usage usage) {
        List<ShopOrderPreviewVo.Coupon> list = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();
        for (ShopOrderCouponService.Usage.Entry entry : usage.getEntries()) {
            usedIds.add(entry.coupon().getId());
            list.add(previewCoupon(entry.coupon(), entry.discount(), true));
        }
        for (ShopActivityCouponUser pending : usage.getPendingFreeShipping()) {
            if (!usedIds.contains(pending.getId())) {
                list.add(previewCoupon(pending, BigDecimal.ZERO, false));
            }
        }
        return list;
    }

    private ShopOrderPreviewVo.Coupon previewCoupon(ShopActivityCouponUser coupon, BigDecimal discount, boolean used) {
        ShopOrderPreviewVo.Coupon item = new ShopOrderPreviewVo.Coupon();
        item.setCouponId(coupon.getId());
        // 券名称是发放时的快照，万一那格空着就退回券码，总之得让买家认出是哪一张
        item.setCouponName(StringUtils.defaultIfBlank(coupon.getCouponName(), coupon.getCouponCode()));
        item.setCouponType(coupon.getCouponType());
        item.setDiscountAmount(scale(discount));
        item.setUsed(used);
        return item;
    }

    /** 试算的逐行抵扣：行实付（小计 − 券优惠 − 积分抵的钱）就是提交后落在 shop_order_item 上那几个数，退款和出库都按它 */
    private List<ShopOrderPreviewVo.Item> previewItems(List<ShopOrderItem> items) {
        List<ShopOrderPreviewVo.Item> list = new ArrayList<>(items.size());
        for (ShopOrderItem item : items) {
            ShopOrderPreviewVo.Item line = new ShopOrderPreviewVo.Item();
            line.setSkuId(item.getSkuId());
            line.setProductName(item.getProductName());
            line.setSpecText(item.getSpecText());
            line.setPrice(item.getPrice());
            line.setQuantity(item.getQuantity());
            line.setTotalAmount(scale(item.getTotalAmount()));
            line.setDiscountAmount(scale(item.getDiscountAmount()));
            BigDecimal pointsAmount = shopPointsSupport.moneyOf(item.getUsedPoints());
            line.setUsedPoints(item.getUsedPoints());
            line.setPointsAmount(scale(pointsAmount));
            line.setPaidAmount(scale(item.getTotalAmount()).subtract(scale(item.getDiscountAmount()))
                    .subtract(scale(pointsAmount)));
            list.add(line);
        }
        return list;
    }

    /** 回给前端的金额一律两位小数（库里就是 decimal(10,2)，别让 5.5 和 5.50 两种写法混着出去） */
    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 一行明细：校验入参，再从 SKU 表抄单价和规格文本、从它所属的商品抄名称、主图、门店，落成下单时的快照
     *
     * @param no 第几行（从 1 开始，只用来拼报错文案）
     */
    private ShopOrderItem buildItem(ShopOrderCreateDto.Item line, int no,
                                    Map<Long, ShopProductSku> skuMap,
                                    Map<Long, ShopProduct> productMap) {
        if (line == null || line.getSkuId() == null) {
            throw new ServiceException("第 " + no + " 行没选规格");
        }
        Integer quantity = line.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new ServiceException("第 " + no + " 行的购买数量必须大于 0");
        }
        ShopProductSku sku = skuMap.get(line.getSkuId());
        if (sku == null) {
            throw new ServiceException("第 " + no + " 行的规格不存在或已删除");
        }
        // 商品行是跟着 SKU 捞回来的那一批里按 sku.product_id 取的，这里读不到就是那件商品被删了
        ShopProduct product = productMap.get(sku.getProductId());
        if (product == null) {
            throw new ServiceException("第 " + no + " 行的商品不存在或已删除");
        }
        // 只认上下架这一道：商品下了架就不该再被下单（能走到这里多半是详情页开着期间后台把它下了）。
        // 规格的 sale_enable 不参与 —— 那是 ERP 建销售单那边的开关，跟前台能不能买无关
        if (!Integer.valueOf(ENABLED).equals(product.getStatus())) {
            throw new ServiceException("商品「" + product.getProductName() + "」已下架，不能下单");
        }
        if (sku.getProductPrice() == null) {
            throw new ServiceException("商品「" + product.getProductName() + "」的这个规格还没定价，不能下单");
        }

        BigDecimal price = sku.getProductPrice();
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        ShopOrderItem item = new ShopOrderItem();
        item.setSkuId(sku.getId());
        item.setProductName(product.getProductName());
        // 主图优先用这个规格自己那张，没单独配才退回商品主图（和前台详情页同一口径）
        item.setImage(StringUtils.defaultIfBlank(sku.getSkuImage(), product.getMainImage()));
        item.setStoreId(product.getStoreId());
        // 规格文本不给客户端填：这一格是 shop_product_sku 上由规格字典现拼出来的那一份快照，
        // 页面传来的字符串既对不上账也说不清是哪个规格（同单价的口径）
        item.setSpecText(sku.getSpecText());
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setTotalAmount(total);
        // 这一格建行的时候给 0，随后由 ShopOrderCouponService#apply 把券的抵扣摊上来（2026-09-29 起请求体里
        // 就没有"这一行优惠多少"那一格了，优惠一律服务端算）。先给 0 而不是留 NULL，
        // 是因为券那边的"这一行还能抵多少 = 小计 − 已摊优惠"要拿它做起点
        item.setDiscountAmount(BigDecimal.ZERO);
        // 这一格跟请求体一对一：买家自己选"这一行花几分"，服务端只认这一个数、也只按它折钱
        //（能抵多少钱是 ShopPointsSupport 按 sys_config 那一档汇率现算的，客户端传了金额也不认）。
        // 不传就是这一行不用分，落 0 而不是留 NULL —— 退款那一路要按这一格算还多少分
        item.setUsedPoints(line.getUsedPoints() == null ? 0 : line.getUsedPoints());
        item.setShipStatus(SHIP_STATUS_PENDING);
        return item;
    }

    /**
     * 就近分仓的起点：收货地址（用户这一单选的那一个）优先，没传才用用户档案上的经纬度。
     *
     * 只认 addressId、不认客户端直接传上来的经纬度：坐标是"算发货仓"的依据，
     * 让前端随便填就等于让它挑一家离自己最近、货最好抢的仓，口径同单价和金额。
     *
     * 地址上没录经纬度不再拦下单：那一条的省/市/区三段行政区划代码还能拿去匹同地区的仓
     * （见 ShopErpInventoryMapper#selectWarehouseIdByArea），坐标只是更准的那把尺子，
     * 不是唯一的尺子。两份都没有才报错 —— 那时候是真的无从分仓。
     */
    private Location resolveLocation(ShopOrderCreateDto form, Long userId, SysUser buyer) {
        Long addressId = form == null ? null : form.getAddressId();
        if (addressId != null) {
            ShopConsumerAddress address = shopConsumerAddressMapper.selectById(addressId);
            if (address == null) {
                throw new ServiceException("所选收货地址不存在或已删除");
            }
            // 别人的地址也能拿 id 打到这里，那样会把货往"离收货人最近"的仓锁，必须拦住
            if (address.getUserId() == null || !address.getUserId().equals(userId)) {
                throw new ServiceException("所选收货地址不属于下单用户，不能用来下单");
            }
            String[] area = areaParts(address.getAreaCode());
            Location location = new Location(address.getLongitude(), address.getLatitude(),
                    area[0], area[1], area[2]);
            if (!location.hasPoint() && !location.hasArea()) {
                // 报错里带上是哪一个地址：一个消费者名下往往有好几条
                String where = address.getDetail() == null || address.getDetail().isBlank()
                        ? "ID " + address.getId() : address.getDetail();
                throw new ServiceException("收货地址「" + where + "」既没设置经纬度也没选所在地区，无法分仓下单");
            }
            return location;
        }
        if (buyer.getLongitude() == null || buyer.getLatitude() == null) {
            throw new ServiceException("用户「" + (buyer.getNickname() == null ? buyer.getUsername() : buyer.getNickname())
                    + "」还没设置经纬度，也没选用收货地址，无法就近分仓下单");
        }
        return new Location(buyer.getLongitude(), buyer.getLatitude(), null, null, null);
    }

    /** area_code（"110000,110100,110101"）拆成省/市/区三段，缺哪一段给 null；整格空就是三个 null */
    private String[] areaParts(String areaCode) {
        String[] parts = new String[]{null, null, null};
        if (StringUtils.isBlank(areaCode)) {
            return parts;
        }
        String[] values = areaCode.split(",");
        for (int i = 0; i < values.length && i < parts.length; i++) {
            parts[i] = StringUtils.trimToNull(values[i]);
        }
        return parts;
    }

    /**
     * 给一行明细定发货仓库，下单还要把这家仓的这一行库存锁住（试算只定仓，不锁）
     *
     * 候选仓按"这一条规格在这家仓还够卖"筛（可用库存 = 总库存 - 已锁定），两步挑：
     * 收货地有坐标就按球面距离取最近的一家（selectNearestWarehouseId）；
     * 没坐标（或按距离挑不出仓）再拿它的省/市/区去匹同地区的仓（selectWarehouseIdByArea，区 > 市 > 省）。
     *
     * 锁之前再单独量一次可用库存（他要的"在锁库前再判断一次库存，没有库存则提示用户"）：
     * 挑仓那两条 SQL 已经带过同样的判断，这一道不改变结论，改变的是**说给用户的话** ——
     * 各仓合计不够就说"库存不足：合计 a 件、需要 b 件"，就近这家不够就说"就近的仓库可用 a 件"，
     * 而不是含糊的"没有可发货的仓库"。
     * 并发超卖仍然靠 lockStock 那条 UPDATE 自己的 WHERE 挡：返回 0 行就是被别人抢完了。
     *
     * @param no 第几行（从 1 开始，只用来拼报错文案）
     * @param lockStock 要不要真把货锁住：统一下单要，试算不要（只挑仓，一行都不写）
     */
    private void allocate(ShopOrderItem item, Location location, int no, boolean lockStock) {
        Long warehouseId = null;
        if (location.hasPoint()) {
            warehouseId = shopErpInventoryMapper.selectNearestWarehouseId(item.getSkuId(),
                    location.longitude(), location.latitude(), item.getQuantity());
        }
        if (warehouseId == null && location.hasArea()) {
            warehouseId = shopErpInventoryMapper.selectWarehouseIdByArea(item.getSkuId(),
                    location.province(), location.city(), location.district(), item.getQuantity());
        }
        if (warehouseId == null) {
            // 分不出仓有两种原因，得说清楚是哪一种：没货是买家自己的事，匹不到仓是平台数据没录全
            int totalAvailable = available(item.getSkuId(), null);
            if (totalAvailable < item.getQuantity()) {
                throw new ServiceException("第 " + no + " 行商品「" + item.getProductName() + "」库存不足：各仓可用合计 "
                        + totalAvailable + " 件，本次需要 " + item.getQuantity() + " 件");
            }
            throw new ServiceException("第 " + no + " 行商品「" + item.getProductName()
                    + "」附近有货，但发不出去：没有一家启用仓库对得上这个收货地");
        }
        int available = available(item.getSkuId(), warehouseId);
        if (available < item.getQuantity()) {
            throw new ServiceException("第 " + no + " 行商品「" + item.getProductName() + "」库存不足：就近的仓库可用 "
                    + available + " 件，本次需要 " + item.getQuantity() + " 件");
        }
        if (lockStock && shopErpInventoryMapper.lockStock(item.getSkuId(), warehouseId, item.getQuantity()) == 0) {
            throw new ServiceException("第 " + no + " 行商品「" + item.getProductName()
                    + "」的库存刚被其它订单锁走，请重新提交");
        }
        item.setWarehouseId(warehouseId);
    }

    /**
     * 可用库存查询的兜底：mapper 那条 SQL 里 COALESCE 过，正常不会返回 null，
     * 但这一列拿不到数就当 0 处理 —— 报"库存不足"总比 NPE 把下单原因盖掉好
     */
    private int available(Long skuId, Long warehouseId) {
        Integer stock = shopErpInventoryMapper.selectAvailableStock(skuId, warehouseId);
        return stock == null ? 0 : stock;
    }

    /**
     * 分仓的起点：收货地址上的经纬度和省/市/区三段行政区划代码（档案回落那一路只有坐标）。
     * 有坐标就按距离挑仓，只有地区就按地区挑，resolveLocation 保证这两份至少有一份。
     */
    private record Location(BigDecimal longitude, BigDecimal latitude,
                            String province, String city, String district) {

        boolean hasPoint() {
            return longitude != null && latitude != null;
        }

        boolean hasArea() {
            return province != null || city != null || district != null;
        }
    }

    /**
     * {@link #quote} 算完的那一单：主单还是个没落库的壳（id、单号都没有），明细行已经定了仓、
     * 券已经摊到行上、分已经逐行折成钱、配送费也定了。
     *
     * 下单拿它去 insert，试算拿它去拼 ShopOrderPreviewVo —— 两份数出自同一次计算。
     * lineTotal 是 Σ 行小计（原价口径），货值实付 = lineTotal − goodsDiscount − points.money，
     * 也就是当初喂给 buildDraft 的那个数（「实付满多少免配送费」那一档看的是货值实付，不是它）。
     * wallet 是这一单要从钱包里占住多少钱（没选余额支付就是 0 元）：它不是新的扣减项，数就是上面刚算出来的
     * payAmount —— 余额付的是这一单实付的那一整笔，所以主单上不用再为它开一格。
     */
    private record Quote(SysOrder order, List<ShopOrderItem> items, ShopDelivery delivery,
                         ShopOrderCouponService.Usage coupons, ShopOrderPointsService.Usage points,
                         BigDecimal wallet, BigDecimal lineTotal, BigDecimal deliveryFee,
                         BigDecimal goodsDiscount, BigDecimal shippingDiscount) {
    }
}
