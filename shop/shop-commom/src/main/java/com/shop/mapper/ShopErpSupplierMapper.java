package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpSupplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 供应商 Mapper接口
 */
@Mapper
public interface ShopErpSupplierMapper extends BaseMapper<ShopErpSupplier> {

    /**
     * 分页查询供应商：关键字要跨名称/编码/联系人/电话四列搜，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpSupplier> selectSupplierPage(IPage<ShopErpSupplier> page, @Param("q") ShopErpSupplier query);
}
