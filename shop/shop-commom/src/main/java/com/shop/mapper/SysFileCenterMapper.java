package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.dto.file.SysFileCenterQueryDto;
import com.shop.entity.SysFileCenter;
import com.shop.vo.file.SysFileCenterVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文件中心 Mapper接口
 */
@Mapper
public interface SysFileCenterMapper extends BaseMapper<SysFileCenter> {

    /**
     * 分页查询（含所属用户信息与各Tab作用域）
     */
    IPage<SysFileCenterVo> selectFileCenterPage(Page<?> page, @Param("q") SysFileCenterQueryDto q);

    /**
     * 判断文件属主与当前用户是否有共同部门
     */
    int countSharedDept(@Param("fileOwnerId") Long fileOwnerId, @Param("userId") Long userId);

    /**
     * 判断文件属主与当前用户是否有共同角色
     */
    int countSharedRole(@Param("fileOwnerId") Long fileOwnerId, @Param("userId") Long userId);
}
