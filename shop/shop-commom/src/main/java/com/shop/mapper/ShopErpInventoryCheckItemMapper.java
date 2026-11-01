package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopErpInventoryCheckItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 盘点明细 Mapper接口（同 ShopErpInventoryTransferItemMapper：名称联表带，一次把该带的都带上）
 */
@Mapper
public interface ShopErpInventoryCheckItemMapper extends BaseMapper<ShopErpInventoryCheckItem> {

    /**
     * 一张盘点单的明细。
     *
     * @param checkId     盘点单id
     * @param warehouseId 这张单盘的那家仓id：拿它再联一跳 shop_erp_inventory，把"这一条规格现在还剩几件、
     *                    还能动几件"一起带出来（「调整」弹窗里那两格要用，也是它和库存行唯一的那条路）。
     *                    传 null 就没有这两格（比如仓已经被删了）
     */
    List<ShopErpInventoryCheckItem> selectItemsByCheckId(@Param("checkId") Long checkId,
            @Param("warehouseId") Long warehouseId);

    /**
     * 改单时把 (check_id, sku_id) 那一行**原行覆盖回去**：book_quantity / cost_price 不动（账面必须停在
     * 盘点开始那一刻），其它服务端算出来的列整行重写，并且把 is_deleted 拨回 0。
     *
     * 为什么要自己写这条 UPDATE：明细表上有唯一键 uk_check_sku (check_id, sku_id)，软删掉的旧行仍然占着
     * 这个键，所以"整单软删重插"（调拨明细那一套）在这一张表上第二次保存同一条规格就会撞 1062。
     * 而 MyBatis-Plus 的任何 update 都会被逻辑删除谓词挡在 is_deleted=0 那一侧，捞不回被删的那一行 ——
     * 只有这条不带谓词的 SQL 能同时覆盖"活行"和"上次删掉的行"两种情况。
     */
    int resetItem(@Param("q") ShopErpInventoryCheckItem item);
}
