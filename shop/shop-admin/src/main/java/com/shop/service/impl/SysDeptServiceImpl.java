package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.SysDept;
import com.shop.entity.SysDeptUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysDeptMapper;
import com.shop.mapper.SysDeptUserMapper;
import com.shop.service.SysDeptService;
import com.shop.utils.PageUtil;
import com.shop.vo.user.SysUserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final SysDeptUserMapper deptUserMapper;

    @Override
    public List<SysDept> listDeptTree(SysDept query) {
        if (query == null) {
            query = new SysDept();
        }
        // 筛选与人数统计均在 XML（selectDeptTree）中完成，这里只负责构建树形结构
        List<SysDept> depts = baseMapper.selectDeptTree(query);
        return buildDeptTree(depts);
    }

    /**
     * 根据扁平部门列表构建树形结构（根节点：无父节点或父节点不在当前结果集中）
     */
    private List<SysDept> buildDeptTree(List<SysDept> depts) {
        if (depts == null || depts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<SysDept>> childrenMap = depts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .collect(Collectors.groupingBy(SysDept::getParentId));
        depts.forEach(d -> d.setChildren(childrenMap.getOrDefault(d.getId(), Collections.emptyList())));
        Set<Long> keptIds = depts.stream().map(SysDept::getId).collect(Collectors.toSet());
        return depts.stream()
                .filter(d -> d.getParentId() == null || d.getParentId() == 0 || !keptIds.contains(d.getParentId()))
                .collect(Collectors.toList());
    }

    @Override
    public void addDept(SysDept dept) {
        if (StringUtils.isBlank(dept.getName())) {
            throw new ServiceException("部门名称不能为空");
        }
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        if (dept.getParentId() != 0 && getById(dept.getParentId()) == null) {
            throw new ServiceException("父部门不存在");
        }
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        save(dept);
    }

    @Override
    public void updateDept(SysDept dept) {
        if (dept.getId() == null || getById(dept.getId()) == null) {
            throw new ServiceException("部门不存在");
        }
        if (dept.getParentId() != null && dept.getParentId().equals(dept.getId())) {
            throw new ServiceException("父部门不能是自己");
        }
        if (dept.getParentId() != null && dept.getParentId() != 0) {
            if (getById(dept.getParentId()) == null) {
                throw new ServiceException("父部门不存在");
            }
            // 检查是否形成循环引用
            Long pid = dept.getParentId();
            Set<Long> visited = new HashSet<>();
            while (pid != null && pid != 0) {
                if (pid.equals(dept.getId())) {
                    throw new ServiceException("父部门不能是其子部门");
                }
                if (!visited.add(pid)) {
                    break;
                }
                SysDept parent = getById(pid);
                if (parent == null) break;
                pid = parent.getParentId();
            }
        }
        updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            if (count(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id)) > 0) {
                throw new ServiceException("存在子部门，不能删除");
            }
            if (deptUserMapper.selectCount(new LambdaQueryWrapper<SysDeptUser>().eq(SysDeptUser::getDeptId, id)) > 0) {
                throw new ServiceException("部门下存在用户，不能删除");
            }
        }
        removeBatchByIds(ids);
        deptUserMapper.deleteByDeptIds(ids);
    }

    @Override
    public IPage<SysUserVo> getDeptUserPage(Long deptId) {
        return baseMapper.selectDeptUserPage(PageUtil.getPage(), deptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDeptUsers(Long deptId, List<Integer> userIds) {
        if (deptId == null || getById(deptId) == null) {
            throw new ServiceException("部门不存在");
        }
        if (userIds == null || userIds.isEmpty()) {
            throw new ServiceException("请选择要添加的人员");
        }
        // 过滤已在部门中的用户，避免唯一键冲突
        List<Integer> existingIds = deptUserMapper.selectList(new LambdaQueryWrapper<SysDeptUser>()
                        .eq(SysDeptUser::getDeptId, deptId)
                        .in(SysDeptUser::getUserId, userIds))
                .stream()
                .map(SysDeptUser::getUserId)
                .collect(Collectors.toList());
        List<Integer> toAdd = userIds.stream()
                .distinct()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toList());
        if (!toAdd.isEmpty()) {
            deptUserMapper.insertDeptUsers(deptId, toAdd);
        }
    }
}
