package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopStoreInvestment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 门店招商 Mapper接口
 */
@Mapper
public interface ShopStoreInvestmentMapper extends BaseMapper<ShopStoreInvestment> {

    /**
     * 分页查询招商线索：关键字要跨招商编号/申请人/电话三列搜，还要按创建时间区间筛，
     * 条件写在 XML 里（代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopStoreInvestment> selectInvestmentPage(IPage<ShopStoreInvestment> page,
                                                    @Param("q") ShopStoreInvestment query);
}
