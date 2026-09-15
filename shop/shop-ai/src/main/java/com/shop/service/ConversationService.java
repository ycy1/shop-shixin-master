package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.ai.ConversationDto;
import com.shop.dto.ai.ConversationQueryDto;
import com.shop.entity.Conversation;
import com.shop.vo.ai.ConversationVo;

import java.util.List;

public interface ConversationService extends IService<Conversation> {

    /**
     * 分页查询会话列表
     */
    IPage<ConversationVo> selectPage(Page<Object> page, ConversationQueryDto dto);

    /**
     * 会话详情
     */
    ConversationVo detail(Long id);

    /**
     * 新增会话
     */
    Conversation add(ConversationDto dto);

    /**
     * 修改会话
     */
    Boolean update(ConversationDto dto);

    /**
     * 删除会话
     */
    Boolean delete(List<Long> ids);

    /**
     * 更新会话token和消息计数
     */
    void updateStats(Long conversationId, int tokensUsed);
}
