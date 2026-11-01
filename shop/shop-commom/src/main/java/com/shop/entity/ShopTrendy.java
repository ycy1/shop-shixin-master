package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 潮玩系列表
 *
 * 商品/品牌/门店三张表各挂一个可空的 trendy_id 指向这张表，NULL 就是「不是潮玩」。
 * 这张表刻意没有 user_id：它是平台级的字典，不按账号类型收窄数据范围（口径同门店招商、门店培训）。
 *
 * reward_type / reward_value 这两格是「集齐这条系列给什么奖励」的配置，写入方只有后台那一页，
 * 读的那一头是定时任务（见 shop-api 的 ShopTrendyCollectRewardService）—— 发出去的那一笔落在
 * shop_trendy_collect_record，那一行同时是"这个人这条系列领过没有"那道闸。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy")
@ApiModel(value = "潮玩系列")
public class ShopTrendy extends BaseEntity {

    /** 集齐奖励类型：1 积分（reward_value 那一格存的是积分数） */
    public static final int REWARD_POINTS = 1;

    /** 集齐奖励类型：2 优惠券（reward_value 是券模板 id） */
    public static final int REWARD_COUPON = 2;

    /** 集齐奖励类型：3 实物（reward_value 是商品 id，发出去的是这件商品的一张代金券） */
    public static final int REWARD_GOODS = 3;

    @ApiModelProperty(value = "潮玩名称，唯一")
    private String trendyName;

    // 同上，NULL 表示这个系列还没归到任何IP；绑/解都由「潮玩IP」那个面板改这一列
    @ApiModelProperty(value = "所属潮玩IP id(shop_trendy_ip.id)，NULL=未归属")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long ipId;

    // 可空列要显式 IGNORED：全局 field-strategy=not_empty 会把 null 一起从 UPDATE 的 SET 里跳过，
    // 否则前端清空封面/说明后保存不生效（同 ShopBrand.logo）
    @ApiModelProperty(value = "潮玩封面 URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String coverImage;

    @ApiModelProperty(value = "说明")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    /**
     * 集齐这条系列的奖励类型。库里 NOT NULL 默认 1，所以这一格永远有值，不用标 IGNORED；
     * 「集齐」判的是这个人名下抽到过的规格种类数够不够这条系列当前的规格总数，见 ShopTrendyCollectRecordMapper
     */
    @ApiModelProperty(value = "集齐奖励类型：1积分 2优惠券 3实物（字典 trendy_reward_type）")
    private Integer rewardType;

    /**
     * 奖励值按类型指向三种东西（1 是积分数、2 是券模板 id、3 是实物商品 id），所以这一格是字符串不是数字列：
     * 三档各自的解析和校验都在服务层，页面按类型给对应的控件填
     */
    @ApiModelProperty(value = "集齐奖励值：积分数/券模板ID/实物商品ID")
    private String rewardValue;

    /** 名下绑了多少件商品：不是本表列，服务层按当页 id 一次分组数出来回填（列表那一列和那颗按钮都读它） */
    @ApiModelProperty(value = "绑定的商品数（服务层回填，非本表列）")
    @TableField(exist = false)
    private Integer productCount;

    /**
     * 奖励值那一档的名字（券名 / 实物商品名）：不是本表列，服务层按当页 id 回填，积分那一档给的是「N 分」。
     * 列表那一列要给人看出「发的是哪张券、哪件实物」，只报一个 id 没人认得
     */
    @ApiModelProperty(value = "集齐奖励的展示文案（服务层回填，非本表列）")
    @TableField(exist = false)
    private String rewardText;

    /** 查询用一档：true 就只列还没归属任何IP的系列（「潮玩IP」那层候选读它，口径同商品查询的 noTrendy） */
    @ApiModelProperty(value = "查询：只看未归属任何IP的系列")
    @TableField(exist = false)
    private Boolean noIp;
}
