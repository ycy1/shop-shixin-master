package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpSupplier;

import java.util.List;

/**
 * 供应商 服务接口
 */
public interface ShopErpSupplierService extends IService<ShopErpSupplier> {

    /**
     * 启用状态的供应商下拉选项（不分页，给采购单等表单用；口径同门店/品牌的 options）
     *
     * @return 供应商列表，只带 id/编码/名称这几列前端要用的数据
     */
    List<ShopErpSupplier> listOptions();

    /**
     * 分页查询供应商
     *
     * @param query 查询条件（keyword/status；供应商账号会被覆盖成只看自己）
     * @return 供应商分页
     */
    IPage<ShopErpSupplier> selectPage(ShopErpSupplier query);

    /**
     * 单条供应商详情（供修改弹窗回显，供应商账号只能取自己那条）
     *
     * @param id 供应商id
     * @return 供应商
     */
    ShopErpSupplier selectSupplierById(Long id);

    /**
     * 新增供应商：编码不传由系统生成，user_id 记当前登录账号
     *
     * @param supplier 新增参数
     */
    void insert(ShopErpSupplier supplier);

    /**
     * 修改供应商
     *
     * @param supplier 修改参数
     */
    void update(ShopErpSupplier supplier);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 供应商id集合
     */
    void deleteByIds(List<Long> ids);
}
