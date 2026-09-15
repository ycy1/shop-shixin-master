package com.shop.service.impl;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;

@Slf4j
//@Service("streamingChatResponseHandler")
public class StreamingChatResponseHandlerImpl implements StreamingChatResponseHandler {

    //    public StreamingChatResponseHandlerImpl(CountDownLatch latch){
    //        this.latch = latch;
    //    }
    // 创建计数器 线程等待

    public CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onPartialResponse(String s) {
        System.out.println(s);
    }

    @Override
    public void onCompleteResponse(ChatResponse chatResponse) {
        System.out.println(chatResponse);
        latch.countDown(); // 计数器-1 释放线程
        latch = new CountDownLatch(1); // 重置计数器 为下一次请求做准备
    }

    @Override
    public void onError(Throwable throwable) {
        System.out.println(throwable);
        log.error("error",throwable.getMessage());
        latch.countDown(); // 计数器-1 释放线程
        latch = new CountDownLatch(1); // 重置计数器 为下一次请求做准备
    }
}
