package com.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysWebConfig;

public interface SysWebConfigService extends IService<SysWebConfig> {

    void update(SysWebConfig sysWebConfig);
}
