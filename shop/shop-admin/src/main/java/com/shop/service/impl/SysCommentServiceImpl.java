package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.entity.SysComment;
import com.shop.mapper.SysCommentMapper;
import com.shop.service.SysCommentService;
import com.shop.utils.PageUtil;
import com.shop.vo.comment.SysCommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysCommentServiceImpl extends ServiceImpl<SysCommentMapper,SysComment> implements SysCommentService {

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
        String content = commentVO.getContent();
        Long replyUserId = commentVO.getReplyUserId();
        Integer isStick = commentVO.getIsStick();
        Integer likeCount = commentVO.getLikeCount();

        long loginUserId = StpUtil.getLoginIdAsLong();

        SysComment originalComment = baseMapper.selectById(commentId);
        if (originalComment == null) {
            throw new RuntimeException("评论不存在");
        }

        if (originalComment.getUserId() != null &&
            originalComment.getUserId() != loginUserId) {
            throw new RuntimeException("无权编辑该评论");
        }

        originalComment.setContent(content);
        if (replyUserId != null) {
            originalComment.setReplyUserId(replyUserId);
        }
        originalComment.setIsStick(isStick);
        if (likeCount != null) {
            originalComment.setLikeCount(likeCount);
        }
        originalComment.setUpdateTime(LocalDateTime.now());

        baseMapper.updateById(originalComment);
    }
}
