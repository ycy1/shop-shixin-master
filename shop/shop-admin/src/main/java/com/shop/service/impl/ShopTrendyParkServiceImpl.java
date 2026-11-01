package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopTrendyIp;
import com.shop.entity.ShopTrendyPark;
import com.shop.entity.ShopTrendyParkPhoto;
import com.shop.entity.ShopTrendyParkTicket;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopTrendyIpMapper;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.ShopTrendyParkPhotoMapper;
import com.shop.mapper.ShopTrendyParkTicketMapper;
import com.shop.service.ShopTrendyParkService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopTrendyParkServiceImpl extends ServiceImpl<ShopTrendyParkMapper, ShopTrendyPark> implements ShopTrendyParkService {

    /** 状态两档（字典 park_status）：1 营业中 0 停业，超出范围的标签渲染不出来，落库前拦掉 */
    private static final int STATUS_OPEN = 1;
    private static final int STATUS_CLOSED = 0;

    /** 只用来看名下绑的 IP 真存在、并回填列表那一格名字（注入 mapper 而不是 service，避免两边互相依赖） */
    private final ShopTrendyIpMapper trendyIpMapper;

    /** 只用回来数"这家园区名下有几个票种"和删除前那道拦截 */
    private final ShopTrendyParkTicketMapper ticketMapper;

    /** 只在删除园区时用：名下的照片跟着一起软删 */
    private final ShopTrendyParkPhotoMapper photoMapper;

    @Override
    public IPage<ShopTrendyPark> selectPage(ShopTrendyPark query) {
        if (query == null) {
            query = new ShopTrendyPark();
        }
        // 名称/编号一个关键字搜两个字段，走自定义 XML；分页参数仍是 PageUtil 取的 Page
        IPage<ShopTrendyPark> page = baseMapper.selectParkPage(PageUtil.getPage(), query);
        fillIpName(page.getRecords());
        fillTicketCount(page.getRecords());
        return page;
    }

    @Override
    public List<ShopTrendyPark> listOptions() {
        // 只给营业中的：停业的园区不该在票种表单、出票表单里被新选中（口径同盲盒表单那份潮玩系列候选）。
        // 真撞上「票种绑着一个后来停业的园区」，那一格下拉会显示 id 而不是名字，和这项目其它几处一样
        return list(new LambdaQueryWrapper<ShopTrendyPark>()
                .eq(ShopTrendyPark::getStatus, STATUS_OPEN)
                .orderByDesc(ShopTrendyPark::getId));
    }

    @Override
    public ShopTrendyPark detail(Long id) {
        ShopTrendyPark park = getById(id);
        if (park != null) {
            List<ShopTrendyPark> one = new ArrayList<>(1);
            one.add(park);
            fillIpName(one);
        }
        return park;
    }

    @Override
    public void insert(ShopTrendyPark park) {
        // 新增时表单那栏是禁用的、不会传编号，和门店编码一样由系统生成
        if (StringUtils.isBlank(park.getParkNo())) {
            park.setParkNo(GeneratorNoUtils.getParkNo());
        }
        validate(park, null);
        normalize(park);
        try {
            save(park);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_park_no，前置查重（只查未删除的）看不到它
            throw new ServiceException("园区编号「" + park.getParkNo() + "」已被占用，可能来自一个已删除的园区");
        }
    }

    @Override
    public void update(ShopTrendyPark park) {
        if (park.getId() == null) {
            throw new ServiceException("园区ID不能为空");
        }
        if (getById(park.getId()) == null) {
            throw new ServiceException("园区不存在");
        }
        validate(park, park.getId());
        normalize(park);
        try {
            updateById(park);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("园区编号「" + park.getParkNo() + "」已被占用，可能来自一个已删除的园区");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 票种是挂在园区下面的，直接删园区会让那一堆票种指向一个没了的园区，所以先拦一句让人去处理
        //（口径同删IP前先拦系列、删门店前先拦商品）
        Long used = ticketMapper.selectCount(new LambdaQueryWrapper<ShopTrendyParkTicket>()
                .in(ShopTrendyParkTicket::getParkId, ids));
        if (used != null && used > 0) {
            throw new ServiceException("该园区下还有 " + used + " 个票种，请先在「票种管理」里删掉它们或改到别的园区");
        }
        // 照片就跟着园区走（park_id 必填、所属 IP 也是从园区那一格带过来的，离开这家园区没有意义），
        // 所以一起软删、不拦 —— 不一起删的话「园区照片」那页会攒出一堆「已删除园区 #id」的行
        //（口径同删盲盒连带删它名下的款式）
        photoMapper.delete(new LambdaQueryWrapper<ShopTrendyParkPhoto>().in(ShopTrendyParkPhoto::getParkId, ids));
        removeByIds(ids);
    }

    /**
     * 回填「关联IP」那一格的名字：按当页出现过的 ip_id 一次查回来，不联表
     *（形状同 ShopStoreServiceImpl#fillBrand；IP 后来被删掉就查不到，这一格留空、前端显示 '-'）
     */
    private void fillIpName(List<ShopTrendyPark> parks) {
        if (parks == null || parks.isEmpty()) {
            return;
        }
        Set<Long> ipIds = new HashSet<>();
        for (ShopTrendyPark park : parks) {
            if (park.getIpId() != null) {
                ipIds.add(park.getIpId());
            }
        }
        if (ipIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (ShopTrendyIp ip : trendyIpMapper.selectList(new LambdaQueryWrapper<ShopTrendyIp>()
                .select(ShopTrendyIp::getId, ShopTrendyIp::getIpName)
                .in(ShopTrendyIp::getId, ipIds))) {
            nameById.put(ip.getId(), ip.getIpName());
        }
        for (ShopTrendyPark park : parks) {
            park.setIpName(nameById.get(park.getIpId()));
        }
    }

    /**
     * 回填「名下票种数」：按当页这批 id 一次分组数完，别一行发一趟查询。
     * selectMaps 走 BaseMapper，逻辑删除条件自动带上 ⇒ 已删除的票种不计数（口径同删除拦截那一趟）。
     */
    private void fillTicketCount(List<ShopTrendyPark> parks) {
        if (parks == null || parks.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(parks.size());
        parks.forEach(row -> ids.add(row.getId()));
        Map<Long, Integer> counted = new HashMap<>();
        for (Map<String, Object> one : ticketMapper.selectMaps(new QueryWrapper<ShopTrendyParkTicket>()
                .select("park_id AS parkId", "COUNT(1) AS cnt")
                .in("park_id", ids)
                .groupBy("park_id"))) {
            Object parkId = one.get("parkId");
            Object cnt = one.get("cnt");
            if (parkId != null && cnt != null) {
                counted.put(((Number) parkId).longValue(), ((Number) cnt).intValue());
            }
        }
        parks.forEach(row -> row.setTicketCount(counted.getOrDefault(row.getId(), 0)));
    }

    /**
     * NOT NULL 那几格补默认值（和建表默认一致）：页面没给就等于走库的默认档。
     * 图片那一格不在这里管了 —— 园区名下的照片是 shop_trendy_park_photo 那张表，一张一行。
     */
    private void normalize(ShopTrendyPark park) {
        if (park.getStatus() == null) {
            park.setStatus(STATUS_OPEN);
        }
    }

    /**
     * 名称必填 + 编号判重（excludeId 是修改时排除自己）+ IP 那道存在性校验 + 状态取值
     */
    private void validate(ShopTrendyPark park, Long excludeId) {
        if (StringUtils.isBlank(park.getParkName())) {
            throw new ServiceException("园区名称不能为空");
        }
        if (park.getStatus() != null
                && park.getStatus() != STATUS_OPEN && park.getStatus() != STATUS_CLOSED) {
            throw new ServiceException("状态不合法（1营业中 0停业）");
        }
        // IP 可空（不归属某个IP的园区是有的），填了就必须是库里真有的，否则那一格在列表上永远显示不出来
        if (park.getIpId() != null && trendyIpMapper.selectById(park.getIpId()) == null) {
            throw new ServiceException("所选潮玩IP不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopTrendyPark>()
                .eq(ShopTrendyPark::getParkNo, park.getParkNo())
                .ne(excludeId != null, ShopTrendyPark::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("园区编号「" + park.getParkNo() + "」已存在");
        }
    }
}
