package com.shop.service;

import com.shop.dto.ai.ChatDto;
import com.shop.vo.ai.MemoryVo;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 对话服务
 */
public interface AiChatService {

    /**
     * 发送消息（流式返回）
     */
    Flux<String> sendMessage(ChatDto dto);

    /**
     * 获取会话历史消息
     */
    List<MemoryVo> getHistory(Long conversationId);
}
