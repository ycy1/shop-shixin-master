package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.entity.ShopOrderItem;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.SysRefundMapper;
import com.shop.service.ShopOrderItemService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.order.ShopOrderItemVo;
import com.shop.vo.order.ShopOrderVo;
import com.shop.vo.refund.ShopOrderRefundItemVo;
import com.shop.vo.refund.ShopOrderRefundVo;
import com.shop.vo.refund.ShopRefundItemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单明细 服务实现（只读：后台「消费者管理 → 我的订单」列表与详情）
 *
 * 明细行的身份是**具体规格**：这一行按 sku_id 挂 shop_product_sku，不再直接挂商品（那列 product_id 已删）。
 * 所以商品那一头的信息（编码、分类这些）要在这里走两跳：本行 sku_id → shop_product_sku → 它的 product_id
 * → shop_product，那条路在 XML 里联好了（见 ShopOrderItemMapper.xml），本类只读现成的行，不再按商品 id 查。
 *
 * 这一页是订单级的：列表一行一张 sys_order（selectOrderPage），详情弹窗上半是主单、
 * 下半是名下全部明细行（selectOrderById）。列表上那颗「退款单」看的是这一单名下的退货记录，
 * 一行一张退款单、展开是它退了哪几行商品（selectRefundsByOrderId）。
 *
 * 数据范围：消费者账号（account_type=3）只看/只能查自己的单，平台等其它账号看全部。
 * 门店账号按 store_id 收窄是另一条口径，这里没做。
 *
 * 写入不在这个类里：下单由小程序侧的统一下单接口落库（ShopOrderCreateServiceImpl，
 * shop-api 模块只依赖 shop-commom，看不到本模块的 service，所以那一边自己实现）。
 * 唯一的例外是明细行的 ship_status（「已出库」那一档由商品出库的动作回写），见 ShopErpSaleOutboundServiceImpl。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderItemServiceImpl extends ServiceImpl<ShopOrderItemMapper, ShopOrderItem>
        implements ShopOrderItemService {

    /** 只把 sys_refund.extra 那份行快照读回来（写进去的是统一退货接口那一边，见 shop-api 的 ShopRefundCreateServiceImpl） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 还在流程里的那两档：0 待审核 / 3 退款中（审核通过直接落这一档），它们占的件数要在明细行上说清楚 */
    private static final List<Integer> REFUND_STATUS_IN_PROGRESS = Arrays.asList(0, 3);

    private final SysRefundMapper sysRefundMapper;

    @Override
    public IPage<ShopOrderVo> selectOrderPage(ShopOrderQueryDto query) {
        if (query == null) {
            query = new ShopOrderQueryDto();
        }
        // 消费者账号只看自己的订单：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 要联子查询（行数/件数/门店名）和 EXISTS 筛选（发货状态、门店都在明细行上），走 XML；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectOrderPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopOrderVo selectOrderById(Long id) {
        ShopOrderVo order = baseMapper.selectOrderById(id);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        assertConsumerOwns(order.getUserId());
        // 明细单独查一次填进来：弹窗下半那张表要的是全部行，不分页，所以不走列表那条 SQL
        List<ShopOrderItemVo> items = baseMapper.selectOrderItemsByOrderId(id);
        fillRefundingQuantity(items, id);
        order.setItems(items);
        return order;
    }

    /**
     * 逐行填「在途退款单已经占掉的件数」。退货弹窗上的"还能退"= quantity − returned_quantity − 这一格，
     * 和建单那一路的行级闸同口径（ShopRefundCreateServiceImpl#inFlightQuantityByLine）。
     *
     * 一件货可以被两张在途退款单各退一遍吗？不能，所以别的单先占走的那几件要在这一单上说出来，
     * 不然前端给的 max 比后端肯收的大，提交才被闸拦下来 —— 那种"点开能填、点了报数量不对"最招人烦。
     * 已到账（4）的不在这儿算：那一份已经写在明细行的 returned_quantity 上了。
     *
     * @param items 这一单名下的明细行（原地改，不返回新对象）
     * @param orderId 订单 id
     */
    private void fillRefundingQuantity(List<ShopOrderItemVo> items, Long orderId) {
        if (items.isEmpty()) {
            return;
        }
        Map<Long, Integer> occupied = new HashMap<>();
        for (ShopOrderRefundVo refund : sysRefundMapper.selectRefundVoByOrderId(orderId)) {
            if (refund.getStatus() == null || !REFUND_STATUS_IN_PROGRESS.contains(refund.getStatus())) {
                continue;
            }
            for (ShopRefundItemVo extra : parseExtra(refund)) {
                if (extra.getOrderItemId() != null && extra.getQuantity() != null) {
                    occupied.merge(extra.getOrderItemId(), extra.getQuantity(), Integer::sum);
                }
            }
        }
        items.forEach(item -> item.setRefundingQuantity(occupied.getOrDefault(item.getId(), 0)));
    }

    /**
     * 一张订单名下的全部退款单（列表上那颗「退款单」弹窗）。
     *
     * 一条 SQL 把主表那几格连审核人一起捞回来，行级内容在 Java 里解析 extra，商品名称/规格
     * 按 orderItemId 从这一单的明细行现取 —— 不退化成 N+1，也不用把同一份信息存两处。
     */
    @Override
    public List<ShopOrderRefundVo> selectRefundsByOrderId(Long orderId) {
        // 归属校验走主表这一道：退款单上的 user_id 是建单时冗余的，老单可能没写，认 sys_order 更准
        ShopOrderVo order = baseMapper.selectOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        assertConsumerOwns(order.getUserId());
        List<ShopOrderRefundVo> refunds = sysRefundMapper.selectRefundVoByOrderId(orderId);
        if (refunds.isEmpty()) {
            return refunds;
        }
        Map<Long, ShopOrderItemVo> lines = baseMapper.selectOrderItemsByOrderId(orderId).stream()
                .collect(Collectors.toMap(ShopOrderItemVo::getId, Function.identity()));
        refunds.forEach(refund -> fillRefundItems(refund, lines));
        return refunds;
    }

    /**
     * 把 extra 那份申请内容换成给前端看的行，并顺手把弹窗表头要的两个合计算出来。
     *
     * @param lines 这一单名下的全部明细行，按 id 索引（extra 里那几行必然在它里面；对不上就不填名称，报 id）
     */
    private void fillRefundItems(ShopOrderRefundVo refund, Map<Long, ShopOrderItemVo> lines) {
        List<ShopOrderRefundItemVo> items = new ArrayList<>();
        for (ShopRefundItemVo extra : parseExtra(refund)) {
            ShopOrderRefundItemVo item = new ShopOrderRefundItemVo();
            item.setOrderItemId(extra.getOrderItemId());
            // extra 里那一格这轮已从 product_id 换成 sku_id（ShopRefundItemVo 上只有 skuId，
            // 明细表 shop_order_item 那一列也换成了 sku_id），所以按来源单据带出来的身份现在是**规格 id**；
            // 展示要的商品名/编码/规格文本仍然现联明细行那份下单快照（下面那段），不看这一格。
            item.setSkuId(extra.getSkuId());
            item.setQuantity(extra.getQuantity());
            item.setAmount(extra.getAmount());
            ShopOrderItemVo line = extra.getOrderItemId() == null ? null : lines.get(extra.getOrderItemId());
            if (line != null) {
                item.setProductName(line.getProductName());
                item.setProductCode(line.getProductCode());
                item.setSpecText(line.getSpecText());
                item.setImage(line.getImage());
            }
            items.add(item);
        }
        refund.setItems(items);
        refund.setItemCount(items.size());
        refund.setReturnQuantity(items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum());
    }

    /**
     * 读 sys_refund.extra 那份"退哪几行、各退几件、各退多少钱"的快照。
     *
     * 和回调那一路（ShopRefundCallbackServiceImpl#parseItems）不一样，这里解析不出来不报错：
     * 后台手工建的、这一版之前的老退款单在 extra 里存的是别的内容甚至压根没存，那种单子这一格就是空的，
     * 前端显示"没有登记退货商品行"就完了 —— 这是只读的一页，为一行历史数据把整单详情打不开不值当。
     */
    private List<ShopRefundItemVo> parseExtra(ShopOrderRefundVo refund) {
        if (refund.getExtra() == null || refund.getExtra().isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<ShopRefundItemVo> items = JSON.readValue(refund.getExtra(),
                    new TypeReference<List<ShopRefundItemVo>>() {
                    });
            return items == null ? Collections.emptyList() : items;
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 消费者账号的归属校验，口径同列表的数据范围：非消费者直接放过。
     * 列表已经收窄了，这里补的是"绕过列表直接拿别人的 id 打接口"那条路。
     *
     * @param ownerId 这张单归属的消费者 id（sys_order.user_id）
     */
    private void assertConsumerOwns(Long ownerId) {
        if (!LoginUserUtils.isConsumer()) {
            return;
        }
        if (ownerId == null || ownerId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张订单不属于你，无权查看");
        }
    }
}
