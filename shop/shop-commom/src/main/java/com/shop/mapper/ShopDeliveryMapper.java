package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 配送单表 Mapper接口
 */
@Mapper
public interface ShopDeliveryMapper extends BaseMapper<ShopDelivery> {

    /**
     * 分页查询配送单：一个关键字模糊匹配配送单号/出库单号/收件人/手机号。
     * 两个只由服务层塞的查询字段：scopeRiderId 卡"派给这个送货员的 + 还没人接的"，
     * residentLng/residentLat 是那个送货员的常驻地点，带上它结果就按"离他最近"排（详见 XML 里那段注释）。
     */
    IPage<ShopDelivery> selectDeliveryPage(IPage<ShopDelivery> page, @Param("q") ShopDelivery query);

    /**
     * 两个地点之间的距离（km，SQL 里已经 ROUND 到 2 位）—— 配送单上 distance 那一格就取这个值。
     * 用的是就近分仓同一个 ST_Distance_Sphere，球面算法别在 Java 里再写一份。
     * 四个值必须都在合法值域内（经度 ±180、纬度 ±90），超范围 MySQL 会直接报错，值域由服务层先拦。
     */
    BigDecimal selectDistanceKm(@Param("senderLng") BigDecimal senderLng,
                                @Param("senderLat") BigDecimal senderLat,
                                @Param("receiverLng") BigDecimal receiverLng,
                                @Param("receiverLat") BigDecimal receiverLat);
}
