package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopStoreTraining;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 门店培训 Mapper接口
 */
@Mapper
public interface ShopStoreTrainingMapper extends BaseMapper<ShopStoreTraining> {

    /**
     * 分页查询培训：关键字要跨编号/名称/地点三列搜、还要联门店和讲师取名并按参训人数现算，
     * 条件写在 XML 里（代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     *
     * userId 是当前登录账号的 id：额外联一次参训人员表，把"我"在这一场的报名/签到状态带回来
     * （selfSignStatus / selfAttendStatus，前台操作列那两颗按钮判可见用）。只有这一条 SQL 有那一层联表，
     * 详情那条没有 —— 详情弹窗里没有那两颗按钮。
     */
    IPage<ShopStoreTraining> selectTrainingPage(IPage<ShopStoreTraining> page,
                                                @Param("q") ShopStoreTraining query,
                                                @Param("userId") Long userId);

    /**
     * 单条培训（列表那一条 SQL 去掉筛选条件、按 id 取），和分页两条路共用同一套联表与现算口径
     */
    ShopStoreTraining selectTrainingById(@Param("id") Long id);
}
