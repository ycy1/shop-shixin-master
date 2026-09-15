package com.shop.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.vo.comment.CommentListVo;
import com.shop.entity.SysComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.vo.comment.SysCommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysCommentMapper extends BaseMapper<SysComment> {

    IPage<CommentListVo> getComments(@Param("page") Page<Object> page, @Param("businessId") Integer businessId,
                                     @Param("sortType") String sortType, @Param("userId") Long userId,
                                     @Param("commentType") Integer commentType);

    List<CommentListVo> getChildrenComment(@Param("id") Integer id, @Param("userId") Long userId);

    Page<SysCommentVO> selectPage(@Param("page") Page<SysCommentVO> page, @Param("query") SysCommentQueryDto query);

    IPage<CommentListVo> selectMyComment(@Param("page") Page<Object> page, @Param("userId") long userId);

    IPage<CommentListVo> getMyReply(@Param("page") Page<Object> page, @Param("userId") long userId);

    void likeComment(@Param("commentId") Integer commentId, @Param("userId") Integer userId, @Param("commentType") Integer commentType);

    void unLikeComment(@Param("commentId") Integer commentId, @Param("userId") Integer userId);

    Boolean getUserIsLikeComment(@Param("commentId") Integer commentId, @Param("userId") Integer userId);

    void updateLikeCount(@Param("commentId") Integer commentId, @Param("delta") int delta);
}
