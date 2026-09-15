package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.vo.ai.MemoryVo;

public interface MemoryService {

    /**
     * 分页查询消息记录
     */
    IPage<MemoryVo> selectPage(Page<Object> page, Long conversationId);

    /**
     * 删除消息记录
     */
    Boolean deleteMemory(Long id);
}
