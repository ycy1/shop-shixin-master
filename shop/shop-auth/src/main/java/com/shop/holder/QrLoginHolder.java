package com.shop.holder;

import com.shop.common.Result;
import com.shop.vo.QrLoginStateVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫码登录长轮询挂起器
 *
 * <p>把还在等待的浏览器请求按 code 挂起来，App 端扫码/确认时立刻唤醒，避免前端定时空转。
 * 真正的状态存在 Redis 里（见 {@code RedisConstants.QR_LOGIN}），这里只负责"叫醒"——
 * 所以即使唤醒丢了（比如已经超时），前端下一次轮询也能直接读到最新状态，不会卡死。
 *
 * <p>注意：这是单实例内存态。多实例部署时需要换成 Redis 发布订阅。
 *
 * @author: ycy
 */
@Slf4j
@Component
public class QrLoginHolder {

    private final Map<String, DeferredResult<Result<QrLoginStateVo>>> waiters = new ConcurrentHashMap<>();

    /**
     * 挂起一个等待中的轮询请求
     */
    public void register(String code, DeferredResult<Result<QrLoginStateVo>> deferredResult) {
        // 同一个 code 可能因为超时重连而同时挂起两个，旧的那个直接放行让它自己重连，
        // 否则它会一直挂到超时才释放
        DeferredResult<Result<QrLoginStateVo>> previous = waiters.put(code, deferredResult);
        if (previous != null && !previous.isSetOrExpired()) {
            previous.setResult(Result.success(QrLoginStateVo.waiting()));
        }
        // 用两参 remove，避免误删后来注册的那个
        deferredResult.onCompletion(() -> waiters.remove(code, deferredResult));
    }

    /**
     * 唤醒等待中的轮询请求。没有人在等就直接返回，
     * 状态已经写在 Redis 里了，前端下次轮询照样能拿到。
     */
    public void complete(String code, QrLoginStateVo state) {
        DeferredResult<Result<QrLoginStateVo>> deferredResult = waiters.remove(code);
        if (deferredResult == null) {
            return;
        }
        if (deferredResult.isSetOrExpired()) {
            log.debug("扫码登录轮询已超时，跳过唤醒: code={}", code);
            return;
        }
        deferredResult.setResult(Result.success(state));
    }
}
