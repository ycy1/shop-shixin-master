package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopErpSaleItem;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.service.ShopErpSaleOutboundService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品出库（进销存管理 -> 库存管理 -> 商品出库）
 *
 * add 管 来源=2 销售出库 / 4 调拨出库 / 5 报损出库 / 6 其他 四档（这一页那颗「新建」，默认 2；
 * 2 按对外销售单建一张待出库的单子，那三路没有来源单据、商品逐行手挑）；
 * 另外两条来源各有自己的建单入口，都不从这里进：1 线上购买 由支付回调按订单明细自动生成，
 * 3 采购退货 由「采购单」页那颗「采购退货」生成。所以这里没有 update：行由来源单据决定，
 * 建错了就删掉重建（出过货的单子删不掉）。
 * 另有「出库」和「生成配送单」（弹框里点「确认」才派单）两个动作
 *（后者把这一单绑着的配送单从 0 待派单 推成 1 待接单，配送单本身不由这里建）。
 * 还有一个 bySale 读接口：「销售单」页那颗「出库单」弹窗要看这张单出到哪一步了，
 * 一次把名下的出库单连各自的明细给全（形状照 /erp/purchaseIn/byPurchase）。
 */
@Api(tags = "商品出库管理")
@RestController
@RequestMapping("/erp/saleOutbound")
@RequiredArgsConstructor
public class ShopErpSaleOutboundController {

    private final ShopErpSaleOutboundService shopErpSaleOutboundService;

    @GetMapping("/list")
    @ApiOperation(value = "获取出库单列表（不带明细）")
    public Result<IPage<ShopErpSaleOutbound>> list(ShopErpSaleOutbound saleOutbound) {
        return Result.success(shopErpSaleOutboundService.selectPage(saleOutbound));
    }

    @GetMapping("/saleItems/{saleId}")
    @ApiOperation(value = "「新建出库单」选定销售单后带出的明细（每行带可出库数量 = 销售量 − 这张销售单名下已登记的量）")
    public Result<List<ShopErpSaleItem>> saleItems(@PathVariable("saleId") Long saleId) {
        return Result.success(shopErpSaleOutboundService.selectSaleItems(saleId, null));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取出库单详情（含明细，明细带该仓可用库存当本次出库数的上限提示）")
    public Result<ShopErpSaleOutbound> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopErpSaleOutboundService.selectSaleOutboundById(id));
    }

    @GetMapping("/bySale/{saleId}")
    @ApiOperation(value = "获取一张销售单名下的出库单（每张连明细一起返回，给「销售单」页那颗「出库单」弹窗用）")
    public Result<List<ShopErpSaleOutbound>> bySale(@PathVariable("saleId") Long saleId) {
        return Result.success(shopErpSaleOutboundService.selectSaleOutboundBySale(saleId));
    }

    @PostMapping("/add")
    @ApiOperation(value = "新建出库单：来源认 2 销售出库（按销售单建，同一商品超出销售量的会被挡下来）"
            + "和 4 调拨出库 / 5 报损出库 / 6 其他（手工加行，没有来源单据）四档，默认 2；"
            + "建出来是 0 待出库，货要等点「出库」才真离仓，单号/总数/总金额/状态一律服务端算")
    @OperationLogger("新增出库单")
    @SaCheckPermission("erp:saleOutbound:add")
    public Result<Long> add(@RequestBody ShopErpSaleOutbound saleOutbound) {
        return Result.success(shopErpSaleOutboundService.insert(saleOutbound));
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除出库单（明细跟着一起删；来源=1 线上购买那一路一律不给删，其它来源出过货的也不给删）")
    @OperationLogger("删除出库单")
    @SaCheckPermission("erp:saleOutbound:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopErpSaleOutboundService.deleteByIds(ids);
        return Result.success();
    }

    @PutMapping("/outbound")
    @ApiOperation(value = "执行出库（按明细的本次出库数扣库存、归还下单锁定的可用量，回写这单的状态，并把出满的那几行订单明细标成已出库）")
    @OperationLogger("出库单确认出库")
    @SaCheckPermission("erp:saleOutbound:outbound")
    public Result<Void> outbound(@RequestBody ShopErpSaleOutbound saleOutbound) {
        shopErpSaleOutboundService.outbound(saleOutbound);
        return Result.success();
    }

    @PutMapping("/confirmDelivery/{id}")
    @ApiOperation(value = "「确认」：把这张出库单绑着的那条配送单从 0 待派单 推成 1 待接单，推进送货员的抢单池"
            + "（配送单是统一下单时建的、支付回调换绑到这张单上；没出满、没绑上配送单、已经确认过都会报原因）")
    @OperationLogger("出库单确认配送")
    @SaCheckPermission("erp:saleOutbound:delivery")
    public Result<Void> confirmDelivery(@PathVariable("id") Long id) {
        shopErpSaleOutboundService.confirmDelivery(id);
        return Result.success();
    }
}

