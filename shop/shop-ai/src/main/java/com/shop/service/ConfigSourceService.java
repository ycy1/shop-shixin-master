package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.ConfigSource;

public interface ConfigSourceService {

    IPage<ConfigSource> selectPage(Page<ConfigSource> page, String sourceType);

    Boolean add(ConfigSource configSource);

    Boolean update(ConfigSource configSource);

    Boolean delete(Long id);
}
