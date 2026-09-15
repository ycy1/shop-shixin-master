package com.shop.mapper;

import com.shop.entity.SysDictData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 字典数据表 Mapper接口
 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {

    List<SysDictData> selectDataByDictType(String dictType);
}
