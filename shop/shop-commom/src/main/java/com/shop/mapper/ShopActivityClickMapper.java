package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityClick;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动曝光/点击记录 Mapper接口
 *
 * 写入只有前台埋点那一条路（ShopActivityTrackServiceImpl），用的就是 BaseMapper 的 insert；
 * 读是后台「查看记录」那颗按钮的明细，要按 user_id 联 sys_user 带出昵称，
 * 条件写在 XML 里（代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）。
 *
 * 活动列表那三格统计不归这里：那是 ShopActivityMapper.xml 里按本表 COUNT 的子查询。
 */
@Mapper
public interface ShopActivityClickMapper extends BaseMapper<ShopActivityClick> {

    /**
     * 分页查某一条活动的曝光/点击明细（按事件类型可选筛，时间倒序）
     */
    IPage<ShopActivityClick> selectRecordPage(IPage<ShopActivityClick> page, @Param("q") ShopActivityClick query);
}
