package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.SysDept;
import com.shop.vo.user.SysUserVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 筛选部门并统计人数（返回扁平列表，由 Service 构建树）
     * <p>
     * 筛选条件：status / name / code / parentIds（所属部门子树）；
     * 统计：user_count（部门下直接成员数）、leader_name（负责人名称）。
     *
     * @param query 查询条件
     * @return 筛选后的部门列表（含 userCount、leaderName）
     */
    List<SysDept> selectDeptTree(@Param("query") SysDept query);

    /**
     * 分页查询部门下的人员
     *
     * @param page   分页参数
     * @param deptId 部门ID
     * @return 部门下的人员列表
     */
    IPage<SysUserVo> selectDeptUserPage(@Param("page") Page<Object> page, @Param("deptId") Long deptId);
}
