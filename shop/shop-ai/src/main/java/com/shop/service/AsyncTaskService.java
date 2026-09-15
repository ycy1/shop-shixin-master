package com.shop.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

@Service
@Slf4j
public class AsyncTaskService {

    // 使用独立的线程池，避免阻塞主线程
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(10);

    public void submitTasks(String requestId, int data) throws ExecutionException, InterruptedException, TimeoutException {
        log.info("提交任务: {}", requestId);
        // 新开线程 异步提交所有任务，不等待结果
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // 模拟多个耗时任务
                List<CompletableFuture<Void>> tasks = Arrays.asList(
                        CompletableFuture.runAsync(() -> processTask1(requestId, data), taskExecutor),
                        CompletableFuture.runAsync(() -> processTask1(requestId, data), taskExecutor),
                        CompletableFuture.runAsync(() -> processTask1(requestId, data), taskExecutor),
                        CompletableFuture.runAsync(() -> processTask1(requestId, data), taskExecutor)
                );

//                 等待所有任务完成（但不影响主响应）
                CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                        .orTimeout(30, TimeUnit.SECONDS)  // 设置超时
                        .whenComplete((v, e) -> {
                            if (e != null) {
                                log.error("部分任务处理失败: {}", requestId, e);
                                // 执行补偿逻辑
//                                handleFailure(requestId, data);
                            } else {
                                log.info("所有任务完成: {}", requestId);
                            }
                        });

            } catch (Exception e) {
                log.error("任务提交失败: {}", requestId, e);
            }
        }, taskExecutor);  // 使用另一个线程池提交任务，彻底分离

        future.get(30, TimeUnit.SECONDS);

    }

    public void submitTasksByLatch(String requestId, int data) {
        CountDownLatch latch = new CountDownLatch(4);  // 4个任务

        log.info("提交任务: {}", requestId);
        for (int i = 0; i < 4; i++){
            try {
                taskExecutor.submit(() -> processTask1(requestId, data));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        }
        try {
            // 等待所有任务完成（但不影响主响应）
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    @Qualifier("ollamaChatMode")
    public ChatLanguageModel ollamaModel;


    @Autowired
    @Qualifier("deepseekModel")
    private ChatLanguageModel deepseekModel;

    public void submitTasksByChat(String requestId, String prompt) {
        CountDownLatch latch = new CountDownLatch(1);  // 4个任务

        log.info("提交任务: {}", requestId);
            taskExecutor.submit(() -> {
                List<ChatMessage> messages = List.of(
                    SystemMessage.from("你是一个文章简化提取助手"),
                    UserMessage.from("请将下面这段内容进行简单的总结，并返回总结结果\n\n" + prompt)
            );
            ChatResponse response = deepseekModel.chat(messages);
            System.out.println("response: " + response);
        });
        latch.countDown();

        try {
            // 等待所有任务完成（但不影响主响应）
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void processTask1(String requestId, int data) {
        // 耗时操作1
        try {
            Thread.sleep(5000);

            // 写入文件
            System.out.println("写入文件: " + requestId + " " + data);
            File file = new File("D:/temp/" + requestId + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file, true);
            fileWriter.append(requestId + " " + data + "\n");
            fileWriter.close();
            // 业务逻辑
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        log.info("处理任务: {} {}", requestId, data);
//        System.out.println("处理任务: " + requestId + " " + data);
    }


}
