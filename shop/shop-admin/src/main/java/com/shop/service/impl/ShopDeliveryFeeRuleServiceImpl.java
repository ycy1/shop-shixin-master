package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopDeliveryFeeRule;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopDeliveryFeeRuleMapper;
import com.shop.service.ShopDeliveryFeeRuleService;
import com.shop.utils.PageUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * 配送费规则表 服务实现（写法照 shop_delivery_company：平台级配置，user_id 只留痕、不参与过滤）
 *
 * 这里只管规则这一张表的增删改查，不管钱怎么算：算钱的那一份口径在 commom 的 DeliveryFeeUtils，
 * 挑规则那一句在 ShopDeliveryFeeRuleMapper#selectTopRule。
 * 因为付钱的这一路已经挪到「统一下单」（shop-api 的 ShopOrderDeliveryServiceImpl）那边了，
 * 它只依赖 commom 的 mapper，够不着后台的 service —— 口径抄两份的话改一处忘一处，
 * 同一张单收的钱和后台看到的规则会对不上，而钱是下单那一刻跟货款一起收走的，事后只能退款。
 */
@Service
public class ShopDeliveryFeeRuleServiceImpl
        extends ServiceImpl<ShopDeliveryFeeRuleMapper, ShopDeliveryFeeRule>
        implements ShopDeliveryFeeRuleService {

    @Override
    public IPage<ShopDeliveryFeeRule> selectPage(ShopDeliveryFeeRule query) {
        if (query == null) {
            query = new ShopDeliveryFeeRule();
        }
        // 全局共享的配置，不按创建人/账号类型收窄（口径同物流公司）：user_id 只留痕，不参与过滤
        return baseMapper.selectRulePage(PageUtil.getPage(), query);
    }

    @Override
    public void insert(ShopDeliveryFeeRule rule) {
        validate(rule, null);
        normalize(rule);
        rule.setUserId(StpUtil.getLoginIdAsLong());
        save(rule);
    }

    @Override
    public void update(ShopDeliveryFeeRule rule) {
        if (rule.getId() == null) {
            throw new ServiceException("配送费规则ID不能为空");
        }
        ShopDeliveryFeeRule exists = getById(rule.getId());
        if (exists == null) {
            throw new ServiceException("配送费规则不存在");
        }
        if (StringUtils.isBlank(rule.getRuleName())) {
            rule.setRuleName(exists.getRuleName());
        }
        validate(rule, rule.getId());
        normalize(rule);
        updateById(rule);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 不拦"有没有配送单在用这条规则"：配送单上存的是算好的那个数，规则只是它当时参照的说法。
        // 历史单要是一律拦着不让删，配了半年就再也删不动任何一条。
        // 删掉之后单上的 fee_rule_id 查不到名字，配送单详情会显示"规则已删除"，那笔钱本身不受影响。
        removeByIds(ids);
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带上 NULL 而报"不能为空"。
     * 金额那几列没在库里写默认值以外的约束，所以这里一并补 0 —— 配规则的人只填自己想改的那几格。
     */
    private void normalize(ShopDeliveryFeeRule rule) {
        if (rule.getDeliveryType() == null) {
            rule.setDeliveryType(1);
        }
        if (rule.getBaseFee() == null) {
            rule.setBaseFee(BigDecimal.ZERO);
        }
        if (rule.getBaseDistance() == null) {
            rule.setBaseDistance(BigDecimal.ZERO);
        }
        if (rule.getPerKmFee() == null) {
            rule.setPerKmFee(BigDecimal.ZERO);
        }
        if (rule.getWeightFree() == null) {
            rule.setWeightFree(BigDecimal.ZERO);
        }
        if (rule.getPerKgFee() == null) {
            rule.setPerKgFee(BigDecimal.ZERO);
        }
        if (rule.getPeakFee() == null) {
            rule.setPeakFee(BigDecimal.ZERO);
        }
        if (rule.getNightFee() == null) {
            rule.setNightFee(BigDecimal.ZERO);
        }
        if (rule.getWeatherFee() == null) {
            rule.setWeatherFee(BigDecimal.ZERO);
        }
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
    }

    private void validate(ShopDeliveryFeeRule rule, Long excludeId) {
        if (StringUtils.isBlank(rule.getRuleName())) {
            throw new ServiceException("规则名称不能为空");
        }
        rule.setRuleName(rule.getRuleName().trim());
        Long nameCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryFeeRule>()
                .eq(ShopDeliveryFeeRule::getRuleName, rule.getRuleName())
                .ne(excludeId != null, ShopDeliveryFeeRule::getId, excludeId));
        if (nameCount != null && nameCount > 0) {
            throw new ServiceException("配送费规则「" + rule.getRuleName() + "」已存在");
        }
        // 钱是给人看的，负数一律拦在门口（库里这些列也没让存负数，靠代码兜住）
        requireNotNegative(rule.getBaseFee(), "起步配送费");
        requireNotNegative(rule.getBaseDistance(), "起步里程");
        requireNotNegative(rule.getPerKmFee(), "每公里加收");
        requireNotNegative(rule.getWeightFree(), "免费重量");
        requireNotNegative(rule.getPerKgFee(), "每公斤加收");
        requireNotNegative(rule.getPeakFee(), "高峰加价");
        requireNotNegative(rule.getNightFee(), "夜间加价");
        requireNotNegative(rule.getWeatherFee(), "天气加价");
        requireNotNegative(rule.getFreeDeliveryAmount(), "免配送费金额");
        requireNotNegative(rule.getFreeDeliveryDistance(), "免配送费距离");
        // 时段配半截最容易看错：只填了开始时刻的话这一档其实不生效，但界面上看着像加了钱
        requireTimePair(rule.getPeakStart(), rule.getPeakEnd(), "高峰时段");
        requireTimePair(rule.getNightStart(), rule.getNightEnd(), "夜间时段");
    }

    private void requireNotNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw new ServiceException(label + "不能是负数");
        }
    }

    private void requireTimePair(LocalTime start, LocalTime end, String label) {
        if ((start == null) != (end == null)) {
            throw new ServiceException(label + "的开始和结束时刻要一起填（只填一个的话这一档不会生效）");
        }
    }
}
