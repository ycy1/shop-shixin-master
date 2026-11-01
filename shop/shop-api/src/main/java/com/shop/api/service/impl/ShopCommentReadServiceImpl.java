package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.api.service.ShopCommentReadService;
import com.shop.dto.comment.ShopCommentQueryDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysCommentMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.comment.CommentListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前台评论区 读实现（商品/门店/品牌/广场/公园几处详情页 + Web/App 两端共用这一份）
 *
 * 一趟接口回「顶层分页 + 每行的整棵回复树」，理由不是省事，是往返数：
 * 库在公网那台，一个来回几十毫秒，前端要是先拿一页顶层再逐条逐层问回复，一页十条就是十几条查询。
 * 所以第二趟用递归 CTE 把这一页所有顶层名下的各层回复一次捞完（不管几层都只一趟），
 * 回来在内存里按 parentId 堆成树（口径同「按采购单连明细」那一条）。
 * 这一趟总共两条 SQL，回复不再分页 —— 一个对象下的回复本来就跟着那几条顶层走，铺完就完了。
 *
 * 只有两条判据：commentType 和 businessId 必须一起给（少一条就报，不给它拼 `= NULL` 那种查不出东西的谓词）；
 * 软删由 XML 里的 is_deleted = 0 挡（自定义 SQL 不走 MP 的全局逻辑删除，那边每一段都手写好了）。
 * 对象本身下没下架、存不存在都不在这里判：评论是已经发生的事实，商品下架了评论区照样得能翻出来看，
 * 详情页自己进不去是另一条链的事（和「收藏/足迹的对象后来改名下架都不影响那一行」同一口径）。
 *
 * 未登录照样回（/api/** 不过 Sa-Token 拦截器，取不到登录态就传 userId = null，
 * XML 里那一格 is_like 恒为 0，前端画空心按钮 + 挂 data-open-login）。
 */
@Service
@RequiredArgsConstructor
public class ShopCommentReadServiceImpl implements ShopCommentReadService {

    private final SysCommentMapper sysCommentMapper;

    @Override
    public IPage<CommentListVo> page(ShopCommentQueryDto query) {
        Integer commentType = query == null ? null : query.getCommentType();
        Long businessId = query == null ? null : query.getBusinessId();
        if (commentType == null || businessId == null) {
            throw new ServiceException("请指明看哪一类对象（1商品 2门店 3品牌 4广场 5公园）的哪一条的评论");
        }

        LoginUserInfo login = LoginUserUtils.getLoginUser();
        Long userId = login == null ? null : login.getId();

        Page<Object> page = PageUtil.getPage();
        IPage<CommentListVo> result =
                sysCommentMapper.getComments(page, businessId, query.getSortType(), userId, commentType);
        List<CommentListVo> roots = result.getRecords();
        if (roots == null || roots.isEmpty()) {
            // 这一页一条顶层都没有，第二趟省掉：拿空集合去拼 IN () 是一条废 SQL
            return result;
        }

        List<Integer> rootIds = roots.stream().map(CommentListVo::getId).collect(Collectors.toList());
        List<CommentListVo> descendants = sysCommentMapper.getDescendantsComment(rootIds, userId);
        Map<Integer, List<CommentListVo>> byParent = descendants == null ? Collections.emptyMap()
                : descendants.stream().collect(Collectors.groupingBy(CommentListVo::getParentId));
        roots.forEach(root -> root.setChildren(childrenOf(root.getId(), byParent)));
        return result;
    }

    /**
     * 从「parentId → 直接回复」那张堆里递归把这一棵取出来：一趟 SQL 给回的是各层混在一条列表里的行，
     * 谁的下一级都靠这一格 parentId 认，所以挂到父行上也只能一层层现挂 —— 每一行挂完自己的下一级，
     * 页面上「回复的回复」就是这么一层层缩进去的。
     */
    private List<CommentListVo> childrenOf(Integer id, Map<Integer, List<CommentListVo>> byParent) {
        List<CommentListVo> direct = byParent.get(id);
        if (direct == null || direct.isEmpty()) {
            return new ArrayList<>();
        }
        direct.forEach(row -> row.setChildren(childrenOf(row.getId(), byParent)));
        return direct;
    }
}
