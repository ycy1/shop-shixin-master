package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopTrendyParkPhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 园区照片表 Mapper接口
 *
 * 列表走自定义 XML 只为一个关键字同时搜描述和标签（同票种/园区那两条的取舍）；
 * 园区名、上传人不在这里联，由服务层按当页 id 一次查回来（形状同 ShopTrendyParkTicketServiceImpl.fillParkName）。
 * 按园区捞那一路（前台图集、后台下拉）用的是 BaseMapper 的 wrapper，不另开一条 SQL。
 */
@Mapper
public interface ShopTrendyParkPhotoMapper extends BaseMapper<ShopTrendyParkPhoto> {

    /**
     * 分页查询照片：parkId、ipId、sourceType、status、isFeatured 精确匹配，keyword 模糊匹配描述/标签
     */
    IPage<ShopTrendyParkPhoto> selectPhotoPage(IPage<ShopTrendyParkPhoto> page, @Param("q") ShopTrendyParkPhoto query);

    /**
     * 审核那一句专用 UPDATE：一次把 status 和 audit_msg 两格写掉，别的列一个都不碰。
     *
     * 单开一句、不走实体修改，两处必要的别扭都得绕：
     * 1) audit_msg 要能写成 NULL（上次拒绝留了原因、这次通过没原因，那句得清掉），
     *    而全局 field-strategy=not_empty 会把 null 和空串一起跳过 —— 挂在实体上根本清不掉；
     * 2) 反过来给它标 IGNORED 更糟：后台「修改」那颗是整行提交、表单里没有审核意见这一格，
     *    一提交就把审核意见抹成 NULL（同用户票 qr_img 那把坑）。
     * 所以这一格只有审核这一路写得动，别的路一句都碰不到它。
     */
    int auditPhoto(@Param("id") Long id, @Param("status") Integer status,
                   @Param("auditMsg") String auditMsg, @Param("updateBy") Long updateBy);
}
