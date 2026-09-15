package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.Memory;
import com.shop.vo.ai.MemoryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryMapper extends BaseMapper<Memory> {

    IPage<MemoryVo> selectMemoryVoPage(Page<Object> page, @Param("conversationId") Long conversationId);

    List<MemoryVo> selectMemoryByConversationId(@Param("conversationId") Long conversationId);
}
