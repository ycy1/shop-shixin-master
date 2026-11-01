package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopTrendyCollectRecord;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectProgressVo;
import com.shop.vo.trendy.ShopTrendyCollectionViewVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 潮玩系列集齐领取记录 Mapper接口
 *
 * 读的方向一共三条：图鉴那一页的系列列表（带我的进度）、后台「集齐进度」弹窗的分页，
 * 和它下面点开一个人的款式明细；外加领取那一步要的一句判定。
 * 写的一律走 BaseMapper：这张表唯一的写入方是页面上那颗「领取奖励」（ ShopTrendyCollectRewardService#claim ）。
 */
@Mapper
public interface ShopTrendyCollectRecordMapper extends BaseMapper<ShopTrendyCollectRecord> {

    /**
     * 领取那一步的判定：这一条系列上，这个人的奖励该不该发（够线 + 奖励配齐 + 还没领过）。
     *
     * 规格种类数和达标线在同一条 SQL 里比完（HAVING），回得来的一行就是能直接插进本表的那一行
     *（trendyId / userId / skuCount / rewardType / rewardValue 五格都齐了，服务层只补 collect_time）——
     * 读那一页可以报「差几格」，判能不能领不能拿页面那一格当数，两边必须各查各的。
     */
    ShopTrendyCollectRecord selectCollectHitOne(@Param("trendyId") Long trendyId, @Param("userId") Long userId);

    /**
     * 图鉴那一页（前台 /trendy/collection）：一行一条潮玩系列，带这条系列的规格总数、我凑到几格、
     * 集齐奖励配置和那一笔领取记录。打头的是 shop_trendy，所以一条没抽过的系列也在场（图鉴要看的是整套）。
     *
     * @param userId 登录人；没登录传 NULL —— 分子和领取记录那两条 LEFT JOIN 都不命中，
     *               页面念成「0 / N」，图鉴照样逛
     */
    IPage<ShopTrendyCollectionViewVo> selectCollectionPage(IPage<ShopTrendyCollectionViewVo> page,
                                                           @Param("userId") Long userId);

    /**
     * 后台「集齐进度」那一弹窗的分页：一条系列上，抽过它名下商品规格的人一人一行
     *（手上凑到几个 / 这套一共几个 / 那一笔奖励领过没有）。
     *
     * 只有「凑到几个」是按抽取记录现数的那一列，其余几格分别是从 sys_user 和领取记录那两行上联出来的；
     * 这条读的是"看"的口径、不是"发"的口径 —— 判要不要发仍然走上面那一条，两边不共用一套谓词。
     */
    IPage<ShopTrendyCollectProgressVo> selectCollectProgress(IPage<ShopTrendyCollectProgressVo> page,
                                                             @Param("trendyId") Long trendyId,
                                                             @Param("keyword") String keyword);

    /**
     * 点开弹窗里某一个人之后的那一排格子：这条系列下的<b>全部</b>规格，一格上标出他抽到过没有
     *（drawnTime 是 NULL 就是还缺这一格）。
     *
     * 不分页：一条系列名下撑死几十格规格，一次全带（同签到日历那一条「一个月最多 31 行，一次全带」的口径）。
     */
    List<ShopTrendyCollectItemVo> selectCollectItems(@Param("trendyId") Long trendyId,
                                                     @Param("userId") Long userId);
}
