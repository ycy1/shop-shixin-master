package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ConfigSource;
import com.shop.mapper.ConfigSourceMapper;
import com.shop.service.ConfigSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConfigSourceServiceImpl extends ServiceImpl<ConfigSourceMapper, ConfigSource> implements ConfigSourceService {

    @Override
    public IPage<ConfigSource> selectPage(Page<ConfigSource> page, String sourceType) {
        LambdaQueryWrapper<ConfigSource> wrapper = new LambdaQueryWrapper<ConfigSource>()
                .eq(StringUtils.hasText(sourceType), ConfigSource::getSourceType, sourceType)
                .eq(ConfigSource::getDeleted, 0)
                .orderByDesc(ConfigSource::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public Boolean add(ConfigSource configSource) {
        configSource.setDeleted(0);
        configSource.setEnabled(configSource.getEnabled() != null ? configSource.getEnabled() : 1);
        return baseMapper.insert(configSource) > 0;
    }

    @Override
    public Boolean update(ConfigSource configSource) {
        return baseMapper.updateById(configSource) > 0;
    }

    @Override
    public Boolean delete(Long id) {
        ConfigSource source = new ConfigSource();
        source.setId(id);
        source.setDeleted(1);
        return baseMapper.updateById(source) > 0;
    }
}
