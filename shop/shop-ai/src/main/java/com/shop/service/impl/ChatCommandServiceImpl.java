package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.entity.Agent;
import com.shop.entity.ConfigSource;
import com.shop.entity.Conversation;
import com.shop.entity.Memory;
import com.shop.kit.DatabaseKnowledgeLoader;
import com.shop.kit.DocumentKnowledgeLoader;
import com.shop.entity.Conversation;
import com.shop.mapper.AgentMapper;
import com.shop.mapper.ConfigSourceMapper;
import com.shop.mapper.ConversationMapper;
import com.shop.mapper.MemoryMapper;
import com.shop.service.ChatCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCommandServiceImpl implements ChatCommandService {

    private final MemoryMapper memoryMapper;
    private final AgentMapper agentMapper;
    private final ConfigSourceMapper configSourceMapper;
    private final ConversationMapper conversationMapper;
    private final DatabaseKnowledgeLoader databaseKnowledgeLoader;
    private final DocumentKnowledgeLoader documentKnowledgeLoader;

    @Override
    public String executeClear(Conversation conversation, Agent agent) {
        LocalDateTime now = LocalDateTime.now();
        // 软删除会话下的所有记忆
        memoryMapper.update(null, new LambdaUpdateWrapper<Memory>()
                .eq(Memory::getConversationId, conversation.getId())
                .isNull(Memory::getDeletedAt)
                .set(Memory::getDeletedAt, now));
        // 重置会话统计
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversation.getId())
                .set(Conversation::getMessageCount, 0)
                .set(Conversation::getTotalTokensUsed, 0));
        log.info("已清除会话 {} 的上下文", conversation.getId());
        return "✅ 上下文已清除，对话历史已清空";
    }

    @Override
    public byte[] executeExport(Conversation conversation, Integer limit) {
        LambdaQueryWrapper<Memory> wrapper = new LambdaQueryWrapper<Memory>()
                .eq(Memory::getConversationId, conversation.getId())
                .isNull(Memory::getDeletedAt)
                .orderByDesc(Memory::getCreateTime);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        List<Memory> memories = memoryMapper.selectList(wrapper);
        // 反转时间顺序，最早的在前
        java.util.Collections.reverse(memories);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("对话记录");
            // 表头
            Row header = sheet.createRow(0);
            String[] cols = {"序号", "角色", "内容", "时间"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                CellStyle style = wb.createCellStyle();
                Font font = wb.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }
            // 数据
            int rowNum = 1;
            for (Memory m : memories) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(m.getRole());
                row.createCell(2).setCellValue(m.getContent());
                row.createCell(3).setCellValue(m.getCreateTime() != null
                        ? m.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            }
            // 列宽
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 12 * 256);
            sheet.setColumnWidth(2, 60 * 256);
            sheet.setColumnWidth(3, 22 * 256);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("导出对话失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    @Override
    public String executeInit(Conversation conversation, Agent agent) {
        // 清除上下文
        executeClear(conversation, agent);
        // 重新加载知识库
        if (agent.getConfigId() != null && ("database".equals(agent.getConfigType()) || "document".equals(agent.getConfigType()))) {
            var config = configSourceMapper.selectById(agent.getConfigId());
            if (config != null && config.getEnabled() == 1) {
                String knowledge = null;
                if ("database".equals(config.getSourceType())) {
                    knowledge = databaseKnowledgeLoader.load(config);
                } else if ("document".equals(config.getSourceType())) {
                    knowledge = documentKnowledgeLoader.load(config);
                }
                if (knowledge != null && !knowledge.isEmpty()) {
                    agent.setSystemPrompt(agent.getSystemPrompt() != null
                            ? agent.getSystemPrompt() : "");
                    agent.setSystemPrompt(agent.getSystemPrompt() + "\n\n以下是你的前置知识库内容，请基于这些知识回答用户问题：\n" + knowledge);
                    agentMapper.updateById(agent);
                }
            }
        }
        return "✅ 窗口已重新加载，知识库已刷新，上下文已清空";
    }
}

