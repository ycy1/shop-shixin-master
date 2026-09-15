package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.ai.ChatDto;
import com.shop.service.AiChatService;
import com.shop.vo.ai.MemoryVo;
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
import java.util.List;

@RestController
@RequestMapping("/ai")
@Api(tags = "AI-对话")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final AiChatService aiChatService;

    @CrossOrigin
    @PostMapping(value = "/send", produces = "text/event-stream;charset=UTF-8")
    @ApiOperation(value = "发送消息（SSE流式返回）")
    public ResponseEntity<Flux<String>> sendMessage(@RequestBody ChatDto dto) {
        log.info("AI对话请求: agentId={}, conversationId={}", dto.getAgentId(), dto.getConversationId());

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

    @GetMapping("/history/{conversationId}")
    @ApiOperation(value = "获取会话历史消息")
    public Result<List<MemoryVo>> history(@PathVariable Long conversationId) {
        return Result.success(aiChatService.getHistory(conversationId));
    }

    @GetMapping("/export-download/{fileId}")
    @ApiOperation(value = "下载导出的对话文件")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String fileId) {
        String tempDir = System.getProperty("java.io.tmpdir");
        String filePath = tempDir + "/chat_export_" + fileId + ".xlsx";
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            file.delete(); // 下载后删除
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chat_export.xlsx")
                    .contentType(org.springframework.http.MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
