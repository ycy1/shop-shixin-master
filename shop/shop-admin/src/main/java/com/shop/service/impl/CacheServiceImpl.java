package com.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.exception.ServiceException;
import com.shop.service.CacheService;
import com.shop.vo.cache.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    /**
     * 一次最多铺开这么多行成员：hash/list/set/zset 可能挂着上万个成员，
     * 全铺到弹窗里没人看，还会把浏览器卡住
     */
    private static final int MAX_LINES = 200;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 只为判断「这一串是不是合法 JSON」，用的是应用自己那一份 Jackson 配置 */
    private final ObjectMapper objectMapper;

    @Override
    public CacheInfoVo getCacheInfo() {
        Properties info = redisTemplate.execute((RedisCallback<Properties>) RedisServerCommands::info);

        CacheInfoVo vo = new CacheInfoVo();
        vo.setVersion(info.getProperty("redis_version"));
        vo.setMode(info.getProperty("redis_mode"));
        vo.setPort(info.getProperty("tcp_port"));

        String uptimeInSeconds = info.getProperty("uptime_in_seconds");
        long days = Long.parseLong(uptimeInSeconds) / (24 * 3600);
        vo.setUptime(String.valueOf(days));

        // 客户端连接数
        vo.setClients(info.getProperty("connected_clients"));

        // 内存信息
        vo.setUsedMemory(formatBytes(Long.parseLong(info.getProperty("used_memory"))));
        String maxMemory = info.getProperty("maxmemory");
        vo.setMaxmemory(maxMemory.equals("0") ? "不限制" : formatBytes(Long.parseLong(maxMemory)));

        // 持久化信息
        vo.setAofEnabled("1".equals(info.getProperty("aof_enabled")) ? "是" : "否");
        vo.setRdbLastSaveStatus("ok".equals(info.getProperty("rdb_last_bgsave_status")) ? "成功" : "失败");

        // 获取所有数据库的key总数
        Long dbSize = redisTemplate.execute(RedisServerCommands::dbSize);
        vo.setKeys(String.valueOf(dbSize));

        // 网络信息
        vo.setInstantaneousInputKbps(info.getProperty("instantaneous_input_kbps") + " kbps");
        vo.setInstantaneousOutputKbps(info.getProperty("instantaneous_output_kbps") + " kbps");

        return vo;
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public CacheMemoryVo getMemoryInfo() {
        Properties info = redisTemplate.execute((RedisCallback<Properties>) RedisServerCommands::info);

        CacheMemoryVo vo = new CacheMemoryVo();
        vo.setUsed(Long.parseLong(info.getProperty("used_memory")));
        vo.setTotal(Runtime.getRuntime().maxMemory());
        return vo;
    }

    @Override
    public IPage<CacheKeyVo> getKeyList(CacheKeyQuery query) {
        IPage<CacheKeyVo> page = new Page<>();
        Set<String> keys = redisTemplate.keys(query.getKey() == null ? "*" : "*" + query.getKey() + "*");
        if (keys == null || keys.isEmpty()) {
            page.setTotal(0);
            page.setRecords(Collections.emptyList());
            return page;
        }

        List<CacheKeyVo> list = keys.stream().map(key -> {
            CacheKeyVo vo = new CacheKeyVo();
            vo.setKey(key);
            vo.setType(Objects.requireNonNull(redisTemplate.type(key)).name());
            vo.setSize(getKeySize(key));
            vo.setTtl(redisTemplate.getExpire(key));
            return vo;
        }).collect(Collectors.toList());

        // 分页处理
        int start = (query.getPageNum() - 1) * query.getPageSize();
        int end = Math.min(start + query.getPageSize(), list.size());
        page.setRecords(list.subList(start, end));
        page.setTotal(list.size());
        return page;
    }

    @Override
    public void clearCache() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 看内容：读的是库里存的那串原始字节（不是再走一遍 Jackson 反序列化）
     *
     * 为什么要绕这一圈 —— 这个项目的 string 缓存值全都带着 @class 那一份类型信息，
     * 反序列化回来再 toString 给管理员看，他抄回去的那一份就已经不是读侧认得的样子了；
     * 而且碰上不是本项目写的键（sa-token 自己那一份），反序列化直接抛，这一格就打不开。
     */
    @Override
    public CacheValueVo getValue(String key) {
        String name = StringUtils.trimToEmpty(key);
        DataType type = StringUtils.isBlank(name) ? DataType.NONE : redisTemplate.type(name);
        if (type == null || DataType.NONE == type) {
            throw new ServiceException("这个键不在了（可能刚过期、也可能已被清掉），刷新列表再看");
        }
        CacheValueVo vo = new CacheValueVo();
        vo.setKey(name);
        vo.setType(type.name());
        vo.setTtl(redisTemplate.getExpire(name));
        // 只有 string 给整体改。其余类型改的是「一整套成员」，弹窗里那几行文本拼不回去：
        // 硬写就是把一个 hash 覆盖成 string，读它的那段代码当场对不上类型。那一类键要给改就整条删掉，让写它的那一侧重建。
        vo.setEditable(DataType.STRING == type);
        vo.setValue(readContent(name, type));
        return vo;
    }

    @Override
    public void updateValue(CacheValueDto dto) {
        String key = StringUtils.trimToEmpty(dto.getKey());
        if (StringUtils.isBlank(key)) {
            throw new ServiceException("键名不能为空");
        }
        if (dto.getValue() == null) {
            throw new ServiceException("内容不能为空（要把这一格清掉就删这个键）");
        }
        DataType type = redisTemplate.type(key);
        if (DataType.STRING != type) {
            throw new ServiceException("只有 string 类型的缓存能改内容，这个键是 "
                    + (type == null ? DataType.NONE.name() : type.name()) + "，要重灌就删掉它");
        }

        byte[] raw = rawKey(key);
        String before = text(redisTemplate.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(raw)));
        // 原文是 JSON 就要求改完还是 JSON：写成半截不会立刻报错，要等下一次业务读它时才在反序列化里抛，
        // 报在离这一改很远的地方，排查的人根本想不到是缓存被手动动过。
        if (isJson(before) && !isJson(dto.getValue())) {
            throw new ServiceException("这一格存的是 JSON，改完的内容不是合法 JSON（少引号/少括号），先核对再保存");
        }

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        byte[] value = dto.getValue().getBytes(StandardCharsets.UTF_8);
        redisTemplate.execute((RedisCallback<Void>) c -> {
            c.stringCommands().set(raw, value);
            // SET 会把原来的时限抹掉：按改之前那一份补回去（-1 本来就是永不过期，不用补）
            if (ttl != null && ttl > 0) {
                c.keyCommands().expire(raw, ttl);
            }
            return null;
        });
    }

    @Override
    public void deleteValue(String key) {
        String name = StringUtils.trimToEmpty(key);
        if (StringUtils.isBlank(name)) {
            throw new ServiceException("键名不能为空");
        }
        if (!Boolean.TRUE.equals(redisTemplate.delete(name))) {
            throw new ServiceException("这个键不在了（可能刚过期、也可能已被清掉），刷新列表再看");
        }
    }

    /** 按类型把内容铺成一行一条的文本，最多 MAX_LINES 行 */
    private String readContent(String key, DataType type) {
        byte[] raw = rawKey(key);
        switch (type) {
            case STRING: {
                return text(redisTemplate.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(raw)));
            }
            case HASH: {
                Map<byte[], byte[]> entries =
                        redisTemplate.execute((RedisCallback<Map<byte[], byte[]>>) c -> c.hashCommands().hGetAll(raw));
                if (entries == null || entries.isEmpty()) {
                    return "";
                }
                return limited(entries.entrySet().stream()
                        .map(one -> text(one.getKey()) + " = " + text(one.getValue()))
                        .collect(Collectors.toList()));
            }
            case LIST: {
                List<byte[]> items =
                        redisTemplate.execute((RedisCallback<List<byte[]>>) c -> c.listCommands().lRange(raw, 0, -1));
                if (items == null || items.isEmpty()) {
                    return "";
                }
                List<String> lines = new ArrayList<>(items.size());
                for (int i = 0; i < items.size(); i++) {
                    lines.add("[" + i + "] " + text(items.get(i)));
                }
                return limited(lines);
            }
            case SET: {
                Set<byte[]> members =
                        redisTemplate.execute((RedisCallback<Set<byte[]>>) c -> c.setCommands().sMembers(raw));
                if (members == null || members.isEmpty()) {
                    return "";
                }
                return limited(members.stream().map(CacheServiceImpl::text).collect(Collectors.toList()));
            }
            case ZSET: {
                // 这一档走 template 那一层（其余类型读的是原始字节）：要看的是反序列化回来的成员和分值。
                // 名次在这里自己排——那一串给回来的时候顺序已经丢了，不排就是刷新一次换个样。
                Set<ZSetOperations.TypedTuple<Object>> tuples =
                        redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
                if (tuples == null || tuples.isEmpty()) {
                    return "";
                }
                List<ZSetOperations.TypedTuple<Object>> sorted = new ArrayList<>(tuples);
                sorted.sort(Comparator
                        .comparingDouble((ZSetOperations.TypedTuple<Object> one) -> one.getScore() == null ? 0 : one.getScore())
                        .thenComparing(one -> String.valueOf(one.getValue())));
                int rows = Math.min(sorted.size(), MAX_LINES);
                List<String> lines = new ArrayList<>(rows);
                for (int i = 0; i < rows; i++) {
                    ZSetOperations.TypedTuple<Object> one = sorted.get(i);
                    lines.add("[" + (i + 1) + "] " + one.getValue() + "  分值 " + (one.getScore() == null ? 0 : one.getScore()));
                }
                return limited(lines);
            }
            default: {
                return "";
            }
        }
    }

    private String limited(List<String> lines) {
        if (lines.size() <= MAX_LINES) {
            return String.join("\n", lines);
        }
        return String.join("\n", lines.subList(0, MAX_LINES))
                + "\n…… 共 " + lines.size() + " 条，只展开前 " + MAX_LINES + " 条";
    }

    private boolean isJson(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            objectMapper.readTree(value);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /** 键这一侧用的是 StringRedisSerializer，序列化出来的字节和读侧完全一致 */
    private byte[] rawKey(String key) {
        byte[] raw = redisTemplate.getStringSerializer().serialize(key);
        if (raw == null) {
            throw new ServiceException("键名不能为空");
        }
        return raw;
    }

    private static String text(byte[] raw) {
        return raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
    }

    private Long getKeySize(String key) {
        try {
            return redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().strLen(key.getBytes()));
        } catch (Exception e) {
            return 0L;
        }
    }
}