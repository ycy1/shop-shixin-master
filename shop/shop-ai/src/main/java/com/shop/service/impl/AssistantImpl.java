package com.shop.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.shop.service.Assistant;
import com.shop.utils.ToolsResult;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Service("myAssistant")
@Slf4j
public class AssistantImpl implements Assistant {
//    private List<ChatMessage> memorys = new ArrayList<>();
    private ChatLanguageModel model =  null;
    private ChatMemory memory = null;
    private List<ToolSpecification> toolSpecifications = new ArrayList<>();

    private List<Map<Method, ToolSpecification>> toolSpecificationsMap = new ArrayList<>();


    @Override
    public Assistant buildModel(ChatLanguageModel model) {
        this.model = model;
        return this;
    }

    @Override
    public Assistant buildMemory(ChatMemory memory) {
        this.memory = memory;
        return this;
    }

    @Override
    public Assistant buildTools(Class... clazzs) {
        List<ToolSpecification> tools = new ArrayList<>();
        List<Map<Method, ToolSpecification>> collecttools = new ArrayList<>();
        for (Class clazz : clazzs) {
            List tool = Arrays.stream(clazz.getDeclaredMethods())
                    .filter((method) -> method.isAnnotationPresent(Tool.class))
                    .map(ToolSpecifications::toolSpecificationFrom)
                    .toList();

            List<Map<Method, ToolSpecification>> collect = Arrays.stream(clazz.getDeclaredMethods())
                    .filter((method) -> method.isAnnotationPresent(Tool.class))
                    .map(method -> {
                        Map<Method, ToolSpecification> methodToolSpecificationHashMap =new HashMap<>();
                        methodToolSpecificationHashMap.put(method, ToolSpecifications.toolSpecificationFrom(method));
                        return methodToolSpecificationHashMap;
                    }).toList();

            tools.addAll(!tool.isEmpty()?tool:new ArrayList<>());
            collecttools.addAll(collect);
        }


        this.toolSpecifications = tools;
        this.toolSpecificationsMap = collecttools;
        return this;
    }

    @Override
    public TokenStream chat(String question) {
        return null;
    }

    @Override
    public TokenStream chatStream(ChatMessage... messages) {
        return null;
    }

    @Override
    public Flux<String> chatFlux(String messages) {
        return null;
    }

    @Override
    public Flux<String> chatFlux(ChatMessage... messages) {
        return null;
    }

    @Override
    public ChatResponse chat(ChatMessage... messages) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
        ChatResponse response = model.chat(request);
        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()){
            List<ToolExecutionResultMessage> toolExecutionResultMessages = new ArrayList<>();
            List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
            for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
                String arguments = toolExecutionRequest.arguments();
                String name = toolExecutionRequest.name();

                for (Map<Method, ToolSpecification> methodToolSpecificationHashMap : toolSpecificationsMap) {
                    for (Map.Entry<Method, ToolSpecification> entry : methodToolSpecificationHashMap.entrySet()) {
                        if (entry.getValue().name().equals(name)){
                            try {
                                Method method = entry.getKey();
                                Gson gson = new Gson();
                                JsonObject paramJson = gson.fromJson(arguments, JsonObject.class);
                                List<Object> params = new ArrayList<>();
                                for (Parameter parameter : method.getParameters()) {
                                    Object param = gson.fromJson(paramJson.get(parameter.getName()), parameter.getType());
                                    params.add(param);
                                }
                                Object methodRes = method.invoke(method.getDeclaringClass().getDeclaredConstructor().newInstance(), params.toArray());
                                ToolsResult result = (ToolsResult) methodRes;
                                if(result.getCode() == 200){
                                    String data = result.getData().toString();
                                    log.info(String.format("调用工具，名称：%s，参数：%s，结果：%s", name, paramJson, data));
                                    toolExecutionResultMessages.add(ToolExecutionResultMessage.from(toolExecutionRequest, data));
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }

            if(!toolExecutionResultMessages.isEmpty()){
                List<ChatMessage> messages1 = new ArrayList<>(List.of(messages));
                messages1.addAll(toolExecutionResultMessages);
//                request = ChatRequest.builder()
//                        .messages(messages1)
//                        .toolSpecifications(toolSpecifications)
//                        .build();
//                response = model.chat(request);

                // 回归调用 直到没有工具的函数调用
                response = this.chat(messages1.toArray(new ChatMessage[0]));

            }
        }


        return response;
    }


    @Override
    public ChatResponse chatCarryMemory(ChatMessage... messages) {
        if (memory.messages().size()>10){
            List<ChatMessage> allMessages = memory.messages();
            memory.clear();
            allMessages.subList(allMessages.size()-10,allMessages.size()).forEach(memory::add);
        }
        List.of(messages).forEach(memory::add);
        ChatRequest request = ChatRequest.builder().messages(messages)
                .toolSpecifications(toolSpecifications).build();
        ChatResponse chatResponse = model.chat(request);
        memory.add(chatResponse.aiMessage());
        return chatResponse;
    }

}
