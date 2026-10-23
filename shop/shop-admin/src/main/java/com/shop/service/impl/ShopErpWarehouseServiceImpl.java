package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpWarehouse;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpInventoryMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopErpWarehouseService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库 服务实现（写法照供应商 ShopErpSupplierServiceImpl：编码系统生成、名称必填、编码查重）
 *
 * 数据范围：ERP 操作员账号（account_type=5）只看/只改 user_id = 自己那几条（"我负责的仓库"），
 * 其它账号（平台、门店等）看全部。三件套同采购单：列表覆盖 userId、读写前 assertOwn、
 * ERP 新增钉成登录 id / 修改钉回原值。user_id 为 NULL（没指派负责人）的仓库 ERP 账号看不到，
 * 要让他管就得先由平台账号把这一格填上。
 *
 * capacity_used 由服务端说了算：新增补 0，修改钉回库里的值。它的数不在这份 service 里算，
 * 是库存那两条记账路（ShopErpInventoryServiceImpl#inbound / #outbound）末尾按库存表现汇总刷进来的。
 *
 * 两处拿"仓里还有多少货"当闸门（都现量 shop_erp_inventory.stock，不信 capacity_used 那格冗余快照）：
 * 1. 还有库存的仓库不许删（{@link #deleteByIds}），报错带仓名和件数；
 * 2. 改仓库时「仓库容量」「容量预警值」不能录到低于当前库存（{@link #validate}），留空=不限仍然允许。
 * 前端按列表那列「已用/容量」把这两道先禁掉（复选框不给勾、删除按钮灰掉带提示），服务端这道是兜底。
 *
 * 容量两格自己还得排个序：预警值不能大于容量（{@link #validate}，新增和修改都走这条）。
 * "默认取容量的 10%"是前端表单填了容量时回填的便利值，服务端不替客户端算，免得把留空=不设预警线这条覆盖掉。
 */
@Service
@RequiredArgsConstructor
public class ShopErpWarehouseServiceImpl extends ServiceImpl<ShopErpWarehouseMapper, ShopErpWarehouse>
        implements ShopErpWarehouseService {

    /** 只用来做"关联门店传了就必须真有其店"的存在性校验；门店都是逻辑删除，所以不加物理外键 */
    private final ShopStoreMapper shopStoreMapper;

    /** 同上，校验负责的 ERP 操作员 */
    private final SysUserMapper sysUserMapper;

    /** 量"这家仓还有多少货"：删仓库和调低容量两道闸都靠它（只读，不改库存） */
    private final ShopErpInventoryMapper shopErpInventoryMapper;

    @Override
    public List<ShopErpWarehouse> listOptions() {
        // 只给启用中的仓库（禁用的留在列表里显示名字，不让入库单再选）。
        // 这一条**不按 ERP 收窄**：它是入库表单的下选项、不是"我的数据"，
        // 按负责人筛会让还没被指派任何仓库的 ERP 连入库单都开不了
        // （他要查看/修改仓库本身，走的还是收窄过的那份列表和详情）
        return list(new LambdaQueryWrapper<ShopErpWarehouse>()
                .eq(ShopErpWarehouse::getStatus, 1)
                .orderByAsc(ShopErpWarehouse::getWarehouseName)
                .orderByAsc(ShopErpWarehouse::getId));
    }

    @Override
    public IPage<ShopErpWarehouse> selectPage(ShopErpWarehouse query) {
        if (query == null) {
            query = new ShopErpWarehouse();
        }
        // 一律覆盖 userId：ERP 账号换成登录 id，其它账号置空。列表上没有"按负责操作员筛"那一格，
        // 所以不能让请求体里的 userId 越过账号类型去查别人负责的仓库
        query.setUserId(LoginUserUtils.isErp() ? StpUtil.getLoginIdAsLong() : null);
        // 关键字跨四列搜 + 门店名/操作员名联表，写条件用 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectWarehousePage(PageUtil.getPage(), query);
    }

    @Override
    public ShopErpWarehouse selectWarehouseById(Long id) {
        ShopErpWarehouse warehouse = baseMapper.selectWarehouseById(id);
        if (warehouse == null) {
            throw new ServiceException("仓库不存在");
        }
        assertOwn(warehouse);
        return warehouse;
    }

    @Override
    public void insert(ShopErpWarehouse warehouse) {
        // 新增时表单那栏是禁用的、不会传编码，和供应商编码一样由系统生成
        if (StringUtils.isBlank(warehouse.getWarehouseCode())) {
            warehouse.setWarehouseCode(GeneratorNoUtils.getWarehouseNo());
        }
        validate(warehouse, null);
        normalize(warehouse);
        // ERP 账号建的仓记在自己名下（这一格表单里禁用了，服务端再钉一道），否则他建完就在列表里找不到它
        if (LoginUserUtils.isErp()) {
            warehouse.setUserId(StpUtil.getLoginIdAsLong());
        }
        try {
            save(warehouse);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_warehouse_code，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("仓库编码「" + warehouse.getWarehouseCode()
                    + "」已被占用，可能来自一个已删除的仓库");
        }
    }

    @Override
    public void update(ShopErpWarehouse warehouse) {
        if (warehouse.getId() == null) {
            throw new ServiceException("仓库ID不能为空");
        }
        ShopErpWarehouse exists = getById(warehouse.getId());
        if (exists == null) {
            throw new ServiceException("仓库不存在");
        }
        // 判库里那行的归属，不判请求体（请求体里的 user_id 带什么都不算数）
        assertOwn(exists);
        // 负责的操作员只有非 ERP 账号能换；ERP 账号钉回原值，免得改个备注把仓库挪到别人名下
        // （这一列是 IGNORED，给 null 就会真写出 null，所以是钉值不是留空）
        if (LoginUserUtils.isErp()) {
            warehouse.setUserId(exists.getUserId());
        }
        // 编码/名称允许不传，不传时沿用原值参与查重（not_empty 策略下不传本来也不会写这一列）
        if (StringUtils.isBlank(warehouse.getWarehouseCode())) {
            warehouse.setWarehouseCode(exists.getWarehouseCode());
        }
        if (StringUtils.isBlank(warehouse.getWarehouseName())) {
            warehouse.setWarehouseName(exists.getWarehouseName());
        }
        // 已用容量不让表单写：弹窗里那一格只读，但提交时整行都会带回来，钉回库里的值才不怕客户端乱传
        warehouse.setCapacityUsed(exists.getCapacityUsed());
        validate(warehouse, warehouse.getId());
        normalize(warehouse);
        try {
            updateById(warehouse);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("仓库编码「" + warehouse.getWarehouseCode()
                    + "」已被占用，可能来自一个已删除的仓库");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 先按 id 捞出实体：既给 ERP 判归属（堵住直接拿别人仓库 id 打接口这条路），
        // 也给下面那句报错报得出仓库名字
        List<ShopErpWarehouse> warehouses = listByIds(ids);
        Map<Long, Integer> stockUsed = stockUsedOf(ids);
        for (ShopErpWarehouse warehouse : warehouses) {
            assertOwn(warehouse);
            // 仓里还有货就不许删：删掉之后那些库存行仍然挂着这个 warehouse_id，
            // 库存页显示"已删除仓库 #id"、出库单也找不到发货仓，等于把已经入进来的货凭空抹掉。
            // 拦的是 shop_erp_inventory.stock，锁着的那份也算（货还在仓里就照样占仓位）
            Integer used = stockUsed.get(warehouse.getId());
            if (used != null && used > 0) {
                throw new ServiceException("仓库「" + warehouse.getWarehouseName() + "」还有库存 " + used
                        + " 件，不能删除；请先把这一仓的货出掉或调走");
            }
        }
        // 挂了 warehouse_id 的单据（入库单、出库单明细）仍然不在这里拦：那些行是历史凭证，
        // 删了仓库它们显示"已删除仓库 #id"是可以接受的（真要拦就是供应商那套 selectCount 口径）
        removeByIds(ids);
    }

    /**
     * 按仓现汇总仓内件数（shop_erp_inventory.stock 之和，不看锁定，含被订单锁住的那部分）。
     *
     * 不用 capacity_used 那一格：它是入库/出库末尾刷出来的冗余快照，历史数据可能还没回填，
     * 删仓库和改容量这两道闸要用就量现账（一条 IN 查完，不逐仓查）。
     * 只捞 stock > 0 的行，没行的仓在返回的 Map 里就是没有（调用方按 0 处理）。
     */
    private Map<Long, Integer> stockUsedOf(List<Long> warehouseIds) {
        Map<Long, Integer> used = new HashMap<>();
        List<ShopErpInventory> rows = shopErpInventoryMapper.selectList(new LambdaQueryWrapper<ShopErpInventory>()
                .select(ShopErpInventory::getWarehouseId, ShopErpInventory::getStock)
                .in(ShopErpInventory::getWarehouseId, warehouseIds)
                .gt(ShopErpInventory::getStock, 0));
        for (ShopErpInventory row : rows) {
            used.merge(row.getWarehouseId(), row.getStock() == null ? 0 : row.getStock(), Integer::sum);
        }
        return used;
    }

    /**
     * ERP 账号的归属校验，口径同采购单：非 ERP 直接放过（平台等看全部、也管全部）。
     * 这里补的是"绕过列表直接拿别人仓库 id 打接口"那条路。
     * user_id 为 NULL 的仓库（没指派负责人）ERP 账号本来就看不到，这里也一律改不动。
     */
    private void assertOwn(ShopErpWarehouse warehouse) {
        if (!LoginUserUtils.isErp()) {
            return;
        }
        Long userId = warehouse.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这个仓库不是你负责的，无权查看或修改");
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传时补上库里的默认值，否则 INSERT 会显式带 NULL 而报"不能为空"。
     * capacity_used 也走这里，别让它跟着表单传进来的值走（update 里已经钉过，这里是新增的兜底）
     */
    private void normalize(ShopErpWarehouse warehouse) {
        if (warehouse.getWarehouseType() == null) {
            warehouse.setWarehouseType(1);
        }
        if (warehouse.getStatus() == null) {
            warehouse.setStatus(1);
        }
        if (warehouse.getWarnEnable() == null) {
            warehouse.setWarnEnable(1);
        }
        if (warehouse.getCapacityUsed() == null) {
            warehouse.setCapacityUsed(0);
        }
    }

    private void validate(ShopErpWarehouse warehouse, Long excludeId) {
        if (StringUtils.isBlank(warehouse.getWarehouseCode())) {
            throw new ServiceException("仓库编码不能为空");
        }
        if (StringUtils.isBlank(warehouse.getWarehouseName())) {
            throw new ServiceException("仓库名称不能为空");
        }
        warehouse.setWarehouseCode(warehouse.getWarehouseCode().trim());
        warehouse.setWarehouseName(warehouse.getWarehouseName().trim());
        // 所在地区必填：收货地址只选了地区没录坐标时，统一下单就拿这一列匹仓（selectWarehouseIdByArea，区 > 市 > 省），
        // 空着的仓在这一档也进不了候选池
        if (StringUtils.isBlank(warehouse.getAreaCode())) {
            throw new ServiceException("请选择仓库所在地区");
        }
        // 经纬度必填：统一下单靠它就近分仓（ShopErpInventoryMapper#selectNearestWarehouseId 会把没坐标的仓
        // 整个排除在候选池外）。一家录不全的仓等于货压在里面发不出去，宁可在保存这里拦住，
        // 也别等买家下单时才报"附近有货但发不出去"
        if (warehouse.getLongitude() == null || warehouse.getLatitude() == null) {
            throw new ServiceException("请填写仓库经纬度，或在地图上选点");
        }
        // 关联门店/负责操作员都可以留空（只有主仓、分仓本来就不挂门店），但填了就必须真有其人其店，
        // 口径同供应商的绑定账号、门店的店长
        if (warehouse.getStoreId() != null && shopStoreMapper.selectById(warehouse.getStoreId()) == null) {
            throw new ServiceException("所选关联门店不存在");
        }
        if (warehouse.getUserId() != null && sysUserMapper.selectById(warehouse.getUserId()) == null) {
            throw new ServiceException("所选ERP操作员不存在");
        }
        // 仓库容量 / 容量预警值不许录到比仓里现有的货还少：这两格是"这家仓装得下多少、快到顶了没有"，
        // 低于已入进来的数就等于账面上一建单就超载。留空是"不限/不设预警线"，照样允许
        //（新增的仓还没有货，所以只在改已有仓库时量一次）
        if (warehouse.getId() != null) {
            int used = stockUsedOf(List.of(warehouse.getId())).getOrDefault(warehouse.getId(), 0);
            if (used > 0) {
                if (warehouse.getCapacity() != null && warehouse.getCapacity() < used) {
                    throw new ServiceException("仓库容量不能小于当前库存 " + used + " 件");
                }
                if (warehouse.getCapacityWarn() != null && warehouse.getCapacityWarn() < used) {
                    throw new ServiceException("容量预警值不能小于当前库存 " + used + " 件");
                }
            }
        }
        // 预警线不能高过容量：它记的是"还剩这么多件就该补货/腾仓位"，比容量还大等于刚建好就天天预警。
        // 容量留空 = 不限，没有上限可比；默认值（容量的 10%）是前端表单回填的，服务端不替它算
        if (warehouse.getCapacity() != null && warehouse.getCapacityWarn() != null
                && warehouse.getCapacityWarn() > warehouse.getCapacity()) {
            throw new ServiceException("容量预警值「" + warehouse.getCapacityWarn()
                    + "」不能大于仓库容量 " + warehouse.getCapacity() + " 件");
        }
        // 表上只有 uk_warehouse_code 一个唯一键，名称不查重（原始 DDL 就没这约束，别自己加）；
        // 类型=3(门店仓) 也不强制必须挂门店：他给的是"门店仓时用"，不是"必填"
        Long codeCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopErpWarehouse>()
                .eq(ShopErpWarehouse::getWarehouseCode, warehouse.getWarehouseCode())
                .ne(excludeId != null, ShopErpWarehouse::getId, excludeId));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException("仓库编码「" + warehouse.getWarehouseCode() + "」已存在");
        }
    }
}
