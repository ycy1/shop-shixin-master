package com.shop.controller.app;

import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.dto.ai.ChatDto;
import com.shop.dto.ai.ConversationDto;
import com.shop.entity.Conversation;
import com.shop.service.AiChatService;
import com.shop.service.ConversationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/ai")
@Api(tags = "APP-AI对话")
@RequiredArgsConstructor
@Slf4j
public class AiChatAppController extends BaseAppController {

    private final AiChatService aiChatService;
    private final ConversationService conversationService;

    @CrossOrigin
    @PostMapping(value = "/send", produces = "text/event-stream;charset=UTF-8")
    @ApiOperation(value = "发送消息（SSE流式返回）")
    public ResponseEntity<Flux<String>> sendMessage(@RequestBody ChatDto dto) {
        log.info("APP-AI对话请求: agentId={}, conversationId={}", dto.getAgentId(), dto.getConversationId());

        Flux<String> stream = aiChatService.sendMessage(dto);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8));
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        headers.set("X-Accel-Buffering", "no");

        return ResponseEntity.ok()
                .headers(headers)
                .body(stream);
    }

    @CrossOrigin
    @PostMapping("/conversation")
    @ApiOperation(value = "创建会话")
    public Result<Conversation> createConversation() {
        ConversationDto dto = new ConversationDto();
        dto.setAgentId(10L);
        return Result.success(conversationService.add(dto));
    }
}
