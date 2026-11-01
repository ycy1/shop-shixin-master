package com.shop.api.service.impl;

import com.shop.api.service.ShopSearchHotService;
import com.shop.common.RedisConstants;
import com.shop.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 首页那一排热搜：读写都在 Redis 一个 ZSet 里（成员=关键字，分值=被搜次数）
 *
 * 取前 N 就是按分值从高往低数前 N 个，没有第二份状态 —— 所以「裁尾巴」和「加分」是同一件事的两半，
 * 都放在 record 里，读那一侧只管取。
 * 不鉴权、不看登录态：谁搜都算一票（和活动埋点那条同一口径，也不防刷 —— 一排展示词被刷了也只是那一排不好看，
 * 没有额度、没有钱在背后）。
 */
@Service
@RequiredArgsConstructor
public class ShopSearchHotServiceImpl implements ShopSearchHotService {

    /** 首页那一排最多摆这么多条（再多就不是一排热搜了），ZSet 里也只留这么多 */
    private static final int TOP_SIZE = 10;

    /** 比这还长的不当热搜：搜「面包」是词，搜一整句话是粘贴进来的 */
    private static final int MAX_LENGTH = 20;

    /** 这串多久没人搜就整个清掉：热搜说的是最近的事，早年被刷上来的词不该一直挂着 */
    private static final long HOT_EXPIRE = RedisConstants.WEEK_EXPIRE;

    private final RedisUtil redisUtil;

    @Override
    public List<String> listHot() {
        Set<ZSetOperations.TypedTuple<Object>> rows =
                redisUtil.zReverseRangeWithScores(RedisConstants.SEARCH_HOT_KEY, 0, TOP_SIZE - 1);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 名次在这里自己排：分值大的在前，同票的按词排。
        // 那一串成员给回来的时候顺序已经丢了（Set 里是个 HashSet），不排就是刷新一次换个样。
        List<ZSetOperations.TypedTuple<Object>> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .comparingDouble((ZSetOperations.TypedTuple<Object> row) -> row.getScore()).reversed()
                .thenComparing(row -> String.valueOf(row.getValue())));

        List<String> words = new ArrayList<>(sorted.size());
        for (ZSetOperations.TypedTuple<Object> row : sorted) {
            if (row.getValue() != null) {
                words.add(String.valueOf(row.getValue()));
            }
        }
        return words;
    }

    @Override
    public void record(String keyword) {
        String word = normalize(keyword);
        if (word == null) {
            return;
        }
        redisUtil.zIncrBy(RedisConstants.SEARCH_HOT_KEY, word, 1);
        // 每记一次就把整串的时限往前推：只要还有人在搜，这一排就该一直有词
        redisUtil.expire(RedisConstants.SEARCH_HOT_KEY, HOT_EXPIRE, TimeUnit.SECONDS);

        Long size = redisUtil.zSize(RedisConstants.SEARCH_HOT_KEY);
        // 名次从分数低的一端数起，所以裁掉的是 [0, size-TOP_SIZE) 这一段：榜外那些词既显示不着，也一直在涨分占内存
        if (size != null && size > TOP_SIZE) {
            redisUtil.zRemoveRangeByRank(RedisConstants.SEARCH_HOT_KEY, 0, size - TOP_SIZE - 1);
        }
    }

    /** 首尾空白去掉、中间连着的空白压成一个空格（搜「小米 手环」和「小米手环」是两回事，但都不该带着一串空格上榜）；
     *  整出来是空的或超长就回 null，由调用方放过这一次 */
    private String normalize(String keyword) {
        String word = StringUtils.normalizeSpace(keyword);
        if (StringUtils.isBlank(word) || word.length() > MAX_LENGTH) {
            return null;
        }
        return word;
    }
}
