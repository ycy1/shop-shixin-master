package com.shop.api.service;

import com.shop.dto.order.ShopOrderCreateDto;
import com.shop.vo.order.ShopOrderCreateVo;
import com.shop.vo.order.ShopOrderPreviewVo;

/**
 * 统一下单 服务接口（小程序侧，下单前调用）
 */
public interface ShopOrderCreateService {

    /**
     * 按购买的商品明细落一张订单 + 对应明细行：订单状态 0 待支付、明细发货状态 0 待发货，不动支付。
     *
     * 每一行明细会按收货地就近分到一家有可用库存的启用仓库（落 shop_order_item.warehouse_id）：
     * 坐标优先取参数里 addressId 那条收货地址，没传才用 sys_user 上的经纬度；
     * 那条地址只选了地区没录坐标时不比距离，改拿它的省/市/区匹同地区的启用仓库（区 > 市 > 省）。
     * 锁定之前会再量一次该仓可用库存，不够就报"库存不足"给买家；
     * 随后把 shop_erp_inventory 那行的 locked_stock 加上本行数量 —— 总库存不动，只是这批货不再卖给别人。
     * 挑不出仓或锁不到货会抛异常，整单（含主单和已锁的行）一起回滚。
     *
     * 请求体里每一行还能带一个 usedPoints（这一行花几分）：能抵多少钱由服务端按 sys_config 的汇率折，
     * 每一行抵不出超过它券后剩下的钱、各行加起来不能超账户上的可用积分，超了当场拒单。
     * 下单这一刻只把这一笔分**冻住**（可用 → 冻结，记一笔「8 抵扣」流水），真扣走等支付回调
     *（ShopOrderPointsService#settle：结转 + 按商品 gain_points 送分），买家没付就取消则整笔解冻。
     *
     * @param form 下单参数（下单人 + 收货地址 + 明细行 + 订单类型 + 买家留言）
     * @return 订单号与各项金额，供前端接着去支付
     */
    ShopOrderCreateVo create(ShopOrderCreateDto form);

    /**
     * 下单试算：结算页在买家点提交之前问一遍"这一单多少钱、每张券抵了多少、用了多少分、运费多少"，
     * **入参和 {@link #create} 一模一样**（同一个人、同一个收货地址、同一批行、同一串 couponIds、每行几分）。
     *
     * 和下单的区别只在落不落库：这一路一行数据都不写、不锁库存、不核销券、也不动积分账户，
     * 但钱的那一段和 create 走的是同一段代码（ShopOrderCreateServiceImpl#quote）——
     * 单价、行小计、就近选出的仓、每张券的范围与门槛判断、摊到行上的优惠、每行的分折成多少钱、
     * 按规则算的配送费、免邮券抵运费，全都一样。所以试算报多少，提交就收多少，不会因为另算一遍出现第二种说法。
     *
     * 也正因为共用一段判断，create 会拒的单这里一样拒（同样的报错文案）：库存不够、
     * 券不是这个人的/用过/过期了/够不上门槛/范围内没商品、积分超了这一行剩下的钱或超了账户余额，
     * 都是直接抛 ServiceException，而不是回一个"算不下去"的结果 —— 买家在结算页就该看见这句话，而不是提交时才弹出来。
     *
     * 一处已知的差别：试算不锁库存，所以同一个商品拆成几行时，后一行看到的可用库存是前一行
     * **还没占**的那一份（create 在同一事务里逐行锁，后一行看得到前一行的量）。
     * 真提交时被锁拦下来会报"库存不足"，重新一试算就是准的。
     *
     * @param form 下单参数（同 create：下单人 + 收货地址 + 明细行 + couponIds）
     * @return 各项金额 + 逐张券抵扣 + 逐行优惠
     */
    ShopOrderPreviewVo preview(ShopOrderCreateDto form);
}
