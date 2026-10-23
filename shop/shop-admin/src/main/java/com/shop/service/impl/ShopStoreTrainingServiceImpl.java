package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopStore;
import com.shop.entity.ShopStoreTraining;
import com.shop.entity.ShopStoreTrainingMember;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.ShopStoreTrainingMapper;
import com.shop.mapper.ShopStoreTrainingMemberMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopStoreTrainingService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 门店培训 服务实现（写法照门店招商 ShopStoreInvestmentServiceImpl：编码系统生成、必填校验、可空列靠 IGNORED 清空）
 *
 * 和数据范围：这一页和门店招商一样不按账号类型收窄（平台看全部）。培训一家门店一场，
 * 但「所属门店」只是这场的归属，不是这一行的可见范围 —— 门店档账号也照样能看见别人家开的培训（才报得进名）。
 *
 * 报名那一行写在 shop-api 那一侧（POST /api/comm/store/training/sign，"两端同一个动作"都走那一条），
 * 这一份只管后台的读和改单，所以「已报名人数」是查询时按报名表现算的 ——
 * 库里那一列没人回写，实体上钉了 NEVER 让它永不进 UPDATE。
 */
@Service
@RequiredArgsConstructor
public class ShopStoreTrainingServiceImpl extends ServiceImpl<ShopStoreTrainingMapper, ShopStoreTraining>
        implements ShopStoreTrainingService {

    /** 状态：0 待发布 —— 新建默认的，也是唯一还能删的那一档 */
    private static final int STATUS_PENDING = 0;
    /** 状态：1 报名中 —— 第一次进这一档时服务端盖发布时间 */
    private static final int STATUS_SIGNING = 1;
    /** 状态：4 已结束 —— 第一次进这一档时服务端盖结束时间 */
    private static final int STATUS_FINISHED = 4;

    /** 只用来校验「所属门店」那一格选中的店还在（注入 mapper 而不是 service，避免两边互相依赖） */
    private final ShopStoreMapper shopStoreMapper;

    /** 只用来校验「培训讲师」那一格选中的人还在 */
    private final SysUserMapper sysUserMapper;

    /** 后台这一侧只用它读：列表页那颗「培训人员」弹窗和「已报名人数」的现算都靠它（报名写在 shop-api） */
    private final ShopStoreTrainingMemberMapper shopStoreTrainingMemberMapper;

    @Override
    public IPage<ShopStoreTraining> selectPage(ShopStoreTraining query) {
        if (query == null) {
            query = new ShopStoreTraining();
        }
        // 关键字跨三列搜 + 培训开始时间区间，条件写 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写。
        // 多传一个登录 id 是给那条 SQL 再联一次参训人员表：列表行上要知道"我报过没有、签过没有"
        return baseMapper.selectTrainingPage(PageUtil.getPage(), query, StpUtil.getLoginIdAsLong());
    }

    @Override
    public ShopStoreTraining selectTrainingById(Long id) {
        ShopStoreTraining training = baseMapper.selectTrainingById(id);
        if (training == null) {
            throw new ServiceException("培训不存在");
        }
        return training;
    }

    @Override
    public List<ShopStoreTrainingMember> selectMembers(Long trainingId) {
        if (trainingId == null) {
            throw new ServiceException("培训ID不能为空");
        }
        // 先确认培训还在（getById 走 Wrapper，逻辑删除的行取不到）：
        // 不然弹窗拿到一张空表，分不清是"还没人报名"还是"没这条培训"
        if (getById(trainingId) == null) {
            throw new ServiceException("培训不存在");
        }
        return shopStoreTrainingMemberMapper.selectMemberByTrainingId(trainingId);
    }

    @Override
    public void insert(ShopStoreTraining training) {
        // 新增时表单那栏是禁用的、不会传编号，和招商/门店编码一样由系统生成
        if (StringUtils.isBlank(training.getTrainingNo())) {
            training.setTrainingNo(GeneratorNoUtils.getTrainingNo());
        }
        validate(training, null);
        normalize(training);
        // 新增一律落 0 待发布：发布是「修改」里把状态选成 1 那一步，所以这两个时间戳此刻都还没有
        training.setStatus(STATUS_PENDING);
        training.setPublishTime(null);
        training.setFinishTime(null);
        try {
            save(training);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_training_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("培训编号「" + training.getTrainingNo()
                    + "」已被占用，可能来自一条已删除的培训记录");
        }
    }

    @Override
    public void update(ShopStoreTraining training) {
        if (training.getId() == null) {
            throw new ServiceException("培训ID不能为空");
        }
        ShopStoreTraining exists = getById(training.getId());
        if (exists == null) {
            throw new ServiceException("培训不存在");
        }
        // 编号允许不传（表单里那格是禁用的），不传时沿用原值参与查重（not_empty 策略下不传本来也不会写这一列）
        if (StringUtils.isBlank(training.getTrainingNo())) {
            training.setTrainingNo(exists.getTrainingNo());
        }
        validate(training, training.getId());
        normalize(training);
        fillStatusTime(training, exists);
        try {
            updateById(training);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("培训编号「" + training.getTrainingNo()
                    + "」已被占用，可能来自一条已删除的培训记录");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 只删 0 待发布：一旦发布就可能有人报过名，参训记录还指着这张单（判据同"入过货的单子删不掉"）。
        // 判的是库里那行的状态，不是请求体
        List<ShopStoreTraining> docs = listByIds(ids);
        List<String> published = docs.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() != STATUS_PENDING)
                .map(ShopStoreTraining::getTrainingNo)
                .collect(Collectors.toList());
        if (!published.isEmpty()) {
            throw new ServiceException("只有待发布的培训能删，这几条已经发布过了：" + String.join("、", published));
        }
        removeByIds(ids);
    }

    /**
     * 发布/结束那两个时间戳按 DDL 的注释来：状态第一次流转到 1 报名中 / 4 已结束时服务端盖一次，之后不再覆盖。
     * 表单里根本没这两格，但「修改」是整行提交、会把库里的旧值原样带回请求体，
     * 所以这里一律不认请求体，只按查出来的那一行往下走：
     * 沿用旧值（不是 null 就会被写回同一值），只有"从没进过这一档"的才当场盖时间。
     */
    private void fillStatusTime(ShopStoreTraining training, ShopStoreTraining exists) {
        training.setPublishTime(exists.getPublishTime());
        training.setFinishTime(exists.getFinishTime());
        if (training.getStatus() == null) {
            return;
        }
        if (training.getStatus() == STATUS_SIGNING && exists.getPublishTime() == null) {
            training.setPublishTime(LocalDateTime.now());
        }
        if (training.getStatus() == STATUS_FINISHED && exists.getFinishTime() == null) {
            training.setFinishTime(LocalDateTime.now());
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报"不能为空"。
     * sign_count 也是 NOT NULL，新增补 0（读的时候被现算覆盖，这一格只是让插入过得去）
     */
    private void normalize(ShopStoreTraining training) {
        if (training.getTrainingType() == null) {
            training.setTrainingType(1);
        }
        if (training.getTrainMode() == null) {
            training.setTrainMode(1);
        }
        if (training.getSignCount() == null) {
            training.setSignCount(0);
        }
    }

    private void validate(ShopStoreTraining training, Long excludeId) {
        if (StringUtils.isBlank(training.getTrainingNo())) {
            throw new ServiceException("培训编号不能为空");
        }
        if (StringUtils.isBlank(training.getTrainingName())) {
            throw new ServiceException("培训名称不能为空");
        }
        training.setTrainingNo(training.getTrainingNo().trim());
        training.setTrainingName(training.getTrainingName().trim());
        // 所属门店必填（2026-09-24 他把「总部统一培训」那一档撤了）：列在库里还允许 NULL（历史行留着），
        // 和采购单一样由服务层挡必填。报名那一路整个判据都挂在"这一场是谁家的培训"上，这一格空着就等于没人能报
        if (training.getStoreId() == null) {
            throw new ServiceException("所属门店不能为空");
        }
        if (shopStoreMapper.selectById(training.getStoreId()) == null) {
            throw new ServiceException("所选所属门店不存在");
        }
        // 讲师同样可空（还没定人），填了就要真有其人，口径同招商的负责专员
        if (training.getUserId() != null && sysUserMapper.selectById(training.getUserId()) == null) {
            throw new ServiceException("所选培训讲师不存在");
        }
        // 两组时间各判一次先后：都只在两头填齐时才判，漏一头是"还没定"，不算错
        if (training.getStartTime() != null && training.getEndTime() != null
                && training.getEndTime().isBefore(training.getStartTime())) {
            throw new ServiceException("培训结束时间不能早于开始时间");
        }
        if (training.getSignStart() != null && training.getSignEnd() != null
                && training.getSignEnd().isBefore(training.getSignStart())) {
            throw new ServiceException("报名截止时间不能早于报名开始时间");
        }
        // 表上只有 uk_training_no 一个唯一键，培训名称故意不查重（同名培训开多期是常态，同供应商名称不查重）
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopStoreTraining>()
                .eq(ShopStoreTraining::getTrainingNo, training.getTrainingNo())
                .ne(excludeId != null, ShopStoreTraining::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("培训编号「" + training.getTrainingNo() + "」已存在");
        }
    }
}
