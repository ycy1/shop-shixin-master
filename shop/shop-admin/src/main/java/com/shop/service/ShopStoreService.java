package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopStore;

import java.util.List;

/**
 * 门店表 服务类
 */
public interface ShopStoreService extends IService<ShopStore> {

    /**
     * 分页查询门店
     *
     * @param query storeName 作为模糊关键字，storeType、businessStatus、status 精确匹配
     */
    IPage<ShopStore> selectPage(ShopStore query);

    /**
     * 启用中的门店选项（不分页，供商品表单下拉使用）；门店账号只给名下的店
     */
    List<ShopStore> listOptions();

    /**
     * 按 id 查询门店，并回填店长昵称
     */
    ShopStore detail(Long id);

    /**
     * 当前登录用户担任店长的门店；一个账号可以管多家店，所以是列表。
     * 不是门店用户（店长）时返回空列表。
     */
    List<ShopStore> myStores();

    /**
     * 当前登录账号名下的门店ID，口径和门店列表的数据范围一致：自己创建的 OR 自己当店长的。
     * 送货员列表、配送单列表按账号类型收窄时要拿这组ID去过滤 store_id
     * （不用 myStores()：那个只按 manager_id 算，会漏掉自己新建的店）。
     */
    List<Long> myStoreIds();

    /**
     * 这家店在不在当前登录账号的数据范围内，口径同 myStoreIds()（创建 OR 当店长）。
     * 给写操作按单个 store_id 判归属用，省得先把名下门店整批查出来。
     * 不是门店账号时恒为 true（不收窄）；storeId 为 null（如没归属门店的平台送货员）算不在范围内。
     */
    boolean inMyStores(Long storeId);

    /**
     * 新增门店
     */
    void insert(ShopStore store);

    /**
     * 修改门店
     */
    void update(ShopStore store);

    /**
     * 批量删除门店
     */
    void deleteByIds(List<Long> ids);
}
