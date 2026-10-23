package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopDeliveryCompany;
import com.shop.entity.ShopDeliveryRider;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopDeliveryCompanyMapper;
import com.shop.mapper.ShopDeliveryRiderMapper;
import com.shop.service.ShopDeliveryCompanyService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 物流公司表 服务实现（写法照 shop_brand：编码/名称唯一；分页走自定义 XML，同 shop_delivery_rider；
 * 但它是全局共享数据，user_id 只留痕，不参与任何过滤和归属校验）
 */
@Service
@RequiredArgsConstructor
public class ShopDeliveryCompanyServiceImpl
        extends ServiceImpl<ShopDeliveryCompanyMapper, ShopDeliveryCompany>
        implements ShopDeliveryCompanyService {

    /** 支持服务筛选的三个 key，要和 ShopDeliveryCompanyMapper.xml 里判的那三个一一对应 */
    private static final List<String> SERVICE_KEYS = Arrays.asList("cod", "insurance", "fragile");

    private final ShopDeliveryRiderMapper shopDeliveryRiderMapper;

    @Override
    public IPage<ShopDeliveryCompany> selectPage(ShopDeliveryCompany query) {
        if (query == null) {
            query = new ShopDeliveryCompany();
        }
        // 全局共享数据，不按创建人/账号类型收窄（口径同分类、图标库）：user_id 只留痕，不参与过滤
        query.setSupportServices(normalizeServices(query.getSupportServices()));
        // 关键字搜三列、支持服务是一串"任一支持"的 OR，写条件用 XML 比 wrapper 嵌套清楚；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectCompanyPage(PageUtil.getPage(), query);
    }

    @Override
    public List<ShopDeliveryCompany> listOptions() {
        return list(new LambdaQueryWrapper<ShopDeliveryCompany>()
                .eq(ShopDeliveryCompany::getStatus, 1)
                .orderByAsc(ShopDeliveryCompany::getSort)
                .orderByAsc(ShopDeliveryCompany::getId));
    }

    @Override
    public void insert(ShopDeliveryCompany company) {
        // 新增时表单那栏是禁用的、不会传编码，和门店编码一样由系统生成
        if (StringUtils.isBlank(company.getCompanyCode())) {
            company.setCompanyCode(GeneratorNoUtils.getLogisticsNo());
        }
        validate(company, null);
        normalize(company);
        company.setUserId(StpUtil.getLoginIdAsLong());
        try {
            save(company);
        } catch (DuplicateKeyException e) {
            // 逻辑删除的行仍然占着 uk_company_code / uk_company_name，上面的前置查重（只查未删除的）看不到它
            throw new ServiceException("物流公司「" + company.getCompanyName() + "」或编码「" + company.getCompanyCode()
                    + "」已被占用，可能来自一个已删除的物流公司");
        }
    }

    @Override
    public void update(ShopDeliveryCompany company) {
        if (company.getId() == null) {
            throw new ServiceException("物流公司ID不能为空");
        }
        ShopDeliveryCompany exists = getById(company.getId());
        if (exists == null) {
            throw new ServiceException("物流公司不存在");
        }
        // 编码/名称允许不传，不传时沿用原值参与查重（not_empty 策略下不传本来也不会写这一列）
        if (StringUtils.isBlank(company.getCompanyCode())) {
            company.setCompanyCode(exists.getCompanyCode());
        }
        if (StringUtils.isBlank(company.getCompanyName())) {
            company.setCompanyName(exists.getCompanyName());
        }
        validate(company, company.getId());
        normalize(company);
        try {
            updateById(company);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("物流公司「" + company.getCompanyName() + "」或编码「" + company.getCompanyCode()
                    + "」已被占用，可能来自一个已删除的物流公司");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // selectCount 会自动过滤已删除的送货员，所以删掉的送货员不会拦住删除物流公司
        Long used = shopDeliveryRiderMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryRider>()
                .in(ShopDeliveryRider::getLogisticsCompanyId, ids));
        if (used != null && used > 0) {
            throw new ServiceException("该物流公司下还有 " + used + " 个送货员，请先在送货员里取消关联或删除送货员");
        }
        removeByIds(ids);
    }

    /**
     * 支持服务筛选参数是前端拼的逗号串（axios 默认会把数组序列化成 supportServices[]，Spring 命令对象接不上，所以拼成串）。
     * 这里只留下 XML 里认的那三个 key 并去重；一个都不认识时回 null，让 XML 那一整块条件跳过，
     * 否则会拼出空的 "AND ()"。
     */
    private String normalizeServices(String supportServices) {
        if (StringUtils.isBlank(supportServices)) {
            return null;
        }
        List<String> known = new ArrayList<>();
        for (String item : supportServices.split(",")) {
            String service = StringUtils.trimToEmpty(item);
            if (SERVICE_KEYS.contains(service) && !known.contains(service)) {
                known.add(service);
            }
        }
        return known.isEmpty() ? null : String.join(",", known);
    }

    /**
     * NOT NULL 且带默认值的列：前端不传（表单没填或被 JSON 丢掉 undefined）时补上库里的默认值，
     * 否则 INSERT 会显式带上 NULL 而报"不能为空"。
     */
    private void normalize(ShopDeliveryCompany company) {
        if (company.getCompanyType() == null) {
            company.setCompanyType(1);
        }
        if (company.getSupportCod() == null) {
            company.setSupportCod(0);
        }
        if (company.getSupportInsurance() == null) {
            company.setSupportInsurance(0);
        }
        if (company.getSupportFragile() == null) {
            company.setSupportFragile(0);
        }
        if (company.getSort() == null) {
            company.setSort(0);
        }
        if (company.getStatus() == null) {
            company.setStatus(1);
        }
    }

    private void validate(ShopDeliveryCompany company, Long excludeId) {
        if (StringUtils.isBlank(company.getCompanyCode())) {
            throw new ServiceException("物流公司编码不能为空");
        }
        if (StringUtils.isBlank(company.getCompanyName())) {
            throw new ServiceException("物流公司名称不能为空");
        }
        company.setCompanyCode(company.getCompanyCode().trim());
        company.setCompanyName(company.getCompanyName().trim());
        Long codeCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryCompany>()
                .eq(ShopDeliveryCompany::getCompanyCode, company.getCompanyCode())
                .ne(excludeId != null, ShopDeliveryCompany::getId, excludeId));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException("物流公司编码「" + company.getCompanyCode() + "」已存在");
        }
        Long nameCount = baseMapper.selectCount(new LambdaQueryWrapper<ShopDeliveryCompany>()
                .eq(ShopDeliveryCompany::getCompanyName, company.getCompanyName())
                .ne(excludeId != null, ShopDeliveryCompany::getId, excludeId));
        if (nameCount != null && nameCount > 0) {
            throw new ServiceException("物流公司「" + company.getCompanyName() + "」已存在");
        }
    }
}
