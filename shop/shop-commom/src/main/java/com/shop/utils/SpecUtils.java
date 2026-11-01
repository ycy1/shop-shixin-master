package com.shop.utils;

import com.shop.entity.ShopProductSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规格 id 串与规格文本的口径
 *
 * SKU 上那两列是一对：spec_ids 是存的（shop_product_spec 的 id，半角逗号分隔），spec_text 是给它配的展示串。
 * 文本由服务端从字典现拼、不认页面传来的，否则一处改规格名，全库那些老 SKU 上还挂着旧叫法。
 * 前后台都读这一份：解析规则只此一处，商品详情页的规格表、后台 SKU 弹窗看到的才是同一列。
 */
public class SpecUtils {

    private SpecUtils() {
    }

    /**
     * 解析成一串 id：升序、去重。
     *
     * 只认纯数字那几段，空项、多余的分隔符、历史脏数据一律跳过 —— 不能因为一格脏数据把整个页面打不成。
     * 升序是为了「1,2」和「2,1」存成同一串：页面勾选顺序不该分出两条 SKU 来。
     */
    public static List<Long> parseSpecIds(String specIds) {
        if (specIds == null || specIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(specIds.split(","))
                .map(String::trim)
                .filter(text -> text.matches("\\d+"))
                .map(Long::valueOf)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** 存库那一列：和 parseSpecIds 同一套归一化，没挂规格就是空串（空串=这件商品的默认 SKU）。 */
    public static String normalizeSpecIds(String specIds) {
        List<Long> ids = parseSpecIds(specIds);
        if (ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 从规格行拼展示文本，形如 `内存：8G / 颜色：曜石黑`。
     *
     * 传进来的就是服务层按 parseSpecIds 查回来的那几行，顺序以它为准（和 SKU 弹窗、前台规格表同一排法）；
     * 单位直接贴在值后面，没填单位就光剩值。一行都没捞到（没挂规格、或挂的已被禁用删除）就是空串，
     * 页面上这一格整格不渲染。
     */
    public static String buildSpecText(List<ShopProductSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        return specs.stream()
                .filter(spec -> spec != null && notBlank(spec.getSpecName()))
                .map(spec -> spec.getSpecName().trim() + "：" + specValueOf(spec))
                .collect(Collectors.joining(" / "));
    }

    private static String specValueOf(ShopProductSpec spec) {
        String value = notBlank(spec.getSpecValue()) ? spec.getSpecValue().trim() : "";
        if (notBlank(spec.getSpecUnit())) {
            value = value + spec.getSpecUnit().trim();
        }
        return value;
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
