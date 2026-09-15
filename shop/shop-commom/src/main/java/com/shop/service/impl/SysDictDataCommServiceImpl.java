package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.Constants;
import com.shop.entity.SysDict;
import com.shop.entity.SysDictData;
import com.shop.mapper.SysDictDataMapper;
import com.shop.mapper.SysDictMapper;
import com.shop.service.SysDictDataCommService;
import com.shop.utils.PageUtil;
import com.shop.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 字典数据表 服务实现类
 */
@Service
@RequiredArgsConstructor
public class SysDictDataCommServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataCommService {

    private final SysDictMapper dictMapper;

    @Override
    public IPage<SysDictData> listDictData(Long dictId) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        // 构建查询条件
        wrapper.eq(SysDictData::getDictId,dictId)
                .orderByAsc(SysDictData::getSort);
        return page(PageUtil.getPage(), wrapper);
    }

    @Override
    public void addDictData(SysDictData sysDictData) {
        save(sysDictData);
    }

    @Override
    public void updateDictData(SysDictData sysDictData) {
        updateById(sysDictData);
    }

    @Override
    public Map<String, Map<String, Object>> getDictDataByDictType(List<String> dictTypes) {
        Map<String, Map<String, Object>> map = new HashMap<>();

        List<SysDict> dictList = dictMapper.selectList(new LambdaQueryWrapper<SysDict>().in(SysDict::getType,dictTypes)
                .eq(SysDict::getStatus, Constants.YES));
        dictList.forEach(item ->{
            LambdaQueryWrapper<SysDictData> sysDictDataQueryWrapper = new LambdaQueryWrapper<SysDictData>();
            sysDictDataQueryWrapper.eq(SysDictData::getStatus,Constants.YES);
            sysDictDataQueryWrapper.eq(SysDictData::getDictId, item.getId());
            sysDictDataQueryWrapper.orderByAsc(SysDictData::getSort);
            List<SysDictData> dataList = baseMapper.selectList(sysDictDataQueryWrapper);
            String defaultValue = null;
            for (SysDictData dictData : dataList) {
                //选取默认值
                if (Constants.YES == dictData.getIsDefault()){
                    defaultValue = dictData.getValue();
                    break;
                }
            }
            Map<String, Object> result = new HashMap<>();
            result.put("defaultValue",defaultValue);
            result.put("list",dataList);
            map.put(item.getType(),result);
        });
        return map;
    }

    private final RedisUtil redisUtil;


    @Override
//    @Cacheable(cacheNames = "sys_dict", key = "#dictType")
    public List<SysDictData> selectDataByDictTypeCache(String dictType) {
        List<SysDictData> sysDictData = new ArrayList<>();
        if(!redisUtil.hasKey(dictType)) {
            sysDictData = baseMapper.selectDataByDictType(dictType);
            redisUtil.set(dictType, sysDictData, TimeUnit.DAYS.toSeconds(2), TimeUnit.SECONDS);
        }else{
            sysDictData = (List<SysDictData>) redisUtil.get(dictType);
        }
        return sysDictData;
    }

    @Override
    public SysDictData selectDataByDictTypeAndDictValueCache(String dictType, String dictValue) {
        List<SysDictData> sysDictData = selectDataByDictTypeCache(dictType);
        for (SysDictData dictData : sysDictData) {
            if (dictData.getValue().equals(dictValue)) {
                return dictData;
            }
        }
        return null;
    }

    @Override
    public SysDictData selectDataByDictTypeAndDictLabelCache(String dictType, String dictLabel) {
        List<SysDictData> sysDictData = selectDataByDictTypeCache(dictType);
        for (SysDictData dictData : sysDictData) {
            if (dictData.getLabel().equals(dictLabel)) {
                return dictData;
            }
        }
        return null;
    }

    @Override
    public void refreshDictDataCache(List<String> dictTypes) {
        if (dictTypes == null || dictTypes.isEmpty()) {
            return;
        }
        for (String dictType : dictTypes) {
            // 删除旧缓存，重新查询数据库并回填
            redisUtil.delete(dictType);
            selectDataByDictTypeCache(dictType);
        }
    }
}
