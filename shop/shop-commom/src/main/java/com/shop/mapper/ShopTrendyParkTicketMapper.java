package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopTrendyParkTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 主题公园票种表 Mapper接口
 *
 * 列表走自定义 XML 只为一个关键字同时搜票种名称和编号（同 ShopTrendyParkMapper 的取舍）；
 * 园区名、已出票数不在这里联，由服务层按当页 id 一次查回来（形状同 ShopStoreServiceImpl.fillBrand）。
 * 写的一律走 BaseMapper。
 */
@Mapper
public interface ShopTrendyParkTicketMapper extends BaseMapper<ShopTrendyParkTicket> {

    /**
     * 分页查询票种：keyword 模糊匹配名称/编号，parkId、ticketType、status 精确匹配
     */
    IPage<ShopTrendyParkTicket> selectTicketPage(IPage<ShopTrendyParkTicket> page, @Param("q") ShopTrendyParkTicket query);
}
