package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopBrand;
import com.shop.entity.ShopProduct;
import com.shop.entity.ShopStore;
import com.shop.entity.ShopStoreInvestment;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopBrandMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.ShopStoreInvestmentMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopStoreService;
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

@Service
@RequiredArgsConstructor
public class ShopStoreServiceImpl extends ServiceImpl<ShopStoreMapper, ShopStore> implements ShopStoreService {

    private final ShopProductMapper productMapper;

    private final SysUserMapper sysUserMapper;

    /** 只用来认一下门店绑的品牌真有其人；注入 mapper 而不是 service，避免两边互相依赖 */
    private final ShopBrandMapper shopBrandMapper;

    /** 只用来认一下来源招商线索、带回编号（建店和改绑都走它） */
    private final ShopStoreInvestmentMapper shopStoreInvestmentMapper;

    @Override
    public IPage<ShopStore> selectPage(ShopStore query) {
        if (query == null) {
            query = new ShopStore();
        }
        // 账号类型=门店的只看自己名下的店（自己建的或自己当店长的），其它类型看全部。
        // 一律覆盖 currentUserId，前端传什么都不认；
        // 不复用 managerId：那是本表真实列，而且单列拼不出"创建 OR 店长"的 OR
        query.setCurrentUserId(LoginUserUtils.isStore() ? StpUtil.getLoginIdAsLong() : null);
        // 名称/编码一个关键字搜两个字段，走自定义 XML；分页参数仍是 PageUtil 取的 Page
        IPage<ShopStore> result = baseMapper.selectStorePage(PageUtil.getPage(), query);
        fillManagerName(result.getRecords());
        fillBrand(result.getRecords());
        return result;
    }

    @Override
    public List<ShopStore> listOptions() {
        // 门店账号只能挂到自己名下的店，所以列表收窄了这里也要跟着收窄
        List<Long> scopeIds = LoginUserUtils.isStore() ? myStoreIds() : null;
        if (scopeIds != null && scopeIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 只给启用中的门店；停用的留在列表里靠 store_name 联表显示，不让新商品再选
        return list(new LambdaQueryWrapper<ShopStore>()
                .eq(ShopStore::getStatus, 1)
                .in(scopeIds != null, ShopStore::getId, scopeIds)
                .orderByAsc(ShopStore::getSort)
                .orderByAsc(ShopStore::getId));
    }

    @Override
    public ShopStore detail(Long id) {
        ShopStore store = getById(id);
        if (store != null) {
            fillManagerName(Collections.singletonList(store));
        }
        return store;
    }

    @Override
    public List<ShopStore> myStores() {
        // 一个账号可以是多家门店的店长，所以整表返回，由前端决定是默认选中还是下拉挑
        List<ShopStore> stores = list(new LambdaQueryWrapper<ShopStore>()
                .eq(ShopStore::getManagerId, StpUtil.getLoginIdAsLong())
                .orderByAsc(ShopStore::getSort)
                .orderByAsc(ShopStore::getId));
        fillManagerName(stores);
        return stores;
    }

    @Override
    public List<Long> myStoreIds() {
        // 口径同列表的 currentUserId：创建人 OR 店长，两处不一致的话「列表看得到、下拉里没有」。
        // OR 必须包在嵌套 and 里，否则会和逻辑删除条件拼在同一层，把 is_deleted = 0 一起放行
        List<ShopStore> stores = list(new LambdaQueryWrapper<ShopStore>()
                .select(ShopStore::getId)
                .and(w -> w.eq(ShopStore::getUserId, StpUtil.getLoginIdAsLong())
                        .or()
                        .eq(ShopStore::getManagerId, StpUtil.getLoginIdAsLong())));
        return stores.stream().map(ShopStore::getId).toList();
    }

    @Override
    public boolean inMyStores(Long storeId) {
        if (!LoginUserUtils.isStore()) {
            return true;
        }
        if (storeId == null) {
            return false;
        }
        Long userId = StpUtil.getLoginIdAsLong();
        // 走 count() 而不是把 myStoreIds() 整批查出来再 contains：只要一条存在性判断，也自动带上了软删过滤
        return count(new LambdaQueryWrapper<ShopStore>()
                .eq(ShopStore::getId, storeId)
                .and(w -> w.eq(ShopStore::getUserId, userId)
                        .or()
                        .eq(ShopStore::getManagerId, userId))) > 0;
    }

    @Override
    public void insert(ShopStore store) {
        // 新增时表单那栏是禁用的、不会传编码，和订单编号一样由系统生成
        if (StringUtils.isBlank(store.getStoreCode())) {
            store.setStoreCode(GeneratorNoUtils.getStoreNo());
        }
        validate(store, null);
        normalize(store);
        fillInvestmentSource(store, null);
        store.setUserId(StpUtil.getLoginIdAsLong());
        try {
            save(store);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_store_code，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("门店编码「" + store.getStoreCode() + "」已被占用，可能来自一个已删除的门店");
        }
    }

    @Override
    public void update(ShopStore store) {
        if (store.getId() == null) {
            throw new ServiceException("门店ID不能为空");
        }
        ShopStore exists = getById(store.getId());
        if (exists == null) {
            throw new ServiceException("门店不存在");
        }
        // 判的是库里那行的归属，不是请求体里的：否则前端把 userId/managerId 一起改掉就自推自出去了
        assertWritable(Collections.singletonList(exists));
        validate(store, store.getId());
        normalize(store);
        // 招商来源在修改弹窗里也改得动（实体上这两列已放开成 IGNORED），所以这一趟必须跟着走：
        // 编号是 id 的派生快照，不重新按 id 查一遍就会和 id 对不上
        fillInvestmentSource(store, store.getId());
        try {
            updateById(store);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("门店编码「" + store.getStoreCode() + "」已被占用，可能来自一个已删除的门店");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        assertWritable(listByIds(ids));
        // selectCount 会自动过滤已删除商品，所以下架/删除的商品不会拦住删除门店
        Long used = productMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                .in(ShopProduct::getStoreId, ids));
        if (used != null && used > 0) {
            throw new ServiceException("该门店下还有 " + used + " 个商品，请先在商品里取消关联或删除商品");
        }
        removeByIds(ids);
    }

    /**
     * 写操作的归属校验：账号类型=门店的只能动自己名下的店（创建的 OR 当店长的），口径同列表的 currentUserId。
     * 列表已经按这个口径收窄，这里补的是"直接拿 id 打接口"那条路。
     */
    private void assertWritable(List<ShopStore> stores) {
        if (!LoginUserUtils.isStore()) {
            return;
        }
        Long userId = StpUtil.getLoginIdAsLong();
        for (ShopStore store : stores) {
            if (!userId.equals(store.getUserId()) && !userId.equals(store.getManagerId())) {
                throw new ServiceException("门店「" + store.getStoreName() + "」不在你的门店范围内，无权修改或删除");
            }
        }
    }

    /**
     * 按 manager_id 回填店长昵称。
     * 没有在 SQL 里联 sys_user：分页用的是 BaseMapper 的 page()，没有 XML 可加 JOIN，
     * 而且一行的 manager_id 都取自当页出现过的 id，一次 selectBatchIds 就够（不超过页大小）。
     * SysUser.id 是 Integer，这里统一按 Long 建映射，和 shop_store.manager_id 对齐。
     */
    private void fillManagerName(List<ShopStore> stores) {
        Set<Long> managerIds = new HashSet<>();
        for (ShopStore store : stores) {
            if (store.getManagerId() != null) {
                managerIds.add(store.getManagerId());
            }
        }
        if (managerIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(managerIds)) {
            nameById.put(user.getId().longValue(),
                    StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
        }
        for (ShopStore store : stores) {
            store.setManagerName(nameById.get(store.getManagerId()));
        }
    }

    /**
     * 按 brand_id 回填品牌名 + LOGO，给列表那一列「绑定品牌」用（形状同 fillManagerName：
     * 一次 in 查询拿当页出现过的 id，不在 XML 里联 shop_brand）。
     * 品牌后来被停用或删掉了就查不到，这两格留空、前端显示 '-' —— 这一列只是给人看名字，
     * 表单里那格下拉的兜底是另一回事（停用品牌照样能在下拉里选出来）。
     */
    private void fillBrand(List<ShopStore> stores) {
        Set<Long> brandIds = new HashSet<>();
        for (ShopStore store : stores) {
            if (store.getBrandId() != null) {
                brandIds.add(store.getBrandId());
            }
        }
        if (brandIds.isEmpty()) {
            return;
        }
        Map<Long, ShopBrand> brandById = new HashMap<>();
        for (ShopBrand brand : shopBrandMapper.selectList(new LambdaQueryWrapper<ShopBrand>()
                .select(ShopBrand::getId, ShopBrand::getBrandName, ShopBrand::getLogo)
                .in(ShopBrand::getId, brandIds))) {
            brandById.put(brand.getId(), brand);
        }
        for (ShopStore store : stores) {
            ShopBrand brand = brandById.get(store.getBrandId());
            if (brand != null) {
                store.setBrandName(brand.getBrandName());
                store.setBrandLogo(brand.getLogo());
            }
        }
    }

    /**
     * 来源招商线索：新增（招商页的「创建门店」）和修改（门店表单里那格「招商信息」下拉）都会走这里。
     * 编号不认请求体里那份，按 id 从库里查出来覆盖（口径同购物车的分类/品牌由服务端回填）；
     * id 对不上直接报错，否则门店挂到一条不存在的线索上，门店页点「招商信息」就是空的。
     * 没选（id 为空）时两列一起清掉：只清 id 会留下一个没人认领的编号快照，
     * 而招商页那颗「创建门店」判占用读的正是编号，清不干净那条线索就永远回不去。
     * 一家门店一条线索是这张关联的不变量（判据同招商服务层的 fillStoreLink：看门店表的 investment_no），
     * 所以改绑必须拦"这条线索已经建出过别的门店"，否则一条线索能凭空分出几家店。
     *
     * @param excludeStoreId 改绑时传本店 id（本店自己占的那格不算冲突），新增时传 null
     */
    private void fillInvestmentSource(ShopStore store, Long excludeStoreId) {
        if (store.getInvestmentId() == null) {
            store.setInvestmentNo(null);
            return;
        }
        ShopStoreInvestment investment = shopStoreInvestmentMapper.selectById(store.getInvestmentId());
        if (investment == null) {
            throw new ServiceException("所选招商信息不存在");
        }
        // 这里比的是"列 = 传进来的字面量"，不是跨表列 = 列，所以不涉及两张表排序规则不一致的问题
        Long bound = baseMapper.selectCount(new LambdaQueryWrapper<ShopStore>()
                .eq(ShopStore::getInvestmentNo, investment.getInvestmentNo())
                .ne(excludeStoreId != null, ShopStore::getId, excludeStoreId));
        if (bound != null && bound > 0) {
            throw new ServiceException("招商线索「" + investment.getInvestmentNo() + "」已经建出过门店，一条线索只能建一家");
        }
        store.setInvestmentNo(investment.getInvestmentNo());
    }

    /**
     * NOT NULL 且带默认值的列：前端不传（表单没填或被 JSON 丢掉 undefined）时补上库里的默认值，
     * 否则 INSERT 会显式带上 NULL 而报"不能为空"。
     * cover_images 是 MySQL json 列，空字符串不是合法 JSON，写进去会直接报错，统一归一成 "[]"
     * （和商品的 images、品牌的代言人同一处理）。
     */
    private void normalize(ShopStore store) {
        if (store.getStoreType() == null) {
            store.setStoreType(1);
        }
        if (store.getBusinessStatus() == null) {
            store.setBusinessStatus(1);
        }
        if (store.getIsDelivery() == null) {
            store.setIsDelivery(0);
        }
        if (store.getSort() == null) {
            store.setSort(0);
        }
        if (store.getStatus() == null) {
            store.setStatus(1);
        }
        if (StringUtils.isBlank(store.getCoverImages())) {
            store.setCoverImages("[]");
        }
    }

    private void validate(ShopStore store, Long excludeId) {
        if (StringUtils.isBlank(store.getStoreCode())) {
            throw new ServiceException("门店编码不能为空");
        }
        if (StringUtils.isBlank(store.getStoreName())) {
            throw new ServiceException("门店名称不能为空");
        }
        // 店长可空，但填了就必须存在，否则店挂到一个不存在的账号上
        if (store.getManagerId() != null && sysUserMapper.selectById(store.getManagerId()) == null) {
            throw new ServiceException("所选店长不存在");
        }
        // 品牌同样可空（不绑品牌的门店是常态），但选了就得真有其人，口径同上
        if (store.getBrandId() != null && shopBrandMapper.selectById(store.getBrandId()) == null) {
            throw new ServiceException("所选品牌不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopStore>()
                .eq(ShopStore::getStoreCode, store.getStoreCode())
                .ne(excludeId != null, ShopStore::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("门店编码「" + store.getStoreCode() + "」已存在");
        }
    }
}
