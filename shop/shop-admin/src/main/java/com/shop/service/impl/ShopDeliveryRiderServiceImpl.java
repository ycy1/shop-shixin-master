package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopDeliveryCompany;
import com.shop.entity.ShopDeliveryRider;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopDeliveryCompanyMapper;
import com.shop.mapper.ShopDeliveryRiderMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopDeliveryRiderService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopDeliveryRiderServiceImpl extends ServiceImpl<ShopDeliveryRiderMapper, ShopDeliveryRider>
        implements ShopDeliveryRiderService {

    private final ShopDeliveryCompanyMapper logisticsCompanyMapper;

    private final SysUserMapper sysUserMapper;

    /** 经纬度值域：超了范围的数据拿去算"离我最近的配送单"会让 ST_Distance_Sphere 直接抛库里的 range 异常 */
    private static final BigDecimal LNG_RANGE = new BigDecimal("180");

    private static final BigDecimal LAT_RANGE = new BigDecimal("90");

    @Override
    public IPage<ShopDeliveryRider> selectPage(ShopDeliveryRider query) {
        if (query == null) {
            query = new ShopDeliveryRider();
        }
        // 不再按账号类型收窄（2026-09-21 那轮"送货员和配送单不在关联门店"）：
        // 表上已经没有 store_id 这一列，归属只剩物流公司，谁都能看全平台的人，只是搜索栏能按公司筛。
        // 关键字要搜三个字段，所以还是走自定义 XML，分页参数仍是 PageUtil 取的 Page
        IPage<ShopDeliveryRider> result = baseMapper.selectRiderPage(PageUtil.getPage(), query);
        fillNames(result.getRecords());
        return result;
    }

    @Override
    public List<ShopDeliveryRider> listOptions() {
        // 只给启用中的送货员；停用的留在列表里靠 rider_name 显示，不让新配送单再派给他
        return list(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getStatus, 1)
                .orderByAsc(ShopDeliveryRider::getId));
    }

    @Override
    public ShopDeliveryRider detail(Long id) {
        ShopDeliveryRider rider = getById(id);
        if (rider != null) {
            fillNames(Collections.singletonList(rider));
        }
        return rider;
    }

    @Override
    public ShopDeliveryRider getMyInfo() {
        ShopDeliveryRider rider = myRider();
        if (rider != null) {
            fillNames(Collections.singletonList(rider));
        }
        return rider;
    }

    @Override
    public void updateMyInfo(ShopDeliveryRider rider) {
        ShopDeliveryRider exists = myRider();
        if (exists == null) {
            throw new ServiceException("当前账号还没有送货员档案，请联系管理员");
        }
        if (StringUtils.isBlank(rider.getRiderName())) {
            throw new ServiceException("送货员姓名不能为空");
        }
        if (StringUtils.isBlank(rider.getPhone())) {
            throw new ServiceException("手机号不能为空");
        }
        // 只把白名单这几个字段搬到查出来的那行上再存：启用状态、最大接单数、归属（物流公司）、统计一律以库里的值为准，
        // 前端传什么都不认。可空列照请求里的值写，所以"清空头像/清空证件/清掉常驻地点"是能生效的；
        // 交通工具和两个状态列没传（null）时会被全局 not_empty 策略整列跳过，等于不改
        validateResidentPoint(rider.getResidentLng(), rider.getResidentLat());
        exists.setAvatar(rider.getAvatar());
        exists.setRiderName(rider.getRiderName().trim());
        exists.setPhone(rider.getPhone().trim());
        exists.setVehicleNo(rider.getVehicleNo());
        exists.setServiceCity(rider.getServiceCity());
        exists.setServiceArea(rider.getServiceArea());
        // 常驻地点是本人最常知道的那一格（他每天从那儿出发），所以和「服务城市/服务区域」一样在自助白名单里：
        // 配送列表"离我最近"那一版排序读的就是这两个坐标
        exists.setResidentAddress(rider.getResidentAddress());
        exists.setResidentLng(rider.getResidentLng());
        exists.setResidentLat(rider.getResidentLat());
        exists.setVehicleType(rider.getVehicleType());
        exists.setOnlineStatus(rider.getOnlineStatus());
        exists.setWorkStatus(rider.getWorkStatus());
        exists.setIdCard(StringUtils.isBlank(rider.getIdCard()) ? null : rider.getIdCard().trim());
        exists.setIdCardFront(rider.getIdCardFront());
        exists.setIdCardBack(rider.getIdCardBack());
        exists.setHealthCert(rider.getHealthCert());
        exists.setHealthCertExpire(rider.getHealthCertExpire());
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getPhone, exists.getPhone())
                .ne(ShopDeliveryRider::getId, exists.getId()));
        if (count != null && count > 0) {
            throw new ServiceException("手机号「" + exists.getPhone() + "」已被其他送货员使用");
        }
        try {
            updateById(exists);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("手机号「" + exists.getPhone() + "」已被占用，可能来自一个已删除的送货员");
        }
    }

    /**
     * 当前登录账号对应的送货员档案。getOne 第二个参数 false：一个账号理论上只有一份档案，
     * 但历史脏数据里重复时别抛 TooManyResultsException，取第一条就行。
     */
    private ShopDeliveryRider myRider() {
        return getOne(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getUserId, StpUtil.getLoginIdAsLong()), false);
    }

    @Override
    public void insert(ShopDeliveryRider rider) {
        // 新增时表单那栏是禁用的、不会传编码，和门店编码一样由系统生成
        if (StringUtils.isBlank(rider.getRiderCode())) {
            rider.setRiderCode(GeneratorNoUtils.getRiderNo());
        }
        validate(rider, null);
        normalize(rider);
        try {
            save(rider);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_rider_code / uk_phone，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("送货员编码「" + rider.getRiderCode() + "」或手机号「" + rider.getPhone()
                    + "」已被占用，可能来自一个已删除的送货员");
        }
    }

    @Override
    public void update(ShopDeliveryRider rider) {
        if (rider.getId() == null) {
            throw new ServiceException("送货员ID不能为空");
        }
        ShopDeliveryRider exists = getById(rider.getId());
        if (exists == null) {
            throw new ServiceException("送货员不存在");
        }
        // 归属门店那一列已经删了，送货员只剩物流公司一条线，所以这里不再有"只能动自己名下的人"那层校验
        validate(rider, rider.getId());
        normalize(rider);
        try {
            updateById(rider);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("送货员编码「" + rider.getRiderCode() + "」或手机号「" + rider.getPhone()
                    + "」已被占用，可能来自一个已删除的送货员");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    /**
     * 按 logistics_company_id / user_id 回填物流公司名和账号昵称。
     * 没有在 SQL 里联表：一次 selectBatchIds 就能拿下当页出现过的 id（不超过页大小）。
     * SysUser.id 是 Integer，这里统一按 Long 建映射，和 user_id 对齐。
     */
    private void fillNames(List<ShopDeliveryRider> riders) {
        Set<Long> companyIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (ShopDeliveryRider rider : riders) {
            if (rider.getLogisticsCompanyId() != null) {
                companyIds.add(rider.getLogisticsCompanyId());
            }
            if (rider.getUserId() != null) {
                userIds.add(rider.getUserId());
            }
        }

        Map<Long, String> companyNameById = new HashMap<>();
        if (!companyIds.isEmpty()) {
            for (ShopDeliveryCompany company : logisticsCompanyMapper.selectBatchIds(companyIds)) {
                companyNameById.put(company.getId(), company.getCompanyName());
            }
        }

        Map<Long, String> userNameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNameById.put(user.getId().longValue(),
                        StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
            }
        }

        for (ShopDeliveryRider rider : riders) {
            rider.setLogisticsCompanyName(companyNameById.get(rider.getLogisticsCompanyId()));
            rider.setUserName(userNameById.get(rider.getUserId()));
        }
    }

    /**
     * NOT NULL 且带默认值的列：前端不传（表单没填或被 JSON 丢掉 undefined）时补上库里的默认值，
     * 否则 INSERT 会显式带上 NULL 而报"不能为空"。
     * 三个统计列不在这里补：表单不编辑，新增时留在 null 上让库默认值生效，
     * 实体上又用 NEVER 锁住了 UPDATE，不会被请求里的值改掉。
     */
    private void normalize(ShopDeliveryRider rider) {
        if (rider.getVehicleType() == null) {
            rider.setVehicleType(1);
        }
        if (rider.getMaxOrderCount() == null) {
            rider.setMaxOrderCount(5);
        }
        if (rider.getOnlineStatus() == null) {
            rider.setOnlineStatus(0);
        }
        if (rider.getWorkStatus() == null) {
            rider.setWorkStatus(0);
        }
        if (rider.getStatus() == null) {
            rider.setStatus(1);
        }
    }

    /**
     * 常驻经纬度：要么都不填（这个人就不参与"离我最近"排序），要么都填，填了还要在值域内。
     * 半截坐标存进去只是让那一列看着有值却算不出距离；超出 ±180/±90 更糟，
     * 配送列表那一版排序会直接踩到 MySQL ST_Distance_Sphere 的 range 异常（报错的人看不懂是哪一格填错的）。
     */
    private void validateResidentPoint(BigDecimal lng, BigDecimal lat) {
        if (lng == null && lat == null) {
            return;
        }
        if (lng == null || lat == null) {
            throw new ServiceException("常驻地点的经度和纬度要一起填，只填一个排不出就近的配送单");
        }
        if (lng.abs().compareTo(LNG_RANGE) > 0 || lat.abs().compareTo(LAT_RANGE) > 0) {
            throw new ServiceException("常驻经纬度超出范围（经度 -180~180、纬度 -90~90）");
        }
    }

    private void validate(ShopDeliveryRider rider, Long excludeId) {
        if (StringUtils.isBlank(rider.getRiderCode())) {
            throw new ServiceException("送货员编码不能为空");
        }
        if (StringUtils.isBlank(rider.getRiderName())) {
            throw new ServiceException("送货员姓名不能为空");
        }
        if (StringUtils.isBlank(rider.getPhone())) {
            throw new ServiceException("手机号不能为空");
        }
        // 归属都可空（未开通账号、没挂靠物流公司），但填了就必须存在，否则挂到一个不存在的账号上
        if (rider.getUserId() != null && sysUserMapper.selectById(rider.getUserId()) == null) {
            throw new ServiceException("所关联的送货员账号不存在");
        }
        if (rider.getLogisticsCompanyId() != null
                && logisticsCompanyMapper.selectById(rider.getLogisticsCompanyId()) == null) {
            throw new ServiceException("所选物流公司不存在");
        }
        validateResidentPoint(rider.getResidentLng(), rider.getResidentLat());
        Long codeCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getRiderCode, rider.getRiderCode())
                .ne(excludeId != null, ShopDeliveryRider::getId, excludeId));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException("送货员编码「" + rider.getRiderCode() + "」已存在");
        }
        Long phoneCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getPhone, rider.getPhone())
                .ne(excludeId != null, ShopDeliveryRider::getId, excludeId));
        if (phoneCount != null && phoneCount > 0) {
            throw new ServiceException("手机号「" + rider.getPhone() + "」已存在");
        }
    }
}
