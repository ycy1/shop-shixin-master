package com.shop.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.context.CanalContext;
import top.javatool.canal.client.handler.EntryHandler;
import top.javatool.canal.client.model.CanalModel;

import java.util.Map;

/**
 * @author xxj
 * @title BinLogHandler
 * @date 2026/9/11 14:52
 * @description 监听数据库binlog数据
 * mysqlbinlog --base64-output=decode-rows -v /var/lib/mysql/binlog.000008
 */
@CanalTable("all")  // 监听所有表 (通常指定 某一表 sys_user EntryHandler<User>)
//@Component
@Slf4j
public class BinLogHandler implements EntryHandler<Map<String, String>> {
    @Override
    public void insert(Map<String, String> item) {
        System.out.println("insert:" + item);
        CanalModel model = CanalContext.getModel();
        long start = System.currentTimeMillis();
        long delay = start - model.getExecuteTime(); // 数据库binlog到handler耗时
        log.info("table={}.{}, delay={}s",
                model.getDatabase(), model.getTable(), String.format("%.2f", delay / 1000.0));
    }

    @Override
    public void update(Map<String, String> before, Map<String, String> after) {
        System.out.println("update:" + before + "->" + after);
        CanalModel model = CanalContext.getModel();
        long start = System.currentTimeMillis();
        long delay = start - model.getExecuteTime(); // 数据库binlog到handler耗时
        log.info("table={}.{}, delay={}s",
                model.getDatabase(), model.getTable(), String.format("%.2f", delay / 1000.0));
    }

    @Override
    public void delete(Map<String, String> user) {
        System.out.println("delete:" + user);

    }


}
