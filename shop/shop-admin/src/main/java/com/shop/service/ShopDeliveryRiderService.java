package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopDeliveryRider;

import java.util.List;

/**
 * 送货员表 服务类
 */
public interface ShopDeliveryRiderService extends IService<ShopDeliveryRider> {

    /**
     * 分页查询送货员
     *
     * @param query keyword 模糊匹配姓名/编码/手机号，logisticsCompanyId、vehicleType、onlineStatus、workStatus、status 精确匹配。
     *              不按账号类型收窄：表上的 store_id 已经删了，送货员只剩物流公司一条归属线，全平台的人对后台账号都可见
     */
    IPage<ShopDeliveryRider> selectPage(ShopDeliveryRider query);

    /**
     * 启用中的送货员选项（不分页，供配送单表单下拉使用）；同样不按门店收窄
     */
    List<ShopDeliveryRider> listOptions();

    /**
     * 按 id 查询送货员，并回填物流公司名和账号昵称
     */
    ShopDeliveryRider detail(Long id);

    /**
     * 当前登录账号自己的送货员档案（按 user_id 命中）；没建档案时返回 null，由前端提示
     */
    ShopDeliveryRider getMyInfo();

    /**
     * 送货员本人修改自己那行档案：只接受头像/姓名/手机号/车牌号/服务城市/服务区域、常驻地点（地址＋经纬度）、
     * 身份证号/正反面照/健康证图/健康证到期日，以及交通工具/在线状态/工作状态；
     * 启用状态、最大接单数、归属（物流公司）与账号、统计这些一律以库里的原值为准
     */
    void updateMyInfo(ShopDeliveryRider rider);

    /**
     * 新增送货员
     */
    void insert(ShopDeliveryRider rider);

    /**
     * 修改送货员
     */
    void update(ShopDeliveryRider rider);

    /**
     * 批量删除送货员
     */
    void deleteByIds(List<Long> ids);
}
