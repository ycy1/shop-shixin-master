package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.Memory;
import com.shop.mapper.MemoryMapper;
import com.shop.service.MemoryService;
import com.shop.vo.ai.MemoryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemoryServiceImpl extends ServiceImpl<MemoryMapper, Memory> implements MemoryService {

    @Override
    public IPage<MemoryVo> selectPage(Page<Object> page, Long conversationId) {
        return baseMapper.selectMemoryVoPage(page, conversationId);
    }

    @Override
    public Boolean deleteMemory(Long id) {
        return baseMapper.update(null, new LambdaUpdateWrapper<Memory>()
                .eq(Memory::getId, id)
                .set(Memory::getDeletedAt, LocalDateTime.now())) > 0;
    }
}
