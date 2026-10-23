package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.Constants;
import com.shop.entity.SysMenu;
import com.shop.enums.MenuTypeEnum;
import com.shop.mapper.SysMenuMapper;
import com.shop.service.SysMenuService;
import com.shop.vo.menu.RouterVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public List<SysMenu> getMenuTree(SysMenu query) {
        String title = query == null ? null : query.getTitle();
        List<SysMenu> menus = list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        // 父级不留：命中的那一条会被 buildMenuTree 顶到第一层，收起着也能看见
        if (StringUtils.isNotBlank(title)) {
            menus = keepMatchedWithSubtree(menus, title);
        }
        return buildMenuTree(menus);
    }

    /**
     * 留下菜单名称命中的那些行，外加它们名下的整棵子树（子树不参与匹配，是为了展开还能看到下级）
     */
    private List<SysMenu> keepMatchedWithSubtree(List<SysMenu> menus, String keyword) {
        Map<Integer, List<SysMenu>> childrenMap = new HashMap<>();
        for (SysMenu menu : menus) {
            if (menu.getParentId() != null && menu.getParentId() != 0) {
                childrenMap.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
            }
        }

        Set<Integer> keptIds = new HashSet<>();
        Deque<Integer> pending = new ArrayDeque<>();
        menus.stream()
                .filter(menu -> StringUtils.containsIgnoreCase(menu.getTitle(), keyword))
                .forEach(menu -> pending.add(menu.getId()));
        while (!pending.isEmpty()) {
            Integer id = pending.poll();
            if (!keptIds.add(id)) {
                continue;
            }
            List<SysMenu> children = childrenMap.get(id);
            if (children != null) {
                children.forEach(child -> pending.add(child.getId()));
            }
        }
        return menus.stream().filter(menu -> keptIds.contains(menu.getId())).collect(Collectors.toList());
    }

    /**
     * 根据扁平菜单列表构建树形结构（根节点：无父节点或父节点不在当前结果集中）
     * 后者是筛选要的后果：搜「销售单」时上级「进销存管理」没命中，命中的这一行得顶到第一层才看得到
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, List<SysMenu>> childrenMap = menus.stream()
                .filter(menu -> menu.getParentId() != null && menu.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        menus.forEach(menu -> menu.setChildren(childrenMap.get(menu.getId())));

        Set<Integer> keptIds = menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        return menus.stream()
                .filter(menu -> menu.getParentId() == null || menu.getParentId() == 0
                        || !keptIds.contains(menu.getParentId()))
                .collect(Collectors.toList());
    }


    @Override
    public void addMenu(SysMenu menu) {
        if (menu.getType().equals(MenuTypeEnum.CATALOG)) {
            menu.setComponent("Layout");
        }
        save(menu);
    }

    @Override
    public void updateMenu(SysMenu menu) {
        if (menu.getType().equals(MenuTypeEnum.CATALOG)) {
            menu.setComponent("Layout");
        }
        updateById(menu);
    }

    @Override
    public void deleteMenu(Integer id) {
        // 检查是否有子菜单
        if (count(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id)) > 0) {
            throw new RuntimeException("存在子菜单，不能删除");
        }
        removeById(id);
    }

    @Override
    public List<RouterVO> getCurrentUserMenu() {

        List<SysMenu> menus;
        if (StpUtil.hasRole(Constants.ADMIN)) {
            menus = baseMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .ne(SysMenu::getType,MenuTypeEnum.BUTTON.getCode()));
        }else {
            menus = baseMapper.getMenusByUserId(StpUtil.getLoginIdAsInt(),MenuTypeEnum.BUTTON.getCode());
        }

        return this.buildRouterTree(menus);
    }


    public List<RouterVO> buildRouterTree(List<SysMenu> menus) {
        List<RouterVO> resultList = new ArrayList<>();
        for (SysMenu menu : menus) {
            Integer parentId = menu.getParentId();
            if ( parentId == null || parentId == 0) {
                RouterVO.MetaVO metaVO = new RouterVO.MetaVO(menu.getTitle(),menu.getIcon(),menu.getHidden(),menu.getIsExternal());
                RouterVO build = RouterVO.builder().id(menu.getId()).path(menu.getPath()).redirect(menu.getRedirect()).name(menu.getName()).component(menu.getComponent())
                        .meta(metaVO).sort(menu.getSort()).build();
                resultList.add(build);
            }
        }
        resultList.sort(Comparator.comparingInt(RouterVO::getSort));

        for (RouterVO routerVO : resultList) {
            routerVO.setChildren(getRouterChild(routerVO.getId(),menus));
        }
        return resultList;
    }

    private List<RouterVO> getRouterChild(Integer pid , List<SysMenu> menus){
        if (menus == null) {
            return Collections.emptyList();
        }
        Map<Integer, RouterVO> routerMap = new HashMap<>();
        for (SysMenu e: menus) {
            Integer parentId = e.getParentId();
            if(parentId != null && parentId.equals(pid)){
                // 子菜单的下级菜单
                RouterVO.MetaVO metaVO = new RouterVO.MetaVO(e.getTitle(),e.getIcon(),e.getHidden(),e.getIsExternal());
                RouterVO routerVO = RouterVO.builder()
                        .id(e.getId())
                        .path(e.getPath())
                        .redirect(e.getRedirect())
                        .name(e.getName())
                        .component(e.getComponent())
                        .meta(metaVO).sort(e.getSort()).build();

                if (StringUtils.isEmpty(e.getComponent()) && isParentView(e)){
                    routerVO.setComponent(Constants.PARENT_VIEW);
                }
                routerMap.put(e.getId(), routerVO);
            }
        }

        List<RouterVO> childrens = new ArrayList<>(routerMap.values());
        childrens.sort(Comparator.comparingInt(RouterVO::getSort));

        for (RouterVO e : childrens) {
            e.setChildren(getRouterChild(e.getId(), menus));
        }

        return childrens.isEmpty() ? Collections.emptyList() : childrens;
    }

    /**
     * 是否为parent_view组件
     *
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean isParentView(SysMenu menu)
    {
        return menu.getParentId() != 0 && MenuTypeEnum.MENU.equals(menu.getType());
    }
} 