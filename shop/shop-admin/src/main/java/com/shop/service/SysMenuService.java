package com.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysMenu;
import com.shop.vo.menu.RouterVO;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    /**
     * 获取菜单树
     * @param query 查询条件，目前只用 title（菜单名称模糊匹配）
     */
    List<SysMenu> getMenuTree(SysMenu query);
    
    /**
     * 新增菜单
     */
    void addMenu(SysMenu menu);
    
    /**
     * 更新菜单
     */
    void updateMenu(SysMenu menu);
    
    /**
     * 删除菜单
     */
    void deleteMenu(Integer id);

    /**
     * 获取当前登录用户所拥有的菜单
     * @return
     */
    List<RouterVO> getCurrentUserMenu();

}