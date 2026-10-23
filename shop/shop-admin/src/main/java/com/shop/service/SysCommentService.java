package com.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.entity.SysComment;
import com.shop.vo.comment.SysCommentVO;

import java.util.List;

public interface SysCommentService extends IService<SysComment> {

    /**
     * 获取评论列表
     * @param queryDto 查询条件
     * @return
     */
    Page<SysCommentVO> selectList(SysCommentQueryDto queryDto);

    /**
     * 编辑评论（后台管理员改，不看评论是谁写的）
     * @param commentVO 评论信息
     */
    void editComment(SysCommentVO commentVO);

    /**
     * 删除评论（软删），删到顶层评论时连带它名下的回复一起删
     * @param ids 评论主键集合
     */
    void removeWithChildren(List<Integer> ids);
}
