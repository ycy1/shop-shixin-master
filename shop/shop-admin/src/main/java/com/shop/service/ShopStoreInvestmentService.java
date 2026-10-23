package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopStoreInvestment;

import java.util.List;

/**
 * 门店招商 服务接口
 */
public interface ShopStoreInvestmentService extends IService<ShopStoreInvestment> {

    /**
     * 分页查询招商线索
     *
     * @param query 查询条件（keyword/intentType/source/status/beginTime/endTime）
     * @return 招商线索分页，专员昵称已按 user_id 回填
     */
    IPage<ShopStoreInvestment> selectPage(ShopStoreInvestment query);

    /**
     * 单条招商详情（修改弹窗回显、只读详情弹窗都走它）
     *
     * @param id 招商id
     * @return 招商线索
     */
    ShopStoreInvestment selectInvestmentById(Long id);

    /**
     * 按门店取它来源那条招商线索（门店列表的「招商信息」弹窗用）。
     * 关联记在 shop_store.investment_id 上（建店那一次写入），所以这里是一反查、不联表。
     *
     * @param storeId 门店id
     * @return 招商线索；这家店不是招商来的（investment_id 为空）时返回 null
     */
    ShopStoreInvestment selectByStoreId(Long storeId);

    /**
     * 招商线索候选（门店表单那格「招商信息」下拉用，不分页）。
     * 只给还没被任何门店占用的那几条，一条线索只能建一家门店；状态只放行 0 待审核 / 1 已签约；
     * 传 storeId 时把这家门店自己已经绑的那条也放回来，否则修改弹窗回显成一个空下拉。
     *
     * @param storeId 正在编辑的门店id，新增门店时传 null
     * @return 候选线索（只带下拉要用的那几列：编号/申请人/状态）
     */
    List<ShopStoreInvestment> listOptions(Long storeId);

    /**
     * 新增：招商编号不传时系统生成；签约时间/过期时间都是表单必填项，由 validate 把关
     *
     * @param investment 招商线索
     */
    void insert(ShopStoreInvestment investment);

    /**
     * 修改：任何状态都能改（签约文件、过期时间这些在签约之后还要补录）；
     * 签约时间也是普通表单字段，改得动，服务端不插手
     *
     * @param investment 招商线索
     */
    void update(ShopStoreInvestment investment);

    /**
     * 批量逻辑删除：只删 0 待审核 的那几条，别的状态报错了事
     *
     * @param ids 主键集合
     */
    void deleteByIds(List<Long> ids);
}
