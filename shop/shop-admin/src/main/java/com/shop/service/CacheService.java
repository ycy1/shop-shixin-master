package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.vo.cache.*;

public interface CacheService {
    
    /**
     * 获取缓存基本信息
     */
    CacheInfoVo getCacheInfo();
    
    /**
     * 获取内存信息
     */
    CacheMemoryVo getMemoryInfo();
    
    /**
     * 获取缓存键列表
     */
    IPage<CacheKeyVo> getKeyList(CacheKeyQuery query);
    
    /**
     * 清空缓存
     */
    void clearCache();

    /**
     * 看某一个键存的内容（string 给原文，其余类型按成员/字段铺开）
     */
    CacheValueVo getValue(String key);

    /**
     * 改某一个键的内容（只有 string 类型可改）
     */
    void updateValue(CacheValueDto dto);

    /**
     * 删某一个键（什么类型都能删，缓存删了就读侧回源重建）
     */
    void deleteValue(String key);
} 