package com.shop.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import reactor.core.publisher.Flux;

//@AiService(streamingChatModel = "openAiStreamingChatModel")
public interface Assistant {

    Assistant buildModel(ChatLanguageModel  model);

    Assistant buildMemory(ChatMemory  memory);

    Assistant buildTools(Class... clazzs);

//    Result<String> chat(String question);

    TokenStream chat(String question);

    TokenStream chatStream(ChatMessage... messages);

    Flux<String> chatFlux(String messages);

    Flux<String> chatFlux(ChatMessage... messages);

    ChatResponse chat(ChatMessage... messages);



    ChatResponse chatCarryMemory(ChatMessage... messages);

}
