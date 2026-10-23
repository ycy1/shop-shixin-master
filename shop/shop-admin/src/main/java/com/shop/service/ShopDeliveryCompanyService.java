package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopDeliveryCompany;

import java.util.List;

/**
 * 物流公司表 服务类
 */
public interface ShopDeliveryCompanyService extends IService<ShopDeliveryCompany> {

    /**
     * 分页查询物流公司
     *
     * @param query keyword 模糊匹配名称/编码/简称，companyType、status 精确匹配，
     *              supportServices 是逗号分隔的 cod/insurance/fragile，勾中的几项支持任意一项即命中；
     *              全局共享数据，不按创建人/账号类型收窄（user_id 只留痕）
     */
    IPage<ShopDeliveryCompany> selectPage(ShopDeliveryCompany query);

    /**
     * 查询启用状态的物流公司，供表单下拉使用（不分页，按 sort 升序）
     */
    List<ShopDeliveryCompany> listOptions();

    /**
     * 新增物流公司，company_code 为空时由系统生成
     */
    void insert(ShopDeliveryCompany company);

    /**
     * 修改物流公司
     */
    void update(ShopDeliveryCompany company);

    /**
     * 批量删除物流公司（逻辑删除）
     */
    void deleteByIds(List<Long> ids);
}
