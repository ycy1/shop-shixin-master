package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopPlazaShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * 广场消息分享 Mapper接口
 *
 * 只插不查（这一张表只是留痕 + 给广场上的「分享数」供数，而那个数是 ShopPlazaMessageMapper.xml 现算的），
 * 所以只有 BaseMapper 的 insert 有用，没有自定义 SQL。
 */
@Mapper
public interface ShopPlazaShareMapper extends BaseMapper<ShopPlazaShare> {
}
