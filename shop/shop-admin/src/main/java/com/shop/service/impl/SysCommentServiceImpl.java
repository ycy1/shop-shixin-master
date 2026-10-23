package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.entity.SysComment;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysCommentMapper;
import com.shop.service.SysCommentService;
import com.shop.utils.PageUtil;
import com.shop.vo.comment.SysCommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台「评论管理」。编辑和删除两颗都吃 sys:comment:* 那两道接口权限，不再比评论是谁写的：
 * 这一页是给管理员清内容用的，原作者那一格永远比不上（前台本人删自己那一条走 comm 那颗按钮，
 * 归属判据在那一层）。
 *
 * update_time 不手写：实体上钉了 @TableField(fill = INSERT_UPDATE)，MP 自动盖。
 */
@Service
@RequiredArgsConstructor
public class SysCommentServiceImpl extends ServiceImpl<SysCommentMapper,SysComment> implements SysCommentService {

    /** 和前台「发表」那一道闸同一个上限（见 ShopCommentWriteServiceImpl），两边不一致就会出现后台存得下、前台发不出的字 */
    private static final int MAX_CONTENT_LENGTH = 500;

    @Override
    public Page<SysCommentVO> selectList(SysCommentQueryDto queryDto) {
        Page<SysCommentVO> page = baseMapper.selectPage(PageUtil.getPage(), queryDto);
        List<SysCommentVO> allComments = page.getRecords();

        Map<Integer, SysCommentVO> idMap = allComments.stream()
                .collect(Collectors.toMap(SysCommentVO::getId, c -> c));

        allComments.forEach(c -> c.setChildren(new ArrayList<>()));

        List<SysCommentVO> rootComments = new ArrayList<>();
        for (SysCommentVO comment : allComments) {
            if (comment.getParentId() == null) {
                rootComments.add(comment);
            } else {
                SysCommentVO parent = idMap.get(comment.getParentId());
                if (parent != null) {
                    parent.getChildren().add(comment);
                } else {
                    rootComments.add(comment);
                }
            }
        }

        page.setRecords(rootComments);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editComment(SysCommentVO commentVO) {
        Integer commentId = commentVO.getId();
        String content = commentVO.getContent() == null ? "" : commentVO.getContent().trim();
        Long replyUserId = commentVO.getReplyUserId();
        Integer isStick = commentVO.getIsStick();
        Integer likeCount = commentVO.getLikeCount();

        if (content.isEmpty()) {
            throw new ServiceException("评论内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ServiceException("评论内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }

        SysComment originalComment = baseMapper.selectById(commentId);
        if (originalComment == null) {
            throw new ServiceException("评论不存在");
        }

        originalComment.setContent(content);
        if (replyUserId != null) {
            originalComment.setReplyUserId(replyUserId);
        }
        if (isStick != null) {
            originalComment.setIsStick(isStick);
        }
        if (likeCount != null) {
            originalComment.setLikeCount(likeCount);
        }

        baseMapper.updateById(originalComment);
    }

    /**
     * 删一条或一批：删到顶层那一条时把名下的回复一并软删。
     *
     * 前台那颗删除也是这个口径，两边不一致的话，后台删完父行会留下一堆「父不在了」的回复 ——
     * 列表按顶层起树，它们本来就挂不上去，等于删不掉的脏行。
     * 子评论一次 IN 捞完，不逐条问（库在公网那台，一个来回几十毫秒）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWithChildren(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("请选择要删除的评论");
        }
        List<Integer> all = new ArrayList<>(ids);
        baseMapper.selectList(new LambdaQueryWrapper<SysComment>()
                        .select(SysComment::getId)
                        .in(SysComment::getParentId, ids))
                .forEach(child -> all.add(child.getId()));
        // 实体上有 isDeleted ⇒ MP 按字段名接管全局逻辑删除，这一句到库里是 UPDATE is_deleted = 1
        removeBatchByIds(all);
    }
}
