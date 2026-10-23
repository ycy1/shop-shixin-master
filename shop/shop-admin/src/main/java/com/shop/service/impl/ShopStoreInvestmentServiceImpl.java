package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopStore;
import com.shop.entity.ShopStoreInvestment;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopStoreInvestmentMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopStoreInvestmentService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门店招商 服务实现（写法照供应商 ShopErpSupplierServiceImpl：编码系统生成、必填校验、可空列靠 IGNORED 清空）
 *
 * 数据范围和供应商不同：那张表的 user_id 是"认领账号"，供应商账号靠它只看自己那条；
 * 这里的 user_id 只是"这单归哪个招商专员跟"，专员本身是平台账号(account_type=0)，
 * 平台档按项目惯例看全部，所以列表/详情/改/删都不按它收窄（2026-09-24 他定的口径）。
 */
@Service
@RequiredArgsConstructor
public class ShopStoreInvestmentServiceImpl extends ServiceImpl<ShopStoreInvestmentMapper, ShopStoreInvestment>
        implements ShopStoreInvestmentService {

    /** 状态：0 待审核 —— 新建默认的，也是唯一还能删的那一档 */
    private static final int STATUS_PENDING = 0;

    /** 只用来按 user_id 回填专员昵称；注入 mapper 而不是 service，避免两边互相依赖 */
    private final SysUserMapper sysUserMapper;

    /** 只用来按门店反查它来源那条线索（关联记在 shop_store.investment_id 上） */
    private final ShopStoreMapper shopStoreMapper;

    @Override
    public IPage<ShopStoreInvestment> selectPage(ShopStoreInvestment query) {
        if (query == null) {
            query = new ShopStoreInvestment();
        }
        // 关键字跨三列搜 + 签约时间区间，写条件用 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        IPage<ShopStoreInvestment> result = baseMapper.selectInvestmentPage(PageUtil.getPage(), query);
        fillUserName(result.getRecords());
        fillStoreLink(result.getRecords());
        return result;
    }

    @Override
    public ShopStoreInvestment selectInvestmentById(Long id) {
        ShopStoreInvestment investment = getById(id);
        if (investment == null) {
            throw new ServiceException("招商记录不存在");
        }
        // 表单里那个「负责专员」选择器要显示人名，所以昵称按 user_id 现查（不是本表列）
        fillUserName(Collections.singletonList(investment));
        return investment;
    }

    @Override
    public ShopStoreInvestment selectByStoreId(Long storeId) {
        ShopStore store = shopStoreMapper.selectById(storeId);
        if (store == null) {
            throw new ServiceException("门店不存在");
        }
        // 关联记在门店那一侧（建店时写入、之后改不动），所以这里是一反查、不联表。
        // 这家店不是招商来的就返回 null，让前端弹框显示空态，别抛错——按钮点得开就该有回应
        if (store.getInvestmentId() == null) {
            return null;
        }
        // 走 selectInvestmentById 而不是 getById：专员昵称的回填要跟着走，两个入口口径才对得上
        return selectInvestmentById(store.getInvestmentId());
    }

    /**
     * 门店表单里那格「招商信息」的候选：一家门店挂一条线索、一条线索也只能建一家门店，
     * 所以已经被别的门店占用的那些不能出现在下拉里（免得选了保存才被服务层拦回来）。
     * 占用判据沿用 fillStoreLink 那一条（看门店行的 investment_no 有没有值，不是按 investment_id 匹配）；
     * 传 storeId 时把本店自己占的那条留着，不然修改弹窗打开时这一格是空的。
     * 走 Wrapper 而不是自定义 SQL：全局逻辑删除生效，被删掉的门店会让那条线索自己回到候选里。
     */
    @Override
    public List<ShopStoreInvestment> listOptions(Long storeId) {
        // isNotNull 不能省：只 select 一列时，值为 NULL 的行在结果里会变成一个 null 元素，下面的 getInvestmentNo 就 NPE
        List<ShopStore> boundStores = shopStoreMapper.selectList(new LambdaQueryWrapper<ShopStore>()
                .select(ShopStore::getInvestmentNo)
                .isNotNull(ShopStore::getInvestmentNo)
                .ne(storeId != null, ShopStore::getId, storeId));
        Set<String> boundNos = new HashSet<>();
        for (ShopStore store : boundStores) {
            boundNos.add(store.getInvestmentNo());
        }
        // 只放行 0 待审核 / 1 已签约：2 已取消、3 已失效的线索已经黄了，不该再被门店绑上。
        // 判据和招商页那颗「创建门店」一致（那颗按钮同样只在这两档出现）。
        // 副作用：一家店已经绑着的线索后来被改成这两档，修改弹窗里这一格会走前端兜底（只显示编号、没有申请人），
        // 但 form.investmentId 没动，保存还是原样绑着，不会被悄悄解开。
        return list(new LambdaQueryWrapper<ShopStoreInvestment>()
                .select(ShopStoreInvestment::getId, ShopStoreInvestment::getInvestmentNo,
                        ShopStoreInvestment::getApplicantName, ShopStoreInvestment::getStatus)
                .in(ShopStoreInvestment::getStatus, 0, 1)
                .notIn(!boundNos.isEmpty(), ShopStoreInvestment::getInvestmentNo, boundNos)
                .orderByDesc(ShopStoreInvestment::getId));
    }

    @Override
    public void insert(ShopStoreInvestment investment) {
        // 新增时表单那栏是禁用的、不会传编号，和门店/供应商编码一样由系统生成
        if (StringUtils.isBlank(investment.getInvestmentNo())) {
            investment.setInvestmentNo(GeneratorNoUtils.getInvestmentNo());
        }
        validate(investment, null);
        normalize(investment);
        // 负责专员没指定就记登记人自己（线索多半就是经办人在跟），口径同供应商的绑定账号
        if (investment.getUserId() == null) {
            investment.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(investment);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_investment_no，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("招商编号「" + investment.getInvestmentNo()
                    + "」已被占用，可能来自一条已删除的招商记录");
        }
    }

    @Override
    public void update(ShopStoreInvestment investment) {
        if (investment.getId() == null) {
            throw new ServiceException("招商ID不能为空");
        }
        ShopStoreInvestment exists = getById(investment.getId());
        if (exists == null) {
            throw new ServiceException("招商记录不存在");
        }
        // 编号允许不传（表单里那格是禁用的），不传时沿用原值参与查重（not_empty 策略下不传本来也不会写这一列）
        if (StringUtils.isBlank(investment.getInvestmentNo())) {
            investment.setInvestmentNo(exists.getInvestmentNo());
        }
        validate(investment, investment.getId());
        normalize(investment);
        try {
            updateById(investment);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("招商编号「" + investment.getInvestmentNo()
                    + "」已被占用，可能来自一条已删除的招商记录");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 只删 0 待审核：出过结果（签约/取消/失效）的都是留档凭证，口径同入库单"入过货的单子删不掉"。
        // 判的是库里那行的状态，不是请求体
        List<ShopStoreInvestment> docs = listByIds(ids);
        List<String> notPending = docs.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() != STATUS_PENDING)
                .map(ShopStoreInvestment::getInvestmentNo)
                .collect(Collectors.toList());
        if (!notPending.isEmpty()) {
            throw new ServiceException("只有待审核的申请能删，这几条已经出过结果：" + String.join("、", notPending));
        }
        removeByIds(ids);
    }

    /**
     * 按 user_id 回填专员昵称。
     * 没有在 XML 里联 sys_user：详情走 BaseMapper 的 getById，两条路共用一个回填才不会口径不一致，
     * 而且一行的 user_id 都取自当页出现过的 id，一次 selectBatchIds 就够（不超过页大小）。
     * SysUser.id 是 Integer，这里统一按 Long 建映射，和 shop_store_investment.user_id 对齐。
     */
    private void fillUserName(List<ShopStoreInvestment> investments) {
        Set<Long> userIds = new HashSet<>();
        for (ShopStoreInvestment investment : investments) {
            if (investment.getUserId() != null) {
                userIds.add(investment.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
            nameById.put(user.getId().longValue(),
                    StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
        }
        for (ShopStoreInvestment investment : investments) {
            investment.setUserName(nameById.get(investment.getUserId()));
        }
    }

    /**
     * 回填"这条线索已经建出哪家门店"，给列表页决定还要不要让点「创建门店」。
     * 判据是门店表那格 investment_no 有没有值（2026-09-24 他定的口径，不是按 investment_id 匹配）：
     * 招商编号在本表是 uk_investment_no 唯一的，门店那一侧它是建店时按 id 查库里带的快照，
     * 而编号在修改弹窗里不给改，所以两边不会漂；比较的是列和绑定参数，不涉及跨表列=列的排序规则问题。
     * 一次 in 把当页线索对应的门店都查回来，不逐行反查（一页十行就是十次查询）；
     * 走 Wrapper 而不是自定义 SQL，全局逻辑删除因此生效：建出来的门店被删掉后这颗按钮会自己回来。
     * 一条线索万一对应多家店就取 id 最小的那家（判"有没有"够用）。
     */
    private void fillStoreLink(List<ShopStoreInvestment> investments) {
        Set<String> investmentNos = new HashSet<>();
        for (ShopStoreInvestment investment : investments) {
            if (StringUtils.isNotBlank(investment.getInvestmentNo())) {
                investmentNos.add(investment.getInvestmentNo());
            }
        }
        if (investmentNos.isEmpty()) {
            return;
        }
        Map<String, Long> storeIdByNo = new HashMap<>();
        List<ShopStore> stores = shopStoreMapper.selectList(new LambdaQueryWrapper<ShopStore>()
                .select(ShopStore::getId, ShopStore::getInvestmentNo)
                .in(ShopStore::getInvestmentNo, investmentNos)
                .orderByAsc(ShopStore::getId));
        for (ShopStore store : stores) {
            storeIdByNo.putIfAbsent(store.getInvestmentNo(), store.getId());
        }
        for (ShopStoreInvestment investment : investments) {
            investment.setStoreId(storeIdByNo.get(investment.getInvestmentNo()));
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报"不能为空"
     */
    private void normalize(ShopStoreInvestment investment) {
        if (investment.getIntentType() == null) {
            investment.setIntentType(1);
        }
        if (investment.getHasExperience() == null) {
            investment.setHasExperience(0);
        }
        if (investment.getSource() == null) {
            investment.setSource(1);
        }
        if (investment.getStatus() == null) {
            investment.setStatus(STATUS_PENDING);
        }
    }

    private void validate(ShopStoreInvestment investment, Long excludeId) {
        if (StringUtils.isBlank(investment.getInvestmentNo())) {
            throw new ServiceException("招商编号不能为空");
        }
        if (StringUtils.isBlank(investment.getApplicantName())) {
            throw new ServiceException("申请人姓名不能为空");
        }
        if (StringUtils.isBlank(investment.getApplicantPhone())) {
            throw new ServiceException("申请人电话不能为空");
        }
        // 两头时间都必填：库里这两列是可空的（他没让改 DDL），所以判据落在服务层，口径同仓库的经纬度必填
        if (investment.getSignTime() == null) {
            throw new ServiceException("签约时间不能为空");
        }
        if (investment.getExpiryTime() == null) {
            throw new ServiceException("过期时间不能为空");
        }
        investment.setInvestmentNo(investment.getInvestmentNo().trim());
        investment.setApplicantName(investment.getApplicantName().trim());
        investment.setApplicantPhone(investment.getApplicantPhone().trim());
        // 负责专员可以留空（还没分派出去的线索），但填了就必须真有其人，口径同供应商的绑定账号
        if (investment.getUserId() != null && sysUserMapper.selectById(investment.getUserId()) == null) {
            throw new ServiceException("所选负责专员不存在");
        }
        // 表上只有 uk_investment_no 一个唯一键，申请人姓名/电话都不查重（同一人反复来谈是常态）
        Long noCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopStoreInvestment>()
                .eq(ShopStoreInvestment::getInvestmentNo, investment.getInvestmentNo())
                .ne(excludeId != null, ShopStoreInvestment::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new ServiceException("招商编号「" + investment.getInvestmentNo() + "」已存在");
        }
    }
}
