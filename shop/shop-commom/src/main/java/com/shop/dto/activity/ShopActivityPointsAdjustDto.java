package com.shop.dto.activity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员调整积分参数（后台「积分管理 → 修改」那颗）
 *
 * 只收"给谁、加还是减、加减多少分、为什么、这批分几时过期（只有增加才有这一格）"，
 * 五个账面数字（累计获得/累计消耗/可用/冻结/即将过期）
 * 一概不在参数里：那一本是账，账上的数只能由积分变动自己加加减减，
 * 表单让后台直接填五个数就等于允许"账面多了 1000 分、流水里却什么都没有"（口径同商品库存）。
 * 所以这一趟做的是：按方向走一条带余额闸门的原子 UPDATE 改账，同一个事务里补一行
 * biz_type=10 管理员调整 的流水（原因写进 description，过期时间写进 expire_time），要查谁动的、什么时候动的都翻得到。
 */
@Data
@ApiModel(value = "管理员调整积分参数")
public class ShopActivityPointsAdjustDto {

    @ApiModelProperty(value = "用户ID（sys_user.id），必填 —— 调的是哪一个人的那本账")
    private Long userId;

    @ApiModelProperty(value = "调整方向：1 增加 2 减少，必填。"
            + "增加走送分那一条（累计获得和可用一起加）；减少走可用→累计消耗（不把累计获得减回去，"
            + "不然流水里那几笔获取行就对不上账）")
    private Integer type;

    @ApiModelProperty(value = "调整积分数，必填、大于 0（方向由 type 说，这一格只说多少个）。"
            + "减少时不能大过这个人的可用积分，冻结中的那一部分（下单没付的单子占着的）扣不到")
    private Integer points;

    @ApiModelProperty(value = "调整原因，必填 —— 这一格原样写进流水的说明，是这一趟唯一的留痕处，"
            + "例如：客诉补偿 / 活动误发回收")
    private String reason;

    // 只有方向是增加时看这一格：扣掉的那一份本来就不过期（口径同流水表 —— expire_time 只在获取那一头的行上有值）。
    // 购物送分那一头的过期时间是 shop-api 按 sys_config 的 expire_day 自动算的，管理员这一格是手工给的一个日子，
    // 后台不重抄那份配置读法（两处各读一遍迟早对不上）
    @ApiModelProperty(value = "这批分的过期时间（选填，只有增加才有这一格；留空 = 这一笔不设过期）。"
            + "落在积分流水的 expire_time 上")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime expireTime;
}
