package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动 Mapper接口
 */
@Mapper
public interface ShopActivityMapper extends BaseMapper<ShopActivity> {

    /**
     * 分页查询活动：关键字要跨名称/标题两列搜、还要联门店取名并按曝光/点击记录表现算那三格统计，
     * 条件写在 XML 里（代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopActivity> selectActivityPage(IPage<ShopActivity> page, @Param("q") ShopActivity query);

    /**
     * 单条活动（列表那一条 SQL 去掉筛选条件、按 id 取），和分页两条路共用同一套联表与现算口径
     */
    ShopActivity selectActivityById(@Param("id") Long id);
}
