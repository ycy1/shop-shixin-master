package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.api.service.ShopRefundCreateService;
import com.shop.dto.refund.ShopRefundCreateDto;
import com.shop.entity.ShopConsumerAddress;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.SysOrder;
import com.shop.entity.SysRefund;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.SysRefundMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.refund.ShopRefundCreateVo;
import com.shop.vo.refund.ShopRefundItemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一退货 服务实现：一次调用只落一张 sys_refund（status=0 待审核）
 *
 * 到"提交申请"这一步为止：不改 sys_order.status、不动 shop_order_item.returned_quantity、
 * 不碰库存，也不建任何出入库单 —— 那些都在退款真正到账那一刻才做（见 ShopRefundCallbackServiceImpl）。
 * 所以审核人拒掉这张单，库里除了 sys_refund 多一行 2 审核拒绝之外什么都没有发生过：
 * 明细行的可退数一分没被吃掉，同一批货还能再提一次。
 *
 * 三件由服务端说了算的事（表单传了也不认）：
 * 1. **金额**：这一行实付 = 明细的成交小计 − 这一行的优惠（和统一下单落 pay_amount 同一口径）。
 *    整行退完就取这一行的全部实付；只退一部分按件数摊（两位小数四舍五入），
 *    所以"分几次把一行退完"的几笔相加可能比这一行实付多出一两分 —— 末尾那一次会被下面那道
 *    "整单累计不超过 pay_amount"的闸挡回来，不会真多退钱。
 * 2. **退款类型**：这次退完，这一单的每一行是不是都退满了（已退 + 本次 = 购买数量）——
 *    全退满记 1 全额退款，还留着能退的行记 2 部分退款。这一档也是回调要不要把主单推成"已退款"的依据。
 * 3. **行级申请内容**：退哪几行、各退几件、各退多少钱，序列化进 sys_refund.extra 那份 JSON 数组，
 *    建单这一刻写定、之后不改。回调就是照它把各行 returned_quantity 加上去的。
 *
 * 两道闸（2026-09-22 改过一次口径：一张订单**可以同时挂着好几张在途退款单**，每次退的商品和件数都可以不一样，
 * 他原话"可以多次退款 有可能每次退的商品和数量不一样"）：
 * ① 每一行的本次件数不超过"购买数量 − 已退货数量 − 其它在途退款单已经占掉的件数"（占用按行从各张 extra 加回来），
 *    而且同一个明细行一次只让提一条（不然两行加同一行，可退数那道判据各算各的、加起来超了）；
 * ② 这一单退出去或在路上的钱（在途那几张按 refund_amount 占额度）加上本次，不超过实付金额。
 * 之前是"有一张在途就直接拒第二张"，那种一刀切把上面这种正常用法也拦死了，所以换成按行占额度；
 * 钱那一侧一直是按额度算的（见 returnedAmountOf），没动。
 *
 * 鉴权和下单那两接口同理：/api/** 不过 Sa-Token 拦截器，服务层自己取登录态；
 * 消费者账号（account_type=3）只能退自己名下那一张，其它账号（平台 / 门店 / ERP 那三位在后台点的）
 * 算"代客提退"，退给谁由这一单的 user_id 说，不由点按钮的人说。
 */
@Service
@RequiredArgsConstructor
public class ShopRefundCreateServiceImpl implements ShopRefundCreateService {

    /** 找单、捞这一单的商品行、算行实付：和支付回调、统一下单共用同一份取数（见 {@link ShopOrderSupport}） */
    private final ShopOrderSupport shopOrderSupport;

    private final SysRefundMapper sysRefundMapper;

    /** 退款单上的申请人快照（sys_refund 的冗余列），按订单的 user_id 取，不是按点按钮的人 */
    private final SysUserMapper sysUserMapper;

    private final ShopConsumerAddressMapper shopConsumerAddressMapper;

    /** 订单状态（字典 order_status）：只有已支付 / 已完成 的单子能申请退货 */
    private static final int ORDER_STATUS_PAID = 1;

    private static final int ORDER_STATUS_FINISHED = 2;

    /** 退款状态（字典 refund_status）：待审核 —— 建出来的单恒是这个值，审核那一步在后台退款管理页 */
    private static final int REFUND_STATUS_PENDING = 0;

    /** 退款状态：退款中 —— 后台点「审核通过」直接落到这一档（这一档就是"审过了、等退款回调登记结果"） */
    private static final int REFUND_STATUS_REFUNDING = 3;

    /** 还在流程里的（in-flight）那两档：0 待审核 / 3 退款中。钱按 refund_amount 占整单额度，件数按各张 extra 占行级额度 */
    private static final List<Integer> REFUND_STATUS_IN_PROGRESS = Arrays.asList(REFUND_STATUS_PENDING, REFUND_STATUS_REFUNDING);

    /** 退款类型（字典 refund_type）：1 全额退款 2 部分退款 */
    private static final int REFUND_TYPE_FULL = 1;

    private static final int REFUND_TYPE_PART = 2;

    /** 币种兜底：sys_order.currency 建表就有默认值 CNY，真为空时也不让这一格空着 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 一次最多几行：这是能直接被外部调的入口，给个上限防止一次塞进来几千行 */
    private static final int MAX_ITEM_COUNT = 100;

    /** 只用来把 sys_refund.extra 那份商品行快照序列化成 JSON（读回去在回调那边） */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopRefundCreateVo create(ShopRefundCreateDto form) {
        //退款单和它那份行快照要一起落，事务压根没开就别往下写
        TransactionUtils.requireActive("退货");
        LoginUserUtils.requireLoginUser("后再提交退货");
        SysOrder order = resolveOrder(form);
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new ServiceException("请勾选要退的商品");
        }
        if (form.getItems().size() > MAX_ITEM_COUNT) {
            throw new ServiceException("一次最多退 " + MAX_ITEM_COUNT + " 行商品");
        }

        // 其它在途退款单（0/3）已经按行占掉的件数：同一批货不能被两张单各退一遍，
        // 但**不同行、不同件数**可以分开提，所以这里不拦整单，只把每一行的可退数往下压（闸①要用）
        Long orderId = order.getId();
        Map<Long, Integer> occupied = inFlightQuantityByLine(orderId);

        List<ShopOrderItem> lines = shopOrderSupport.orderLines(orderId);
        if (lines.isEmpty()) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」没有商品明细，无法退货");
        }
        Map<Long, ShopOrderItem> lineMap = lines.stream()
                .collect(Collectors.toMap(ShopOrderItem::getId, Function.identity()));

        // ① 逐行判可退数，同时把这一行的退款金额算出来（表单传的金额一律不看）
        List<ShopRefundItemVo> items = new ArrayList<>(form.getItems().size());
        Set<Long> used = new HashSet<>();
        BigDecimal refundAmount = BigDecimal.ZERO;
        int returnQuantity = 0;
        for (int i = 0; i < form.getItems().size(); i++) {
            ShopRefundCreateDto.Item row = form.getItems().get(i);
            if (row == null || row.getOrderItemId() == null) {
                throw new ServiceException("第 " + (i + 1) + " 行退货明细缺少订单明细行，请刷新后重新提交");
            }
            if (!used.add(row.getOrderItemId())) {
                throw new ServiceException("同一条商品行提交了两次，请刷新后重新提交");
            }
            ShopOrderItem line = lineMap.get(row.getOrderItemId());
            if (line == null) {
                throw new ServiceException("第 " + (i + 1) + " 行商品不在这张订单里，不能退货");
            }
            String name = "第 " + (i + 1) + " 行商品「" + line.getProductName() + "」";
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            int returned = line.getReturnedQuantity() == null ? 0 : line.getReturnedQuantity();
            int inFlight = occupied.getOrDefault(line.getId(), 0);
            int remaining = quantity - returned - inFlight;
            Integer want = row.getQuantity();
            if (want == null || want <= 0) {
                throw new ServiceException(name + "请选择要退几件");
            }
            if (want > remaining) {
                // 报出去的三个数分开说：已退是"钱已经退掉了"，在途是"另一张退款单先把这几件占住了"，
                // 处理完那一张（审核拒绝/退款失败/取消都算）这几件会还回来
                throw new ServiceException(name + "最多还能退 " + Math.max(remaining, 0) + " 件（本单共 "
                        + quantity + " 件，已退 " + returned + " 件，另有 " + inFlight + " 件在别的退款单里没处理完）");
            }
            BigDecimal paid = shopOrderSupport.paidAmount(line, name, "无法计算退款金额");
            // 整行退完取这一行的全部实付，只退一部分才按件摊：摊出来的分位才不会把整行的钱摊丢。
            // want == quantity 只可能发生在这一行之前一分没退过、这一张就把整行包完的时候
            // （退过或有人在途的，上面那道 remaining 已经先把 want 压到 quantity 以下了）
            BigDecimal amount = want == quantity ? paid
                    : paid.multiply(BigDecimal.valueOf(want))
                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
            ShopRefundItemVo item = new ShopRefundItemVo();
            item.setOrderItemId(line.getId());
            item.setSkuId(line.getSkuId());
            item.setQuantity(want);
            item.setAmount(amount);
            items.add(item);
            refundAmount = refundAmount.add(amount);
            returnQuantity += want;
        }
        refundAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);

        // ② 这一单退出去的钱（已到账 + 在流程里的）加上这次，不能超过实付金额
        BigDecimal already = returnedAmountOf(orderId);
        BigDecimal payable = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        if (already.add(refundAmount).compareTo(payable) > 0) {
            throw new ServiceException("退款金额 " + refundAmount + " 超出这一单还能退的 "
                    + payable.subtract(already).setScale(2, RoundingMode.HALF_UP)
                    + " 元（本单实付 " + payable + " 元，已退或在途 " + already + " 元）");
        }

        SysRefund refund = new SysRefund();
        refund.setRefundNo(GeneratorNoUtils.getRefundNo());
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        // 申请人跟着订单走：代客提退时点按钮的人不是申请人，冗余那两列也不能记成他
        refund.setUserId(order.getUserId());
        fillBuyer(refund, order);
        refund.setAddressId(resolveAddress(form, order));
        refund.setRefundType(fullRefundAfterThis(lines, items, occupied) ? REFUND_TYPE_FULL : REFUND_TYPE_PART);
        refund.setRefundReason(form.getRefundReason() == null || form.getRefundReason().isBlank()
                ? null : form.getRefundReason().trim());
        refund.setRefundAmount(refundAmount);
        // actual_refund_amount 建单时就填申请的那一份：这一列 NOT NULL 且没有默认值，
        // 空着插不进去；后台审核通过时想少退就在「审核」里改它（见 SysRefundServiceImpl#audit）
        refund.setActualRefundAmount(refundAmount);
        refund.setCurrency(order.getCurrency() == null || order.getCurrency().isBlank()
                ? DEFAULT_CURRENCY : order.getCurrency());
        refund.setStatus(REFUND_STATUS_PENDING);
        refund.setApplyTime(LocalDateTime.now());
        refund.setExtra(toJson(items));
        // refund_channel / third_refund_no / 审核那几列 / refund_time 一律留 NULL：这一刻既没渠道也没人审
        try {
            sysRefundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            // uk_refund_no：单号是时间戳 + 三位随机，撞上的概率低，但撞上了整单回滚重来比改号简单
            throw new ServiceException("退款单号生成冲突，请重新提交");
        }

        ShopRefundCreateVo vo = new ShopRefundCreateVo();
        vo.setRefundId(refund.getId());
        vo.setRefundNo(refund.getRefundNo());
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(refund.getStatus());
        vo.setRefundType(refund.getRefundType());
        vo.setRefundAmount(refundAmount);
        vo.setItemCount(items.size());
        vo.setReturnQuantity(returnQuantity);
        vo.setAddressId(refund.getAddressId());
        vo.setApplyTime(refund.getApplyTime());
        return vo;
    }

    /**
     * 找单（orderId 优先、orderNo 兜底，和支付回调那一条同一份取数）+ 退货这一路自己的两道判据：
     * 谁的单、哪一档状态能提退。删掉的订单在取数那一步就找不到，不会走到这两道
     */
    private SysOrder resolveOrder(ShopRefundCreateDto form) {
        if (form == null) {
            throw new ServiceException("缺少退货参数");
        }
        SysOrder order = shopOrderSupport.requireOrder(form.getOrderId(), form.getOrderNo());
        // 消费者账号只能动自己那一张；平台 / 门店 / ERP 账号算代客提退，退给谁看订单的 user_id
        if (!LoginUserUtils.inOwnScope(order.getUserId())) {
            throw new ServiceException("只能退自己名下的订单");
        }
        Integer status = order.getStatus();
        if (status == null || (status != ORDER_STATUS_PAID && status != ORDER_STATUS_FINISHED)) {
            throw new ServiceException("只有「已支付 / 已完成」的订单能申请退货，这一单现在的状态不行");
        }
        return order;
    }

    /**
     * 这一单已经退掉或在路上的钱：4 退款成功 的按 actual_refund_amount 算（那一格才是真退出去的钱），
     * 0/3 那两档按 refund_amount 占住额度（不然可以同时提两张把同一批货退两遍钱）。
     * 2 审核拒绝 / 5 退款失败 / 6 已取消 的不在流程里了，也不占额度，所以能重新提
     */
    private BigDecimal returnedAmountOf(Long orderId) {
        List<SysRefund> exists = sysRefundMapper.selectList(new LambdaQueryWrapper<SysRefund>()
                .select(SysRefund::getStatus, SysRefund::getRefundAmount, SysRefund::getActualRefundAmount)
                .eq(SysRefund::getOrderId, orderId));
        BigDecimal sum = BigDecimal.ZERO;
        for (SysRefund refund : exists) {
            Integer status = refund.getStatus();
            if (status == null) {
                continue;
            }
            if (status == 4) {
                sum = sum.add(refund.getActualRefundAmount() == null ? BigDecimal.ZERO : refund.getActualRefundAmount());
            } else if (REFUND_STATUS_IN_PROGRESS.contains(status)) {
                sum = sum.add(refund.getRefundAmount() == null ? BigDecimal.ZERO : refund.getRefundAmount());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 名下那些还在流程里的退款单（0 待审核 / 3 退款中）**按行**已经占掉的件数。
     *
     * 一张订单可以同时挂好几张在途退款单（每次退的商品、件数都可以不一样），所以拦的不是"有在途的就不给再提"，
     * 而是把每一行已经被占走的量从可退数里减掉 —— 不然同一批货能被两张单各退一遍，
     * 两张的回调各自往那行的 returned_quantity 上加一次（increaseReturnedQuantity 那道 WHERE 会拦下第二张，
     * 但那是钱已经答应退出去之后才炸，不如建单时就算清楚）。
     * 占了几件从各张自己的 extra 里加回来（建单那一刻写定的申请快照，之后不改）。
     * 已到账（4）的不算：那一档已经落在明细行的 returned_quantity 上了；被拒 / 失败 / 取消（2/5/6）的更不算，
     * 那三档的钱和货都没走，额度自然还回来，所以那种单子还能接着提下一张。
     */
    private Map<Long, Integer> inFlightQuantityByLine(Long orderId) {
        List<SysRefund> inFlight = sysRefundMapper.selectList(new LambdaQueryWrapper<SysRefund>()
                .select(SysRefund::getId, SysRefund::getRefundNo, SysRefund::getExtra)
                .eq(SysRefund::getOrderId, orderId)
                .in(SysRefund::getStatus, REFUND_STATUS_IN_PROGRESS));
        Map<Long, Integer> occupied = new HashMap<>();
        for (SysRefund refund : inFlight) {
            if (refund.getExtra() == null || refund.getExtra().isBlank()) {
                // 后台手工建的、这一版之前的老退款单没登记行级内容：它占几件说不清，只能按 0 算，
                // 那一笔的钱照样在 returnedAmountOf 里占着整单额度
                continue;
            }
            List<ShopRefundItemVo> rows;
            try {
                rows = JSON.readValue(refund.getExtra(), new TypeReference<List<ShopRefundItemVo>>() {
                });
            } catch (JsonProcessingException e) {
                throw new ServiceException("在途退款单「" + refund.getRefundNo()
                        + "」的退货商品行解析失败，核对不了这一单还能退几件，已中止处理");
            }
            if (rows == null) {
                continue;
            }
            for (ShopRefundItemVo row : rows) {
                if (row.getOrderItemId() != null && row.getQuantity() != null) {
                    occupied.merge(row.getOrderItemId(), row.getQuantity(), Integer::sum);
                }
            }
        }
        return occupied;
    }

    /**
     * 这次退完，这一单是不是每一行都退满了（全退满记 1 全额退款）。
     * 判据只看行，不和金额比：一行分几次退、按件摊出来的分位差一两分，都不影响"这批货全退了没有"。
     * 在途那几张占的件数要一起算进去：三张单各退一行、合起来正好把整单退完时，最后建的那一张记 1 全额。
     * 这一格只是给后台看的说法 —— 回调推不推主单成"已退款"看的是库里的实时行状态，
     * 不读建单时算的这个值（见 ShopRefundCallbackServiceImpl#pushOrderRefunded）
     */
    private boolean fullRefundAfterThis(List<ShopOrderItem> lines, List<ShopRefundItemVo> items,
                                        Map<Long, Integer> occupied) {
        Map<Long, Integer> wantByLine = items.stream()
                .collect(Collectors.toMap(ShopRefundItemVo::getOrderItemId, ShopRefundItemVo::getQuantity, Integer::sum));
        for (ShopOrderItem line : lines) {
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            int returned = line.getReturnedQuantity() == null ? 0 : line.getReturnedQuantity();
            int want = wantByLine.getOrDefault(line.getId(), 0);
            int inFlight = occupied.getOrDefault(line.getId(), 0);
            if (returned + inFlight + want < quantity) {
                return false;
            }
        }
        return true;
    }

    /**
     * 退货地址：表单选了就用表单那条（必须是这一单下单人名下的地址，别人的地址拦掉），
     * 没选回落到这一单当初的收货地址（sys_order.address_id）—— 寄回哪儿和当初寄到哪儿是同一条，
     * 这一格才敢留选填。两边都没有就存 NULL，回调和后台审核那一页都不依赖它
     */
    private Long resolveAddress(ShopRefundCreateDto form, SysOrder order) {
        if (form.getAddressId() == null) {
            return order.getAddressId();
        }
        ShopConsumerAddress address = shopConsumerAddressMapper.selectById(form.getAddressId());
        if (address == null) {
            throw new ServiceException("所选退货地址不存在或已删除");
        }
        if (address.getUserId() == null || !address.getUserId().equals(order.getUserId())) {
            throw new ServiceException("所选退货地址不属于这一单的下单人，不能用作退货地址");
        }
        return address.getId();
    }

    /** 申请人昵称/手机号冗余：订单上那一份就是下单时的快照，优先抄它；为空才回落去查 sys_user */
    private void fillBuyer(SysRefund refund, SysOrder order) {
        refund.setUserName(order.getUserName());
        refund.setMobile(order.getMobile());
        if ((refund.getUserName() == null || refund.getUserName().isBlank()) && order.getUserId() != null) {
            SysUser buyer = sysUserMapper.selectById(order.getUserId());
            if (buyer != null) {
                refund.setUserName(buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername());
                if (refund.getMobile() == null) {
                    refund.setMobile(buyer.getMobile());
                }
            }
        }
    }

    private String toJson(List<ShopRefundItemVo> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new ServiceException("退货商品行序列化失败，已中止处理");
        }
    }
}
