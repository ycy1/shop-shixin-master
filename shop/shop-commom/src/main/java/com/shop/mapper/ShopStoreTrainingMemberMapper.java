package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopStoreTrainingMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 培训参训人员 Mapper接口
 */
@Mapper
public interface ShopStoreTrainingMemberMapper extends BaseMapper<ShopStoreTrainingMember> {

    /**
     * 按培训取参训人员（列表页那颗「培训人员」弹窗）：门店名联表现取，
     * 姓名/电话用的是这一行自己的快照列
     */
    List<ShopStoreTrainingMember> selectMemberByTrainingId(@Param("trainingId") Long trainingId);
}
