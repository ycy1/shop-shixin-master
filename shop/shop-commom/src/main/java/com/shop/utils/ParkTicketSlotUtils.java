package com.shop.utils;

import com.shop.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主题公园票种的「入园时段」那一列（ticket_time_slot）的读写口径，只有一份。
 *
 * 库里存的是逗号分隔的多选值（"1,2" = 上午+下午；"0" = 全天），
 * 但发到用户手上那一张票约的是<b>一个</b>时段（shop_trendy_park_user_ticket.time_slot 是 tinyint），
 * 所以出票那一路要拿这里 parse 出来的候选去筛。两头都自己 split 一遍，早晚会写出两种口径。
 */
public class ParkTicketSlotUtils {

    /** 全天那一档（字典 ticket_time_slot 的 0）：它和其他三档是包含关系，不并列 */
    public static final int SLOT_ALL_DAY = 0;

    private static final int SLOT_MIN = 0;
    private static final int SLOT_MAX = 3;

    private ParkTicketSlotUtils() {
    }

    /**
     * 拆成一串去重、按档位升序的数字；空值当"全天"（和建表默认一致）。
     * 档位不在 0..3 里、或者不是数字，直接报错 —— 落进库里就是一行谁都读不出来的脏值。
     */
    public static List<Integer> parse(String slots) {
        if (StringUtils.isBlank(slots)) {
            List<Integer> one = new ArrayList<>(1);
            one.add(SLOT_ALL_DAY);
            return one;
        }
        Set<Integer> picked = new LinkedHashSet<>();
        for (String token : StringUtils.split(slots, ',')) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new ServiceException("入园时段不合法（" + slots + "），只能是 0-3 这几个档位，逗号分隔");
            }
            if (value < SLOT_MIN || value > SLOT_MAX) {
                throw new ServiceException("入园时段不合法（" + slots + "），只有 0全天 1上午 2下午 3夜场这四档");
            }
            picked.add(value);
        }
        if (picked.isEmpty()) {
            List<Integer> one = new ArrayList<>(1);
            one.add(SLOT_ALL_DAY);
            return one;
        }
        List<Integer> sorted = new ArrayList<>(picked);
        sorted.sort(Integer::compareTo);
        if (sorted.contains(SLOT_ALL_DAY) && sorted.size() > 1) {
            throw new ServiceException("「全天」和上午/下午/夜场不并列：勾全天就是这三档都能进，只留「全天」一个就行");
        }
        return sorted;
    }

    /** 拼回落库那一列的写法（去重 + 升序 + 逗号分隔）；只留「全天」就归成 "0" */
    public static String join(List<Integer> slots) {
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(slots));
        sorted.sort(Integer::compareTo);
        StringBuilder text = new StringBuilder();
        for (Integer slot : sorted) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(slot);
        }
        return text.toString();
    }

    /** 先 parse 再 join：表单交来的那串归一成库里该存的写法（默认档、去重、升序都在 parse 里判完了） */
    public static String normalize(String slots) {
        return join(parse(slots));
    }

    /**
     * 这一档在不在那串多选里（出票、改票那一路判"这张票约的时段，这个票种卖不卖"）。
     * 严格成员判断：票种是「全天」时，发出去那张票的时段也只能是 0 全天 ——
     * 把一张全天的票收窄成"上午场"不是买家挑的，那是运营在出票时替他改小了权益
     */
    public static boolean contains(String slots, Integer slot) {
        return slot != null && parse(slots).contains(slot);
    }

    /** 是不是这四档里的一个（单值那一格用：发到用户手上那张票约的时段） */
    public static boolean isValid(Integer slot) {
        return slot != null && slot >= SLOT_MIN && slot <= SLOT_MAX;
    }
}
