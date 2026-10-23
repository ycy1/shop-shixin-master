package com.shop.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.PageQuery;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Objects;

public class PageUtil {
    private static final String PAGE_NUM = "pageNum";
    private static final String PAGE_SIZE = "pageSize";

    /**
     * 获取分页参数
     */
    public static <T> Page<T> getPage() {
        PageQuery query = getPageQuery();
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /**
     * 空分页：数据范围收窄到"一行都不该看到"时直接返回，省掉一次注定为空的查询，
     * 也避免拿着空集合去拼 IN () 把条件写失效
     */
    public static <T> Page<T> getEmptyPage() {
        PageQuery query = getPageQuery();
        Page<T> page = new Page<>(query.getPageNum(), query.getPageSize());
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        return page;
    }

    /**
     * 获取分页查询参数
     */
    public static PageQuery getPageQuery() {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        PageQuery pageQuery = new PageQuery();

        // 获取页码
        String pageNum = request.getParameter(PAGE_NUM);
        if (pageNum != null && !pageNum.isEmpty()) {
            pageQuery.setPageNum(Integer.parseInt(pageNum));
        }

        // 获取每页数量
        String pageSize = request.getParameter(PAGE_SIZE);
        if (pageSize != null && !pageSize.isEmpty()) {
            pageQuery.setPageSize(Integer.parseInt(pageSize));
        }

        return pageQuery;
    }

}
