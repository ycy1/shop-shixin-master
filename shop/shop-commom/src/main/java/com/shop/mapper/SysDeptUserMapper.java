package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.SysDeptUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysDeptUserMapper extends BaseMapper<SysDeptUser> {

    /**
     * 批量插入用户部门关联
     */
    void insertDeptUser(@Param("userId") Integer userId, @Param("deptIds") List<Long> deptIds);

    /**
     * 批量插入部门人员（同一部门，多个用户）
     */
    void insertDeptUsers(@Param("deptId") Long deptId, @Param("userIds") List<Integer> userIds);

    /**
     * 根据用户ID删除关联
     */
    void deleteByUserIds(@Param("userIds") List<Integer> userIds);

    /**
     * 根据部门ID删除关联
     */
    void deleteByDeptIds(@Param("deptIds") List<Long> deptIds);
}
