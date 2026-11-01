package com.shop.api.service;

import java.util.List;

/**
 * 首页搜索框下那一排热搜（只有 Web 前台一个使用方，所以接口挂在 /api/web 下面，不放 comm）
 *
 * 这份和 ShopSearchReadService 分开：那一条是查库的读链路，这一条一行 SQL 都不发，
 * 读写都在 Redis 一个 ZSet 里 —— 混进去会让那个「Read」的名字说谎。
 *
 * 为什么用 Redis 而不是建一张表：热搜是一个会过期的统计口径（最近谁搜得多），不是单据；
 * 每次搜索往库里插一行、再 GROUP BY 现算，代价全花在展示一排词上。活动埋点那种要落表是因为
 * 后台得看同一张流水，热搜没有这个要求。
 */
public interface ShopSearchHotService {

    /**
     * 当前热搜（按被搜次数从多到少，最多 10 条）。Redis 里还没人搜过就回空列表，不报错 ——
     * 首页那一排没词就是不出现，跟「接口挂了」是两回事，由页面各自决定怎么说。
     */
    List<String> listHot();

    /**
     * 记一次搜索：这个关键字加分 +1，超过上限的尾巴裁掉。
     *
     * 关键字不合法（空的、纯空格、长得不是人打的）就直接返回，不报错也不回话 ——
     * 这一条是埋点性质的写，一次脏输入不该把搜索页打断。
     */
    void record(String keyword);
}
