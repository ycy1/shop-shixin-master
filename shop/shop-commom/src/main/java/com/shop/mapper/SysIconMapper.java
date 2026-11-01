package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.SysIcon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 图标库表 Mapper接口
 */
@Mapper
public interface SysIconMapper extends BaseMapper<SysIcon> {

    /**
     * 分页查询图标，联表带出所属类目名称
     */
    IPage<SysIcon> selectIconPage(IPage<SysIcon> page, @Param("q") SysIcon query);
}
