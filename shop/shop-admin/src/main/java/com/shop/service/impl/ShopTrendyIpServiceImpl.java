package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopTrendy;
import com.shop.entity.ShopTrendyIp;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopTrendyIpMapper;
import com.shop.mapper.ShopTrendyMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopTrendyIpService;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopTrendyIpServiceImpl extends ServiceImpl<ShopTrendyIpMapper, ShopTrendyIp> implements ShopTrendyIpService {

    /** 授权类型的四档（字典 license_type），超出这个范围的值页面渲染不出标签，落库前就拦掉 */
    private static final int LICENSE_TYPE_MIN = 1;
    private static final int LICENSE_TYPE_MAX = 4;

    /** 只用来看名下绑了哪些系列（回填系列数、删除拦截、绑定判存在）；注入 mapper 而不是 service，避免两边互相依赖 */
    private final ShopTrendyMapper trendyMapper;

    /** 只用来看版权人/授权人存不存在，和回填列表那一格名字（口径同盲盒那侧） */
    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopTrendyIp> selectPage(ShopTrendyIp query) {
        if (query == null) {
            query = new ShopTrendyIp();
        }
        IPage<ShopTrendyIp> page = page(PageUtil.getPage(), new LambdaQueryWrapper<ShopTrendyIp>()
                .like(StringUtils.isNotBlank(query.getIpName()), ShopTrendyIp::getIpName, query.getIpName())
                .eq(query.getStatus() != null, ShopTrendyIp::getStatus, query.getStatus())
                .eq(query.getLicenseType() != null, ShopTrendyIp::getLicenseType, query.getLicenseType())
                .eq(query.getIsHot() != null, ShopTrendyIp::getIsHot, query.getIsHot())
                // 启用的打头（status：1启用 0禁用），同一状态内按运营排的 sort、再按录入倒序（口径同盲盒列表）
                .orderByDesc(ShopTrendyIp::getStatus)
                .orderByAsc(ShopTrendyIp::getSort)
                .orderByDesc(ShopTrendyIp::getId));
        fillTrendyCount(page.getRecords());
        fillUserNames(page.getRecords());
        return page;
    }

    @Override
    public List<ShopTrendyIp> listOptions() {
        // 只给启用中的：停用的IP不该在系列表单里被新选中（口径同盲盒表单那份潮玩系列候选 ——
        // 真撞上「已绑着一个后来被停用的IP」，那一格下拉会显示 id 而不是名字，和这项目其它几处一样）
        return list(new LambdaQueryWrapper<ShopTrendyIp>()
                .eq(ShopTrendyIp::getStatus, 1)
                .orderByAsc(ShopTrendyIp::getSort)
                .orderByAsc(ShopTrendyIp::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTrendies(List<Long> trendyIds, Long ipId) {
        if (trendyIds == null || trendyIds.isEmpty()) {
            throw new ServiceException("请选择要操作的潮玩系列");
        }
        // ipId 传 null 就是解绑（这一格本来就是可空的）
        if (ipId != null && getById(ipId) == null) {
            throw new ServiceException("潮玩IP不存在");
        }
        // 一次捞回来对个数：库里有已经被别人删掉的 id 就当场说，别闷头更新 0 行让人以为绑上了
        List<ShopTrendy> found = trendyMapper.selectBatchIds(trendyIds);
        if (found.size() != trendyIds.size()) {
            throw new ServiceException("有潮玩系列已不存在，请刷新后重试");
        }
        // 一条 UPDATE 收尾（公网库一趟往返不便宜，别一行发一趟）。
        // 也不能走 updateById(空实体)：那会把实体里其它 IGNORED 列（封面/说明…）一起写成 null。
        // 留痕两格按 MyMetaObjectHandler 的口径手工补上。
        trendyMapper.update(null, new LambdaUpdateWrapper<ShopTrendy>()
                .set(ShopTrendy::getIpId, ipId)
                .set(ShopTrendy::getUpdateBy, StpUtil.getLoginIdAsLong())
                .set(ShopTrendy::getUpdateTime, LocalDateTime.now())
                .in(ShopTrendy::getId, trendyIds));
    }

    /**
     * 回填「名下有几个系列」：按当页这批 id 一次分组数完，别一行发一趟查询。
     * selectMaps 走 BaseMapper，逻辑删除条件自动带上 ⇒ 已删除的系列不计数（口径同删除拦截那一趟）。
     */
    private void fillTrendyCount(List<ShopTrendyIp> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(rows.size());
        rows.forEach(row -> ids.add(row.getId()));
        Map<Long, Integer> counted = new HashMap<>();
        for (Map<String, Object> one : trendyMapper.selectMaps(new QueryWrapper<ShopTrendy>()
                .select("ip_id AS ipId", "COUNT(1) AS cnt")
                .in("ip_id", ids)
                .groupBy("ip_id"))) {
            Object ipId = one.get("ipId");
            Object cnt = one.get("cnt");
            if (ipId != null && cnt != null) {
                counted.put(((Number) ipId).longValue(), ((Number) cnt).intValue());
            }
        }
        rows.forEach(row -> row.setTrendyCount(counted.getOrDefault(row.getId(), 0)));
    }

    /**
     * 回填「版权人/授权人」那一格的名字：按当页出现过的 user_id 一次查回来，不联表。
     * sys_user.id 是 Integer，按 id 批量查就要用 Integer 集合（口径同 ShopStoreServiceImpl#fillManagerName）。
     */
    private void fillUserNames(List<ShopTrendyIp> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Integer> userIds = new HashSet<>();
        for (ShopTrendyIp row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId().intValue());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> userNameById = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
            userNameById.put(user.getId().longValue(),
                    StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
        }
        rows.forEach(row -> row.setUserName(userNameById.get(row.getUserId())));
    }

    @Override
    public void insert(ShopTrendyIp ip) {
        validate(ip, null);
        applyDefaults(ip);
        try {
            save(ip);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_ip_name，前置查重（只查未删除的）看不到它
            throw new ServiceException("IP「" + ip.getIpName() + "」已被占用，可能来自一个已删除的IP");
        }
    }

    @Override
    public void update(ShopTrendyIp ip) {
        if (ip.getId() == null) {
            throw new ServiceException("潮玩IP id不能为空");
        }
        if (getById(ip.getId()) == null) {
            throw new ServiceException("潮玩IP不存在");
        }
        validate(ip, ip.getId());
        try {
            updateById(ip);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("IP「" + ip.getIpName() + "」已被占用，可能来自一个已删除的IP");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 系列是挂在IP下面的，直接删IP会让那一堆系列变成「归属一个没了的IP」，所以先拦一句让人去解绑
        // （口径同 ShopTrendyServiceImpl 删系列前先拦商品/品牌/门店）
        Long usedTrendies = trendyMapper.selectCount(new LambdaQueryWrapper<ShopTrendy>()
                .in(ShopTrendy::getIpId, ids));
        if (usedTrendies != null && usedTrendies > 0) {
            throw new ServiceException("该IP下还有 " + usedTrendies + " 个潮玩系列，请先在「绑定系列」里解绑");
        }
        removeByIds(ids);
    }

    /**
     * 名称必填 + 判重（excludeId 是修改时排除自己）+ 授权那两格的取值与先后
     */
    private void validate(ShopTrendyIp ip, Long excludeId) {
        if (StringUtils.isBlank(ip.getIpName())) {
            throw new ServiceException("IP名称不能为空");
        }
        if (ip.getLicenseType() != null
                && (ip.getLicenseType() < LICENSE_TYPE_MIN || ip.getLicenseType() > LICENSE_TYPE_MAX)) {
            throw new ServiceException("授权类型不合法（1原创 2独家授权 3非独家授权 4联名）");
        }
        if (ip.getLicenseStart() != null && ip.getLicenseEnd() != null
                && ip.getLicenseEnd().isBefore(ip.getLicenseStart())) {
            throw new ServiceException("授权结束日期不能早于开始日期");
        }
        // 版权人可以留空（留了就没人收到期提醒），填了就必须是库里真有的账号 —— 否则那条提醒会发给一个不存在的人
        if (ip.getUserId() != null && sysUserMapper.selectById(ip.getUserId()) == null) {
            throw new ServiceException("所选版权人/授权人不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopTrendyIp>()
                .eq(ShopTrendyIp::getIpName, ip.getIpName())
                .ne(excludeId != null, ShopTrendyIp::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("IP「" + ip.getIpName() + "」已存在");
        }
    }

    /**
     * NOT NULL 那几格补默认值（和建表默认一致）：页面没给就等于走库的默认档
     */
    private void applyDefaults(ShopTrendyIp ip) {
        if (ip.getLicenseType() == null) {
            ip.setLicenseType(LICENSE_TYPE_MIN);
        }
        if (ip.getIsHot() == null) {
            ip.setIsHot(0);
        }
        if (ip.getIsRecommend() == null) {
            ip.setIsRecommend(0);
        }
        if (ip.getSort() == null) {
            ip.setSort(0);
        }
        if (ip.getStatus() == null) {
            ip.setStatus(1);
        }
    }
}
