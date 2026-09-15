package com.shop.controller;

import com.shop.service.Assistant;
import com.shop.service.AsyncTaskService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {
    @Autowired
    private AsyncTaskService asyncTaskService;

    @GetMapping("/async")
    public String testAsync() {
        try {
            asyncTaskService.submitTasksByLatch("test-002", 100);
            return "处理完成";
        } catch (Exception e) {
            e.printStackTrace();
            return "处理失败: " + e.getMessage();
        }
    }


    @Autowired
    @Qualifier("streamOllamaChatModel")
    private OllamaStreamingChatModel streamingChatModel;

    @Autowired
    @Qualifier("openAiStreamingChatModel")
    private OpenAiStreamingChatModel modelscopeStream;

    @Autowired
    @Qualifier("streamingDeepseekModel")
    private OpenAiStreamingChatModel streamingDeepseekModel;

    @CrossOrigin
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<Flux<String>> chat(@RequestBody Map<String, String> prompt) {
        log.info("进入流式响应 ----> start");

        Assistant assistant1 = AiServices.create(Assistant.class, streamingDeepseekModel);
        SystemMessage sysMessage = SystemMessage.from("你是一个文章简化提取助手");
        UserMessage prompt1 = UserMessage.from("请将下面这段内容进行不超过500字的总结，并返回总结结果\n\n" + prompt.get("prompt"));
        String s = sysMessage.text() + "\n" + prompt1.singleText();
        Flux<String> aiFlux = assistant1.chatFlux(s);
        log.info("进入流式响应 ----> end");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8));
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        headers.set("X-Accel-Buffering", "no");
        aiFlux.doOnNext(chunk -> System.out.println("Chunk: " + chunk));
        return ResponseEntity.ok()
                .headers(headers)
                .body(aiFlux);
    }



    @CrossOrigin
    @PostMapping(value = "/chatModel")
    public String chatModel(@RequestBody Map<String, String> prompt) {
        System.out.println("prompt: " + prompt);
        try {
            asyncTaskService.submitTasksByChat("1", prompt.get("prompt"));
            return "处理完成";
        } catch (Exception e) {
            e.printStackTrace();
            return "处理失败: " + e.getMessage();
        }
    }

}
