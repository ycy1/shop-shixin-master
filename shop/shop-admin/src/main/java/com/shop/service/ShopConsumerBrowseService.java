package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.dto.browse.ShopBrowseSaveDto;
import com.shop.entity.ShopConsumerBrowse;
import com.shop.vo.browse.ShopBrowseVo;

import java.util.List;

/**
 * 浏览足迹 服务接口
 */
public interface ShopConsumerBrowseService extends IService<ShopConsumerBrowse> {

    /**
     * 分页查询足迹（联表带出商品名/主图/单价/分类名与消费者昵称，按最近浏览时间倒序）
     *
     * @param query 查询条件（keyword/userId/时间范围）
     * @return 足迹分页
     */
    IPage<ShopBrowseVo> selectPage(ShopBrowseQueryDto query);

    /**
     * 单条足迹详情（供修改弹窗回显）
     *
     * @param id 足迹记录id
     * @return 足迹视图对象
     */
    ShopBrowseVo selectBrowseById(Long id);

    /**
     * 记一次浏览：同一消费者同一商品已有足迹时只把 browse_time 刷成最新（uk_user_product 一人一商品一条），
     * 没有才新增
     *
     * @param form 新增参数
     */
    void insert(ShopBrowseSaveDto form);

    /**
     * 修改足迹：换消费者或换商品都可能和另一条撞成同一对，撞了就报错让你去改那一条
     *
     * @param form 修改参数
     */
    void update(ShopBrowseSaveDto form);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids 记录id集合
     */
    void deleteByIds(List<Long> ids);
}
