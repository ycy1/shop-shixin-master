package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopSupplierProduct;

import java.util.List;

/**
 * 供应商商品（供货关系）服务接口
 */
public interface ShopSupplierProductService extends IService<ShopSupplierProduct> {

    /**
     * 分页查询供货关系（供应商账号会被覆盖成只看自己名下那几家供应商的）
     *
     * @param query 查询条件（keyword/supplierId/categoryId/status）；supplierIds 由服务层按登录人覆盖，不认前端传的
     * @return 供货关系分页
     */
    IPage<ShopSupplierProduct> selectPage(ShopSupplierProduct query);

    /**
     * 单条详情（供修改弹窗回显）：除了本表那几列，还按 sku_id 补一次规格文本 ——
     * 规格不在本表上，而修改弹窗那格要显示「商品名 + 规格」，只回一个 id 前端拼不出来
     *
     * @param id 供货关系id
     * @return 供货关系
     */
    ShopSupplierProduct selectRelationById(Long id);

    /**
     * 新增供货关系：商品名称/分类按商品表回填，重复的(供应商, 规格)会被挡掉
     *
     * @param relation 新增参数
     */
    void insert(ShopSupplierProduct relation);

    /**
     * 修改供货关系：供应商/规格这两列定死了一条关系的身份，服务层钉回原值改不了（要换就删了重建），
     * 能改的只有供货参数那几列；设成默认会把同规格下其它行的 is_default 抹回 0
     *
     * @param relation 修改参数
     */
    void update(ShopSupplierProduct relation);

    /**
     * 批量删除（逻辑删除；已进过采购单的历史明细不受影响）
     *
     * @param ids 供货关系id集合
     */
    void deleteByIds(List<Long> ids);
}
