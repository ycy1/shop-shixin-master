package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpWarehouse;

import java.util.List;

/**
 * 仓库 服务接口
 */
public interface ShopErpWarehouseService extends IService<ShopErpWarehouse> {

    /**
     * 启用状态的仓库下拉选项（不分页，给商品入库等表单用；口径同供应商/门店的 options）
     *
     * @return 仓库列表，只带 id/编码/名称这几列前端要用的数据
     */
    List<ShopErpWarehouse> listOptions();

    /**
     * 分页查询仓库
     *
     * @param query 查询条件（keyword/warehouseType/status/storeId）
     * @return 仓库分页
     */
    IPage<ShopErpWarehouse> selectPage(ShopErpWarehouse query);

    /**
     * 单条仓库详情（供修改弹窗回显，带关联门店名称和操作员昵称）
     *
     * @param id 仓库id
     * @return 仓库
     */
    ShopErpWarehouse selectWarehouseById(Long id);

    /**
     * 新增仓库：编码不传由系统生成
     *
     * @param warehouse 新增参数
     */
    void insert(ShopErpWarehouse warehouse);

    /**
     * 修改仓库
     *
     * @param warehouse 修改参数
     */
    void update(ShopErpWarehouse warehouse);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 仓库id集合
     */
    void deleteByIds(List<Long> ids);
}
