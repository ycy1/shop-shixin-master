package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.dto.ai.ChatDto;
import com.shop.entity.Agent;
import com.shop.entity.Conversation;
import com.shop.entity.Memory;
import com.shop.entity.ConfigSource;
import com.shop.mapper.AgentMapper;
import com.shop.mapper.ConfigSourceMapper;
import com.shop.mapper.ConversationMapper;
import com.shop.mapper.MemoryMapper;
import com.shop.service.AiChatService;
import com.shop.vo.ai.MemoryVo;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AgentMapper agentMapper;
    private final ConversationMapper conversationMapper;
    private final MemoryMapper memoryMapper;
    private final ConfigSourceMapper configSourceMapper;
    private final com.shop.kit.DatabaseKnowledgeLoader databaseKnowledgeLoader;
    private final com.shop.service.ChatCommandService chatCommandService;

    @Resource(name = "deepseekModel")
    private ChatLanguageModel chatModel;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flux<String> sendMessage(ChatDto dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Flux.create(sink -> {
            try {
                // 1. 解析会话
                Conversation conversation = resolveConversation(dto, userId);

                // 2. 获取智能体配置
                Agent agent = agentMapper.selectById(conversation.getAgentId());
                if (agent == null || agent.getDeletedAt() != null) {
                    sink.error(new RuntimeException("智能体不存在或已删除"));
                    return;
                }

                // 2.5 检测是否为指令
                String userContent = dto.getContent();
                if (com.shop.enums.ChatCommandEnum.isCommand(userContent)) {
                    var parseResult = com.shop.enums.ChatCommandEnum.parse(userContent);
                    if (parseResult != null) {
                        String cmdResult = handleCommand(parseResult, conversation, agent);
                        // 直接返回指令执行结果（不入消息记录）
                        sink.next(cmdResult);
                        sink.complete();
                    }else {
                        sink.next("无效的指令！");
                        sink.complete();
                    }
                    return;
                }

                // 3. 构建上下文消息（系统提示词已包含预生成的知识库内容）
                List<ChatMessage> messages = buildContext(agent, conversation, userContent);

                // 3.5 数据库智能体：将用户问题转为 SQL 并执行，结果注入上下文
                String queryResult = generateAndExecuteSql(agent, messages, dto.getContent());
                if (queryResult != null) {
                    messages.add(dev.langchain4j.data.message.SystemMessage.from(
                            "以下是查询数据库得到的实际数据，请基于这些数据回答用户问题：\n" + queryResult));
                }

                // 4. 保存用户消息
                saveUserMemory(conversation, agent, userId, dto);

                // 5. 调用 AI（携带工具，优先使用前端传入的工具列表）
                String toolsStr = dto.getTools() != null ? dto.getTools() : agent.getTools();
                dev.langchain4j.agent.tool.ToolSpecification[] toolSpecs = parseAgentTools(toolsStr);
                dev.langchain4j.model.chat.request.ChatRequest chatReq;
                if (toolSpecs.length > 0) {
                    chatReq = dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(messages)
                            .toolSpecifications(java.util.Arrays.asList(toolSpecs))
                            .build();
                } else {
                    chatReq = dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(messages)
                            .build();
                }
                // 调用模型并处理工具调用
                ChatResponse response = chatModel.chat(chatReq);
                if (response.aiMessage().hasToolExecutionRequests()) {
                    response = executeToolAndRetry(response, agent, messages, chatReq);
                }
                String aiContent = response.aiMessage().text();

                // 流式输出（按行分割，保留换行和空格）
                StringBuilder fullResponse = new StringBuilder();
                String[] lines = aiContent.split("(?<=\\n)", -1);
                for (String line : lines) {
                    fullResponse.append(line);
                    sink.next(line);
                    try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                }

                // 6. 保存AI回复
                saveAssistantMemory(conversation, agent, userId, fullResponse.toString());

                sink.complete();

            } catch (Exception e) {
                log.error("AI对话异常", e);
                sink.error(e);
            }
        });
    }

    @Override
    public List<MemoryVo> getHistory(Long conversationId) {
        return memoryMapper.selectMemoryByConversationId(conversationId);
    }

    /**
     * 解析或创建会话
     */
    private Conversation resolveConversation(ChatDto dto, Long userId) {
        if (dto.getConversationId() != null) {
            Conversation conversation = conversationMapper.selectById(dto.getConversationId());
            if (conversation == null || conversation.getDeletedAt() != null) {
                throw new RuntimeException("会话不存在或已删除");
            }
            return conversation;
        }

        // 新建会话
        if (dto.getAgentId() == null) {
            throw new RuntimeException("新建会话时必须指定智能体ID");
        }
        Conversation conversation = new Conversation();
        conversation.setAgentId(dto.getAgentId());
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setStatus(1);
        conversation.setContextWindow(10);
        conversation.setIsPinned(0);
        conversation.setMessageCount(0);
        conversation.setTotalTokensUsed(0);
        conversation.setLastActiveAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        // 更新智能体会话计数
        Agent agent = agentMapper.selectById(dto.getAgentId());
        if (agent != null) {
            agent.setTotalConversations(agent.getTotalConversations() + 1);
            agentMapper.updateById(agent);
        }

        return conversation;
    }

    /**
     * 构建对话上下文
     */
    private List<ChatMessage> buildContext(Agent agent, Conversation conversation, String userContent) {
        List<ChatMessage> messages = new ArrayList<>();

        // 系统提示词（已包含预生成的知识库内容）
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isEmpty()) {
            messages.add(dev.langchain4j.data.message.SystemMessage.from(agent.getSystemPrompt()));
        }

        // 历史消息（最多取最近 50 条）
        int limit = Math.min(50, conversation.getContextWindow() != null ? conversation.getContextWindow() * 2 : 20);
        List<Memory> history = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getConversationId, conversation.getId())
                        .isNull(Memory::getDeletedAt)
                        .orderByDesc(Memory::getCreateTime)
                        .last("LIMIT " + limit)
        );
        // 反转为正序
        java.util.Collections.reverse(history);

        for (Memory m : history) {
            switch (m.getRole()) {
                case "user" -> messages.add(UserMessage.from(m.getContent()));
                case "assistant" -> messages.add(AiMessage.from(m.getContent()));
                case "system" -> messages.add(SystemMessage.from(m.getContent()));
            }
        }

        // 当前用户消息
        messages.add(UserMessage.from(userContent));
        return messages;
    }

    /**
     * 生成并执行 SQL 查询
     */
    /**
     * 处理聊天指令
     */
    private String handleCommand(com.shop.enums.ChatCommandEnum.ParseResult parseResult,
                                  Conversation conversation, Agent agent) {
        try {
            switch (parseResult.getCommand()) {
                case CLEAR:
                    return chatCommandService.executeClear(conversation, agent);
                case EXPORT:
                case EXPORT_N:
                    Integer limit = parseResult.getParam() != null
                            ? Integer.parseInt(parseResult.getParam()) : null;
                    byte[] excelData = chatCommandService.executeExport(conversation, limit);
                    // 保存临时文件并返回下载路径
                    String fileId = java.util.UUID.randomUUID().toString();
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String filePath = tempDir + "/chat_export_" + fileId + ".xlsx";
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath)) {
                        fos.write(excelData);
                    }
                    return "EXPORT_OK:" + fileId;
                case INIT:
                    return chatCommandService.executeInit(conversation, agent);
                default:
                    return "未知指令：" + parseResult.getCommand();
            }
        } catch (Exception e) {
            log.error("指令执行失败", e);
            return "❌ 指令执行失败: " + e.getMessage();
        }
    }

    private String generateAndExecuteSql(Agent agent, java.util.List<dev.langchain4j.data.message.ChatMessage> messages, String userContent) {
        if (!"database".equals(agent.getConfigType()) || agent.getConfigId() == null) return null;
        try {
            com.shop.entity.ConfigSource dbConfig = configSourceMapper.selectById(agent.getConfigId());
            if (dbConfig == null || dbConfig.getEnabled() != 1) return null;

            // 复用已构建的上下文，在用户问题末尾追加 SQL 生成指令
            java.util.List<dev.langchain4j.data.message.ChatMessage> sqlMessages =
                    new java.util.ArrayList<>(messages);
            int lastIdx = sqlMessages.size() - 1;
            if (lastIdx >= 0 && sqlMessages.get(lastIdx) instanceof dev.langchain4j.data.message.UserMessage) {
                dev.langchain4j.data.message.UserMessage lastMsg =
                        (dev.langchain4j.data.message.UserMessage) sqlMessages.get(lastIdx);
                sqlMessages.set(lastIdx, dev.langchain4j.data.message.UserMessage.from(
                        lastMsg.singleText() + "\n\n将以上用户问题转为SQL查询，只返回SQL语句不要解释。"));
            }
            var sqlReq = dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(sqlMessages)
                    .build();
            String sql = chatModel.chat(sqlReq).aiMessage().text()
                    .replaceAll("(?s)```sql\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            log.info("AI 生成的 SQL: {}", sql);
            com.shop.kit.DatabaseKnowledgeLoader.validateReadOnly(sql);
            return databaseKnowledgeLoader.executeQuery(dbConfig, sql);
        } catch (Exception e) {
            log.warn("SQL 自动查询失败(不影响对话): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析智能体的工具列表为 ToolSpecification 数组
     */
    private dev.langchain4j.agent.tool.ToolSpecification[] parseAgentTools(String toolsJson) {
        if (toolsJson == null || toolsJson.trim().isEmpty()) return new dev.langchain4j.agent.tool.ToolSpecification[0];
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String[] classNames = gson.fromJson(toolsJson, String[].class);
            if (classNames == null || classNames.length == 0) return new dev.langchain4j.agent.tool.ToolSpecification[0];

            java.util.List<dev.langchain4j.agent.tool.ToolSpecification> specs = new java.util.ArrayList<>();
            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName("com.shop.tools." + className);
                    specs.addAll(dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(clazz));
                } catch (ClassNotFoundException e) {
                    log.warn("工具类不存在: com.shop.tools.{}", className);
                }
            }
            return specs.toArray(new dev.langchain4j.agent.tool.ToolSpecification[0]);
        } catch (Exception e) {
            log.warn("解析工具列表失败: {}", e.getMessage());
            return new dev.langchain4j.agent.tool.ToolSpecification[0];
        }
    }

    /**
     * 执行工具调用并递归重试直到模型不再调用工具
     */
    private ChatResponse executeToolAndRetry(ChatResponse response, Agent agent,
                                              java.util.List<dev.langchain4j.data.message.ChatMessage> messages,
                                              dev.langchain4j.model.chat.request.ChatRequest originalReq) {
        int maxTurns = 5;
        for (int turn = 0; turn < maxTurns; turn++) {
            if (!response.aiMessage().hasToolExecutionRequests()) break;

            java.util.List<dev.langchain4j.data.message.ChatMessage> allMessages = new java.util.ArrayList<>(messages);
            allMessages.add(response.aiMessage());

            for (var req : response.aiMessage().toolExecutionRequests()) {
                String result = executeSingleTool(req);
                allMessages.add(dev.langchain4j.data.message.ToolExecutionResultMessage.from(req, result));
            }

            var newReq = dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(allMessages)
                    .toolSpecifications(originalReq.toolSpecifications())
                    .build();
            response = chatModel.chat(newReq);
        }
        return response;
    }

    /**
     * 执行单个工具调用
     */
    private String executeSingleTool(dev.langchain4j.agent.tool.ToolExecutionRequest req) {
        String toolName = req.name();
        String argsJson = req.arguments();
        log.info("工具调用开始: {} args={}", toolName, argsJson);
        try {
            // 扫描 tools 包下所有 class 文件
            java.util.List<Class<?>> toolClasses = scanToolClasses();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject params = gson.fromJson(argsJson, com.google.gson.JsonObject.class);

            for (Class<?> clazz : toolClasses) {
                for (var method : clazz.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) continue;
                    var spec = dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom(method);
                    if (!spec.name().equals(toolName)) continue;

                    java.util.List<Object> args = new java.util.ArrayList<>();
                    for (var param : method.getParameters()) {
                        String paramName = param.getName();
                        if (params.has(paramName)) {
                            args.add(gson.fromJson(params.get(paramName), param.getType()));
                        } else {
                            log.warn("工具参数 {} 未在 JSON 中找到, available keys: {}", paramName, params.keySet());
                        }
                    }
                    Object result = method.invoke(clazz.getDeclaredConstructor().newInstance(), args.toArray());
                    String resultStr = (result instanceof com.shop.utils.ToolsResult)
                            ? ((com.shop.utils.ToolsResult) result).getData().toString()
                            : String.valueOf(result);
                    log.info("工具调用完成: {} => {}", toolName, resultStr);
                    return resultStr;
                }
            }
            log.warn("工具未找到: {}", toolName);
            return "未找到工具: " + toolName;
        } catch (Exception e) {
            log.error("工具调用失败: {}", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /** 扫描 tools 包下所有带 @Tool 方法的类 */
    private java.util.List<Class<?>> scanToolClasses() {
        java.util.List<Class<?>> classes = new java.util.ArrayList<>();
        try {
            String packagePath = "com/mojian/tools/";
            var resources = getClass().getClassLoader().getResources(packagePath);
            while (resources.hasMoreElements()) {
                var resource = resources.nextElement();
                if (!"file".equals(resource.getProtocol())) continue;
                java.io.File dir = new java.io.File(resource.toURI());
                if (!dir.exists()) continue;
                for (java.io.File file : dir.listFiles()) {
                    if (!file.getName().endsWith(".class")) continue;
                    String className = "com.shop.tools." + file.getName().replace(".class", "");
                    Class<?> clazz = Class.forName(className);
                    // 只保留有 @Tool 方法的类
                    for (var m : clazz.getDeclaredMethods()) {
                        if (m.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                            classes.add(clazz);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return classes;
    }

    private void saveUserMemory(Conversation conversation, Agent agent, Long userId, ChatDto dto) {
        int turnNumber = (conversation.getMessageCount() / 2) + 1;
        Memory memory = new Memory();
        memory.setConversationId(conversation.getId());
        memory.setAgentId(agent.getId());
        memory.setUserId(userId);
        memory.setRole("user");
        memory.setContent(dto.getContent());
        memory.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : "text");
        memory.setTurnNumber(turnNumber);
        memory.setImportanceScore(new BigDecimal("0.5"));
        memoryMapper.insert(memory);
    }

    /**
     * 保存AI回复到记忆表
     */
    private void saveAssistantMemory(Conversation conversation, Agent agent, Long userId, String content) {
        int turnNumber = (conversation.getMessageCount() / 2) + 1;
        Memory memory = new Memory();
        memory.setConversationId(conversation.getId());
        memory.setAgentId(agent.getId());
        memory.setUserId(userId);
        memory.setRole("assistant");
        memory.setContent(content);
        memory.setMessageType("text");
        memory.setTurnNumber(turnNumber + 1);
        memory.setImportanceScore(new BigDecimal("0.5"));
        memoryMapper.insert(memory);

        // 更新会话统计
        int estimatedTokens = content.length() / 4;
        conversation.setTotalTokensUsed(
                (conversation.getTotalTokensUsed() != null ? conversation.getTotalTokensUsed() : 0) + estimatedTokens);
        conversation.setMessageCount(
                (conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 2);
        conversation.setLastActiveAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        // 仅更新智能体消息计数，避免全字段更新
        agentMapper.update(null, new LambdaUpdateWrapper<Agent>()
                .eq(Agent::getId, agent.getId())
                .setSql("total_messages = total_messages + 2"));
    }
}
