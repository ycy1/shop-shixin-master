package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopStoreTraining;
import com.shop.entity.ShopStoreTrainingMember;

import java.util.List;

/**
 * 门店培训 服务接口
 */
public interface ShopStoreTrainingService extends IService<ShopStoreTraining> {

    /**
     * 分页查询培训
     *
     * @param query 查询条件（keyword/trainingType/trainMode/status/startFrom/startTo）
     * @return 培训分页，门店名/讲师名已联表带出，已报名人数按参训人员表现算
     */
    IPage<ShopStoreTraining> selectPage(ShopStoreTraining query);

    /**
     * 单条培训（修改弹窗回显、只读详情弹窗都走它）
     *
     * @param id 培训id
     * @return 培训
     */
    ShopStoreTraining selectTrainingById(Long id);

    /**
     * 按培训取参训人员列表（列表页那颗「培训人员」弹窗），按登记先后排（id 升序）
     *
     * @param trainingId 培训id
     * @return 参训人员，门店名已联表带出
     */
    List<ShopStoreTrainingMember> selectMembers(Long trainingId);

    /**
     * 新增：培训编号不传时系统生成；状态强制落 0 待发布（发布是「修改」里改状态那一步）
     *
     * @param training 培训
     */
    void insert(ShopStoreTraining training);

    /**
     * 修改：状态在这一次整行提交；发布时间和结束时间只由状态第一次流转到 1/4 时服务端补，
     * 请求体里带什么都不认
     *
     * @param training 培训
     */
    void update(ShopStoreTraining training);

    /**
     * 批量逻辑删除：只删 0 待发布 的那几条（这个状态下不可能有报名记录，参训人员表不会被删成孤儿）
     *
     * @param ids 主键集合
     */
    void deleteByIds(List<Long> ids);
}
