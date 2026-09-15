package com.shop.config.fesod;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author xxj
 * @title CustomReadDataListener
 * @date 2026/8/27 16:58
 * @description 实现 ReadListener 接口，设置读取数据的操作
 *
 * 局域单例（单线程有序时），用于保存读取的数据
 * （全局单例可能导致实例争抢 data数据错乱）
 */

@Data
@Slf4j
public class CustomReadDataListener<T> implements ReadListener<T> {

    private static final String KEY_PREFIX = "EXCEL_DATA_SIZE";

    private final Map<String, List<T>> fieldClassMap = new ConcurrentHashMap<>();
    private String key;



    public CustomReadDataListener(String key) {
        this.key = key;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        log.info("解析到一条数据:{}", JSON.toJSONString(data));
        fieldClassMap.computeIfAbsent(key, k -> new ArrayList<>()).add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
//        System.out.println("所有数据解析完成！" + fieldClassMap.size());
    }


    public List<T> getDatas(String key) {
        return getDatas(key, true);
    }
    /**
     * 获取数据
     * @param key 任务ID
     * @param isClear 是否清除
     */
    public List<T> getDatas(String key, Boolean isClear) {
        List<T> data = fieldClassMap.getOrDefault(key, Collections.emptyList());
        if (isClear) {
            fieldClassMap.remove(key);
        }
        return data;
    }

    /**
     * 批量删除数据
     * @param keys 删除的key
     */
    public void removeKeys(List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                fieldClassMap.remove(key);
            }
        }
    }

    /**
     * 清空数据
     */
    private void clear() {
        fieldClassMap.clear();
    }
}
