package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.ai.AgentDto;
import com.shop.dto.ai.AgentQueryDto;
import com.shop.entity.Agent;
import com.shop.entity.Conversation;
import com.shop.entity.Memory;
import com.shop.entity.ConfigSource;
import com.shop.kit.DatabaseKnowledgeLoader;
import com.shop.kit.DocumentKnowledgeLoader;
import com.shop.mapper.AgentMapper;
import com.shop.mapper.ConfigSourceMapper;
import com.shop.mapper.ConversationMapper;
import com.shop.mapper.MemoryMapper;
import com.shop.service.AgentService;
import com.shop.utils.BeanCopyUtil;
import com.shop.vo.ai.AgentVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    private final ConversationMapper conversationMapper;
    private final MemoryMapper memoryMapper;
    private final ConfigSourceMapper configSourceMapper;
    private final DatabaseKnowledgeLoader databaseKnowledgeLoader;
    private final DocumentKnowledgeLoader documentKnowledgeLoader;

    @Override
    public IPage<AgentVo> selectPage(Page<Agent> page, AgentQueryDto dto) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .like(StringUtils.hasText(dto.getName()), Agent::getName, dto.getName())
                .eq(dto.getModelType() != null, Agent::getModelType, dto.getModelType())
                .eq(dto.getStatus() != null, Agent::getStatus, dto.getStatus())
                .eq(dto.getIsPublic() != null, Agent::getIsPublic, dto.getIsPublic())
                .isNull(Agent::getDeletedAt)
                .orderByDesc(Agent::getCreateTime);

        Page<Agent> agentPage = baseMapper.selectPage(page, wrapper);

        IPage<AgentVo> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        voPage.setRecords(agentPage.getRecords().stream()
                .map(agent -> {
                    AgentVo vo = BeanCopyUtil.copyObj(agent, AgentVo.class);
                    if (agent.getConfigId() != null) {
                        ConfigSource cs = configSourceMapper.selectById(agent.getConfigId());
                        if (cs != null) {
                            vo.setConfigSourceName(cs.getSourceName());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public AgentVo detail(Long id) {
        Agent agent = baseMapper.selectById(id);
        if (agent == null || agent.getDeletedAt() != null) {
            return null;
        }
        return BeanCopyUtil.copyObj(agent, AgentVo.class);
    }

    @Override
    public Boolean add(AgentDto dto) {
        Agent agent = BeanCopyUtil.copyObj(dto, Agent.class);
        Long userId = StpUtil.getLoginIdAsLong();
        agent.setUserId(userId);
        agent.setCreatedBy(userId);
        agent.setTotalConversations(0);
        agent.setTotalMessages(0);
        agent.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        // 预生成知识库提示词
        loadKnowledgePrompt(agent);
        return baseMapper.insert(agent) > 0;
    }

    @Override
    public Boolean update(AgentDto dto) {
        Agent agent = BeanCopyUtil.copyObj(dto, Agent.class);
        // 预生成知识库提示词
        loadKnowledgePrompt(agent);
        return baseMapper.updateById(agent) > 0;
    }

    /**
     * 根据配置类型加载知识源内容，存入 systemPrompt
     * 当 configType 为 database/document 时，清空原有提示词并重新生成
     */
    private void loadKnowledgePrompt(Agent agent) {
        if ("database".equals(agent.getConfigType()) || "document".equals(agent.getConfigType())) {
            // 清空原有提示词，从知识源重新生成
            agent.setSystemPrompt(null);
            if (agent.getConfigId() != null) {
                try {
                    ConfigSource config = configSourceMapper.selectById(agent.getConfigId());
                    if (config != null && config.getEnabled() == 1) {
                        String knowledge = null;
                        if ("database".equals(config.getSourceType())) {
                            knowledge = databaseKnowledgeLoader.load(config);
                        } else if ("document".equals(config.getSourceType())) {
                            knowledge = documentKnowledgeLoader.load(config);
                        }
                        if (knowledge != null && !knowledge.isEmpty()) {
                            agent.setSystemPrompt("以下是你的前置知识库内容，请基于这些知识回答用户问题：\n" + knowledge);
                        }
                    }
                } catch (Exception e) {
                    log.error("预生成知识库提示词失败", e);
                }
            }
        }
        // configType 为 default 时，保留用户手动填写的 systemPrompt 不变
    }

    @Override
    public Boolean delete(List<Long> ids) {
        LocalDateTime now = LocalDateTime.now();

        // 软删除智能体下的所有会话及消息
        ids.forEach(agentId -> {
            // 查出该智能体下的所有会话ID
            List<Long> conversationIds = conversationMapper.selectList(
                    new LambdaQueryWrapper<Conversation>()
                            .eq(Conversation::getAgentId, agentId)
                            .isNull(Conversation::getDeletedAt)
            ).stream().map(Conversation::getId).toList();

            if (!conversationIds.isEmpty()) {
                // 软删除会话下的所有消息
                memoryMapper.update(null, new LambdaUpdateWrapper<Memory>()
                        .in(Memory::getConversationId, conversationIds)
                        .isNull(Memory::getDeletedAt)
                        .set(Memory::getDeletedAt, now));

                // 软删除会话
                conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                        .in(Conversation::getId, conversationIds)
                        .isNull(Conversation::getDeletedAt)
                        .set(Conversation::getDeletedAt, now));
            }
        });

        // 软删除智能体
        List<Agent> agents = ids.stream().map(id -> {
            Agent agent = new Agent();
            agent.setId(id);
            agent.setDeletedAt(now);
            return agent;
        }).collect(Collectors.toList());
        return updateBatchById(agents);
    }

    @Override
    public Boolean updateStatus(Long id, Integer status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setStatus(status);
        return baseMapper.updateById(agent) > 0;
    }

    @Override
    public Boolean refreshKnowledge(Long id) {
        Agent agent = baseMapper.selectById(id);
        if (agent == null || agent.getDeletedAt() != null) return false;
        // 清空并重新生成知识库提示词
        loadKnowledgePrompt(agent);
        return baseMapper.updateById(agent) > 0;
    }
}
