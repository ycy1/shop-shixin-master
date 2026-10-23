package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分账户 Mapper接口
 *
 * 读的是积分管理那一页的列表（只有下面那一条自定义 SQL）。
 * 写的一律是条件增量 UPDATE，不走 updateById：这一本的五个数要被"占"（下单冻、支付扣、退货/取消还），
 * 读-改-写之间正好有缝，两个并发动作会互相覆盖；把判据压在 WHERE 里，数据库只会放行其中一个，
 * 另一个返回 0 行由调用方报错回滚（口径同优惠券占发行额度、库存 lockStock 那两条）。
 * 每一笔账变动都必须和 shop_activity_points_log 那一条流水在同一个事务里写完，只改账不记流水这本账就对不上了。
 */
@Mapper
public interface ShopActivityPointsMapper extends BaseMapper<ShopActivityPoints> {

    /**
     * 分页查积分账户（联 sys_user 带出昵称和手机号，可按关键字筛，可用积分多的排前面）
     */
    IPage<ShopActivityPoints> selectPointsPage(IPage<ShopActivityPoints> page,
                                               @Param("q") ShopActivityPoints query);

    /**
     * 下单冻结：可用积分减掉这一笔、冻结积分加上同一笔（累计获得和累计消耗都不动）。
     *
     * WHERE 压着 balance >= 这一笔，就是"一个人同时下两单、分数只够一单"那道闸：
     * 抢不到的那一趟返回 0 行，由调用方报错让整单回滚 —— 绝不能把 balance 减成负数，
     * 恒等式 balance = total − used − frozen 一破，这一页上那五个数就都没法信了。
     *
     * @return 影响行数，0 = 账户上没这么多可用积分（或者压根没有这一行账）
     */
    int freezePoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 解冻：{@link #freezePoints} 的原路退回（冻结减、可用加），未支付的单被取消时走这一条。
     *
     * WHERE 压着 frozen >= 这一笔：还的比冻的多说明这一笔已经被别的动作扣走了，返回 0 行由调用方报错。
     */
    int unfreezePoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 结转：冻结的积分真扣走（冻结减、累计消耗加），支付成功那一刻走这一条。
     *
     * 可用积分一分不动：下单冻结那一步已经把这一笔从 balance 里减掉了，
     * 这一条只是把"暂时占着"改成"已经花掉"，所以恒等式两头一起减、balance 不变。
     * 也因此这一拍不写流水（流水记的是余额变动，余额没变）。
     */
    int consumeFrozenPoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 退回抵扣：累计消耗减、可用积分加（钱退回来的同时，当初抵钱的分离开还给买家）。
     *
     * 只有退款那一路走这一条，退的是按件数摊出来的那一部分，所以一笔单可能分几次退。
     * WHERE 压着 used >= 这一笔：退的比花的多就是数据不对，返回 0 行由调用方报错回滚。
     */
    int giveBackUsedPoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 送分：累计获得加、可用积分加（购物送分那一路，正数）。
     *
     * 没有闸门：加钱不需要判余额。账户行不存在时返回 0 行，由调用方建出第一行账再加
     *（这张表的行由第一次积分变动带出来，后台不建账）。
     */
    int gainPoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 管理员扣减：可用积分减、累计消耗加（后台「积分管理 → 修改」那颗走这一条）。
     *
     * 为什么把扣掉的那一份记进 used_points 而不是把 total_points 减回去：
     * 恒等式 balance = total − used − frozen 得守住，而"减 total"等于宣称这些分从来没给过，
     * 流水里那几笔获取行还在、账面却没有了，对不上；记成消耗至少和"这一笔已经花掉"是同一个说法，
     * 流水那一行会写明是管理员扣的。
     *
     * WHERE 压 balance >= 这一笔：扣成负数就是超扣（正好和这个人同时下单冻结那一笔抢），
     * 返回 0 行由调用方报错。
     */
    int deductBalancePoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 收回送分：累计获得减、可用积分减（{@link #gainPoints} 的原路退回，退货时把当初送给他的那一份收回去）。
     *
     * 这一条和 deductBalancePoints 相反，动的不是 used 而是 total：这一笔分是当初"买这件货"送给他的，
     * 货退回去了、这份赠送也就没发生过，累计得跟着缩（记进 used 反而要说成"他花掉了"，那不对）。
     *
     * **收不满就收到可用为 0**：减的那一份是 LEAST(balance, 这一笔)。他大概率已经把送的分花掉一部分
     * 甚至拿去抵了别的单，这时候退货该退的钱照退、能收多少分收多少 —— 绝不能为了收分把整笔退款卡住，
     * 也绝不能把 balance 扣成负数（恒等式一破，这一页的五个数就都不能信了）。
     * total 和 balance 减的是同一个 LEAST 值，所以恒等式两头一起缩、仍然守得住。
     *
     * 所以这里没有 >= 那道闸门，返回 0 行只可能是账户行没了/被删了（可用为 0 时那条谓词也一样止步），
     * 由调用方重读账户算出真收到多少，收到 0 就不记流水。
     */
    int reclaimGainPoints(@Param("userId") Long userId, @Param("points") Integer points);
}
