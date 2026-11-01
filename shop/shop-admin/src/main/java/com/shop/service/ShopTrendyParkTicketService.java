package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendyParkTicket;

import java.util.List;

/**
 * 主题公园票种 服务类
 */
public interface ShopTrendyParkTicketService extends IService<ShopTrendyParkTicket> {

    /**
     * 分页查询票种
     *
     * @param query keyword 模糊匹配名称/编号，parkId、ticketType、status 精确匹配
     */
    IPage<ShopTrendyParkTicket> selectPage(ShopTrendyParkTicket query);

    /**
     * 上架中的票种选项（不分页，供「用户票种」出票表单那格下拉使用）
     *
     * @param parkId 传了就只给这一家园区的票种（选了园区之后把候选收窄到那一家）
     */
    List<ShopTrendyParkTicket> listOptions(Long parkId);

    /**
     * 按 id 查询票种详情
     */
    ShopTrendyParkTicket detail(Long id);

    /**
     * 新增票种（编号由服务端生成）
     */
    void insert(ShopTrendyParkTicket ticket);

    /**
     * 修改票种
     */
    void update(ShopTrendyParkTicket ticket);

    /**
     * 批量删除票种（已经出出去过票的拦住，否则那些用户票指向一个不存在的票种）
     */
    void deleteByIds(List<Long> ids);
}
