package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysDictData;

import java.util.List;
import java.util.Map;

/**
 * 字典数据表 服务接口
 */
public interface SysDictDataCommService extends IService<SysDictData> {
    /**
     * 查询字典数据分页列表
     */
    IPage<SysDictData> listDictData(Long dictId);

    /**
     * 新增字典数据
     */
    void addDictData(SysDictData sysDictData);

    /**
     * 修改字典数据
     */
    void updateDictData(SysDictData sysDictData);

    /**
     * 根据字典类型查询字典数据
     * @param dictTypes
     * @return
     */
    Map<String, Map<String, Object>> getDictDataByDictType(List<String> dictTypes);

    /**
     * 根据字典类型获取字典数据-缓存版
     * @param dictType
     * @return
     */
    List<SysDictData> selectDataByDictTypeCache(String dictType);

    /**
     * 根据字典类型和字典值获取字典数据-缓存版
     * @param dictType
     * @return
     */
    SysDictData selectDataByDictTypeAndDictValueCache(String dictType, String dictValue);
    /**
     * 根据字典类型和字典lable获取字典数据-缓存版
     * @param dictType
     * @return
     */
    SysDictData selectDataByDictTypeAndDictLabelCache(String dictType, String dictLabel);

    /**
     * 刷新字典缓存：删除 Redis 中的缓存并重新加载
     * @param dictTypes 字典类型集合
     */
    void refreshDictDataCache(List<String> dictTypes);
}
