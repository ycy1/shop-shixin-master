package com.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.entity.SysComment;
import com.shop.vo.comment.SysCommentVO;

public interface SysCommentService extends IService<SysComment> {

    /**
     * 获取评论列表
     * @param queryDto 查询条件
     * @return
     */
    Page<SysCommentVO> selectList(SysCommentQueryDto queryDto);

    /**
     * 编辑评论（原作者或管理员）
     * @param commentVO 评论信息
     */
    void editComment(SysCommentVO commentVO);
}
