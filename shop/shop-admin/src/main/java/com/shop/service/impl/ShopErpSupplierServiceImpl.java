package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpSupplier;
import com.shop.entity.ShopSupplierProduct;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpSupplierMapper;
import com.shop.mapper.ShopSupplierProductMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopErpSupplierService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
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

/**
 * 供应商 服务实现（写法照物流公司 ShopDeliveryCompanyServiceImpl：编码系统生成、名称必填、查重）
 *
 * 数据范围和物流公司不同、和门店/商品同一类思路：供应商账号（account_type=4）只看/只改 user_id = 自己 的那条。
 * user_id 是表单里的「绑定账号」，由平台/门店等账号指定（不选就记创建人）；供应商账号自己改不动，
 * 服务层把它钉回原值。平台、门店等其它账号看全部。
 */
@Service
@RequiredArgsConstructor
public class ShopErpSupplierServiceImpl extends ServiceImpl<ShopErpSupplierMapper, ShopErpSupplier>
        implements ShopErpSupplierService {

    private final SysUserMapper sysUserMapper;

    /** 只用来统计「名下还有几条供货关系」，删除前的引用校验；注入 mapper 而非 service，避免两边互相依赖 */
    private final ShopSupplierProductMapper supplierProductMapper;

    @Override
    public List<ShopErpSupplier> listOptions() {
        // 只给启用中的供应商（停用的留在列表里显示名字，不让采购单再选），口径同门店/品牌的 options。
        // 供应商账号只能看到自己绑定那条：和列表同一个 user_id 条件，别以为下拉就能绕过数据范围
        return list(new LambdaQueryWrapper<ShopErpSupplier>()
                .eq(ShopErpSupplier::getStatus, 1)
                .eq(LoginUserUtils.isSupplier(), ShopErpSupplier::getUserId, StpUtil.getLoginIdAsLong())
                .orderByAsc(ShopErpSupplier::getSupplierName)
                .orderByAsc(ShopErpSupplier::getId));
    }

    @Override
    public IPage<ShopErpSupplier> selectPage(ShopErpSupplier query) {
        if (query == null) {
            query = new ShopErpSupplier();
        }
        // 一律覆盖 userId：供应商换成登录 id，其它账号置空。前端没有"按创建人筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人的记录
        query.setUserId(LoginUserUtils.isSupplier() ? StpUtil.getLoginIdAsLong() : null);
        // 关键字跨四列搜，写条件用 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        IPage<ShopErpSupplier> result = baseMapper.selectSupplierPage(PageUtil.getPage(), query);
        fillUserName(result.getRecords());
        return result;
    }

    @Override
    public ShopErpSupplier selectSupplierById(Long id) {
        ShopErpSupplier supplier = getById(id);
        if (supplier == null) {
            throw new ServiceException("供应商不存在");
        }
        assertOwn(supplier);
        // 编辑弹窗里那个「绑定账号」选择器要显示人名，所以昵称按 user_id 现查（不是本表列）
        fillUserName(Collections.singletonList(supplier));
        return supplier;
    }

    @Override
    public void insert(ShopErpSupplier supplier) {
        // 新增时表单那栏是禁用的、不会传编码，和商品/物流公司编码一样由系统生成
        if (StringUtils.isBlank(supplier.getSupplierCode())) {
            supplier.setSupplierCode(GeneratorNoUtils.getSupplierNo());
        }
        validate(supplier, null);
        normalize(supplier);
        // 绑定账号：供应商账号自己录的只能挂自己名下；平台/门店没选就沿用"记创建人"的留痕口径
        if (supplier.getUserId() == null || LoginUserUtils.isSupplier()) {
            supplier.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(supplier);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_supplier_code，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("供应商编码「" + supplier.getSupplierCode()
                    + "」已被占用，可能来自一个已删除的供应商");
        }
    }

    @Override
    public void update(ShopErpSupplier supplier) {
        if (supplier.getId() == null) {
            throw new ServiceException("供应商ID不能为空");
        }
        ShopErpSupplier exists = getById(supplier.getId());
        if (exists == null) {
            throw new ServiceException("供应商不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 绑定账号只有平台/门店等账号能改；供应商账号一律钉回原值，
        // 免得改个名字就把记录挪到别人名下（或者清空后自己再也看不到这条）。
        // 这一列是 IGNORED，给 null 就会真写出 null，所以钉值而不是留空
        if (LoginUserUtils.isSupplier()) {
            supplier.setUserId(exists.getUserId());
        }
        // 编码/名称允许不传，不传时沿用原值参与查重（not_empty 策略下不传本来也不会写这一列）
        if (StringUtils.isBlank(supplier.getSupplierCode())) {
            supplier.setSupplierCode(exists.getSupplierCode());
        }
        if (StringUtils.isBlank(supplier.getSupplierName())) {
            supplier.setSupplierName(exists.getSupplierName());
        }
        validate(supplier, supplier.getId());
        normalize(supplier);
        try {
            updateById(supplier);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("供应商编码「" + supplier.getSupplierCode()
                    + "」已被占用，可能来自一个已删除的供应商");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 供应商账号删的必须是自己那条：先按 id 捞出实体逐条校验，堵住直接拿别人 id 打接口这条路
        if (LoginUserUtils.isSupplier()) {
            for (ShopErpSupplier supplier : listByIds(ids)) {
                assertOwn(supplier);
            }
        }
        // 被 shop_supplier_product 引用的供应商不许删：删了之后那些供货关系就挂在没人认领的 id 上，
        // 「供应商商品」列表联不出供应商名（采购单那侧也同理），口径同物流公司被送货员引用。
        // 一次 in 统计未删除的关系条数，不在循环里逐条查
        Long relationCount = supplierProductMapper.selectCount(new LambdaQueryWrapper<ShopSupplierProduct>()
                .in(ShopSupplierProduct::getSupplierId, ids));
        if (relationCount != null && relationCount > 0) {
            throw new ServiceException("先删掉这些供应商名下的 " + relationCount + " 条供货关系（供应商商品页）");
        }
        removeByIds(ids);
    }

    /**
     * 供应商账号的归属校验，口径同 selectPage：非供应商直接放过（平台/门店看全部、也管全部）。
     * 列表已经收窄了，这里补的是"绕过列表直接拿别人 id 打接口"那条路。
     */
    private void assertOwn(ShopErpSupplier supplier) {
        if (!LoginUserUtils.isSupplier()) {
            return;
        }
        Long userId = supplier.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这条供应商记录不属于你，无权查看或修改");
        }
    }

    /**
     * 按 user_id 回填绑定账号昵称。
     * 没有在 XML 里联 sys_user：详情走 BaseMapper 的 getById，两条路共用一个回填才不会口径不一致，
     * 而且一行的 user_id 都取自当页出现过的 id，一次 selectBatchIds 就够（不超过页大小）。
     * SysUser.id 是 Integer，这里统一按 Long 建映射，和 shop_erp_supplier.user_id 对齐。
     */
    private void fillUserName(List<ShopErpSupplier> suppliers) {
        Set<Long> userIds = new HashSet<>();
        for (ShopErpSupplier supplier : suppliers) {
            if (supplier.getUserId() != null) {
                userIds.add(supplier.getUserId());
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
        for (ShopErpSupplier supplier : suppliers) {
            supplier.setUserName(nameById.get(supplier.getUserId()));
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报"不能为空"
     */
    private void normalize(ShopErpSupplier supplier) {
        if (supplier.getStatus() == null) {
            supplier.setStatus(1);
        }
    }

    private void validate(ShopErpSupplier supplier, Long excludeId) {
        if (StringUtils.isBlank(supplier.getSupplierCode())) {
            throw new ServiceException("供应商编码不能为空");
        }
        if (StringUtils.isBlank(supplier.getSupplierName())) {
            throw new ServiceException("供应商名称不能为空");
        }
        supplier.setSupplierCode(supplier.getSupplierCode().trim());
        supplier.setSupplierName(supplier.getSupplierName().trim());
        // 绑定账号可以留空（不挂账号的供应商只有平台/门店看得见），但填了就必须真有其人，口径同门店的店长
        if (supplier.getUserId() != null && sysUserMapper.selectById(supplier.getUserId()) == null) {
            throw new ServiceException("所选绑定账号不存在");
        }
        // 表上只有 uk_supplier_code 一个唯一键，名称不查重（原始 DDL 就没这约束，别自己加）
        Long codeCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpSupplier>()
                .eq(ShopErpSupplier::getSupplierCode, supplier.getSupplierCode())
                .ne(excludeId != null, ShopErpSupplier::getId, excludeId));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException("供应商编码「" + supplier.getSupplierCode() + "」已存在");
        }
    }
}
