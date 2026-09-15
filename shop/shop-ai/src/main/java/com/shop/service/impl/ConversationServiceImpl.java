package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.ai.ConversationDto;
import com.shop.dto.ai.ConversationQueryDto;
import com.shop.entity.Conversation;
import com.shop.entity.Memory;
import com.shop.entity.Agent;
import com.shop.mapper.AgentMapper;
import com.shop.mapper.ConversationMapper;
import com.shop.mapper.MemoryMapper;
import com.shop.service.ConversationService;
import com.shop.vo.ai.ConversationVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    private final MemoryMapper memoryMapper;
    private final AgentMapper agentMapper;

    @Override
    public IPage<ConversationVo> selectPage(Page<Object> page, ConversationQueryDto dto) {
        return baseMapper.selectConversationVoPage(page, dto.getAgentId(), dto.getUserId());
    }

    @Override
    public ConversationVo detail(Long id) {
        return baseMapper.selectConversationVoById(id);
    }

    @Override
    public Conversation add(ConversationDto dto) {
        Long userId = StpUtil.getLoginIdAsLong();

        // 结束该用户下该智能体的旧活跃会话，满足唯一约束
        baseMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getAgentId, dto.getAgentId())
                .eq(Conversation::getStatus, 1)
                .set(Conversation::getStatus, 0)
                .set(Conversation::getUpdateTime, LocalDateTime.now()));

        Conversation conversation = new Conversation();
        conversation.setAgentId(dto.getAgentId());
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "新对话");
        conversation.setConfig(dto.getConfig());
        conversation.setContextWindow(dto.getContextWindow() != null ? dto.getContextWindow() : 10);
        conversation.setStatus(1);
        conversation.setIsPinned(dto.getIsPinned() != null ? dto.getIsPinned() : 0);
        conversation.setMessageCount(0);
        conversation.setTotalTokensUsed(0);
        conversation.setLastActiveAt(LocalDateTime.now());
        baseMapper.insert(conversation);

        // 更新智能体会话计数
        agentMapper.update(null, new LambdaUpdateWrapper<Agent>()
                .eq(Agent::getId, dto.getAgentId())
                .setSql("total_conversations = total_conversations + 1"));

        return conversation;
    }

    @Override
    public Boolean update(ConversationDto dto) {
        Conversation conversation = new Conversation();
        conversation.setId(dto.getId());
        conversation.setTitle(dto.getTitle());
        conversation.setConfig(dto.getConfig());
        conversation.setContextWindow(dto.getContextWindow());
        conversation.setIsPinned(dto.getIsPinned());
        return baseMapper.updateById(conversation) > 0;
    }

    @Override
    public Boolean delete(List<Long> ids) {
        LocalDateTime now = LocalDateTime.now();

        // 软删除会话关联的记忆
        ids.forEach(id ->
            memoryMapper.update(null, new LambdaUpdateWrapper<Memory>()
                    .eq(Memory::getConversationId, id)
                    .isNull(Memory::getDeletedAt)
                    .set(Memory::getDeletedAt, now))
        );

        // 先查出各会话所属智能体ID，用于后续更新计数
        List<Long> agentIds = ids.stream()
                .map(id -> {
                    Conversation c = baseMapper.selectById(id);
                    return c != null ? c.getAgentId() : null;
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        // 软删除会话
        List<Conversation> conversations = ids.stream().map(id -> {
            Conversation conversation = new Conversation();
            conversation.setId(id);
            conversation.setDeletedAt(now);
            return conversation;
        }).toList();
        boolean result = updateBatchById(conversations);

        // 更新智能体会话计数
        agentIds.forEach(aid ->
                agentMapper.update(null, new LambdaUpdateWrapper<Agent>()
                        .eq(Agent::getId, aid)
                        .gt(Agent::getTotalConversations, 0)
                        .setSql("total_conversations = total_conversations - 1")));

        return result;
    }

    @Override
    public void updateStats(Long conversationId, int tokensUsed) {
        Conversation conversation = baseMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setTotalTokensUsed(conversation.getTotalTokensUsed() + tokensUsed);
            conversation.setMessageCount(conversation.getMessageCount() + 2);
            conversation.setLastActiveAt(LocalDateTime.now());
            baseMapper.updateById(conversation);
        }
    }
}
